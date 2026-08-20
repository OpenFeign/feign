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

# Prints the comma separated Maven module list for one CI shard, ready to hand to -pl.
#
#   scripts/shard-modules.sh "$CIRCLE_NODE_INDEX" "$CIRCLE_NODE_TOTAL"
#
# Modules are discovered by walking <module> entries down from the root pom, so nothing here needs
# editing when the reactor changes. Each module is weighted by its measured test seconds from
# .circleci/module-timings.properties plus a flat per-module cost, then the modules are packed
# heaviest first into the emptiest shard.
#
# The flat cost matters as much as the test time: most modules barely test at all, yet every one of
# them still pays for compiling, packaging and jarring. Ignoring it packs all the cheap modules into
# one shard and makes it the slow one.
#
# Modules with no recorded timing fall back to a test-source count, so a stale timings file skews
# the split rather than breaking it. Regenerate it with scripts/module-timings.sh.
#
# With no arguments it prints every module, which is what a local full build wants.

INDEX=${1:-0}
TOTAL=${2:-1}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMINGS="$ROOT/.circleci/module-timings.properties"

# compile, package and jar cost roughly this many seconds even for a module with no tests
FLAT_COST=5

collect_modules() {
  local parent=$1 child path
  while read -r child; do
    [ -n "$child" ] || continue
    path="${parent:+$parent/}$child"
    echo "$path"
    collect_modules "$path"
  done < <(sed -n 's/.*<module>\(.*\)<\/module>.*/\1/p' "$ROOT/${parent:+$parent/}pom.xml" 2>/dev/null)
}

weight_of() {
  local module=$1 seconds
  seconds=$(sed -n "s|^${module}=\(.*\)$|\1|p" "$TIMINGS" 2>/dev/null | head -1)
  if [ -z "$seconds" ]; then
    seconds=$(find "$ROOT/$module/src/test" -name '*.java' 2>/dev/null | wc -l)
  fi
  echo $((seconds + FLAT_COST))
}

# heaviest first, and fully ordered so every shard packs the bins identically
mapfile -t weighted < <(
  while read -r module; do
    echo "$(weight_of "$module") $module"
  done < <(collect_modules "") | sort -k1,1rn -k2,2
)

declare -a load bin
for ((i = 0; i < TOTAL; i++)); do
  load[i]=0
  bin[i]=""
done

for entry in "${weighted[@]}"; do
  weight=${entry%% *}
  module=${entry#* }
  lightest=0
  for ((i = 1; i < TOTAL; i++)); do
    ((load[i] < load[lightest])) && lightest=$i
  done
  load[lightest]=$((load[lightest] + weight))
  bin[lightest]="${bin[lightest]:+${bin[lightest]},}$module"
done

if [ -n "${SHARD_DEBUG:-}" ]; then
  for ((i = 0; i < TOTAL; i++)); do
    echo "shard $i weight=${load[i]}: ${bin[i]}" >&2
  done
fi

echo "${bin[INDEX]}"
