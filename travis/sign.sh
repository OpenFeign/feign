#!/usr/bin/env bash
#
# Copyright 2012-2019 The Feign Authors
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

# skip signing when building pull requests
if [[ "$TRAVIS_PULL_REQUEST" = "false" ]]; then
  echo "[ENVIRONMENT] Preparing Signing Signatures"
  openssl aes-256-cbc -K $encrypted_8beb152aadd6_key -iv $encrypted_8beb152aadd6_iv -in travis/codesigning.asc.enc -out travis/codesigning.asc -d
  gpg --fast-import travis/codesigning.asc
fi