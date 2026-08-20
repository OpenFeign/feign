#!/usr/bin/env bash
#
# Copyright 2012-2026 The Feign Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

set -euo pipefail

# Regenerates .circleci/module-timings.properties, which scripts/shard-modules.sh uses to balance
# the CI shards. Run it after a full local build:
#
#   ./mvnw clean verify && scripts/module-timings.sh
#
# Timings come from the surefire and failsafe reports, so they are keyed by module directory and
# need no mapping back from the reactor's display names. Modules missing from the file fall back to
# a test-source-count estimate, so a stale file degrades gracefully rather than breaking the split.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="$ROOT/.circleci/module-timings.properties"

cd "$ROOT"
python3 - "$OUTPUT" <<'PY'
import os, sys, xml.etree.ElementTree as ET
from collections import defaultdict

totals = defaultdict(float)
for dirpath, _, filenames in os.walk('.'):
    if os.path.basename(dirpath) not in ('surefire-reports', 'failsafe-reports'):
        continue
    module = os.path.relpath(dirpath, '.').split('/target/')[0]
    for name in filenames:
        if not (name.startswith('TEST-') and name.endswith('.xml')):
            continue
        try:
            root = ET.parse(os.path.join(dirpath, name)).getroot()
        except ET.ParseError:
            continue
        totals[module] += float(root.get('time') or 0)

if not totals:
    sys.exit('no surefire or failsafe reports found, run a full build first')

with open(sys.argv[1], 'w') as out:
    out.write('# Test seconds per module, consumed by scripts/shard-modules.sh.\n')
    out.write('# Regenerate with: ./mvnw clean verify && scripts/module-timings.sh\n')
    for module in sorted(totals):
        out.write(f'{module}={totals[module]:.0f}\n')
print(f'wrote {len(totals)} modules to {sys.argv[1]}')
PY
