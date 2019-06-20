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

# build native image
FROM oracle/graalvm-ce:19.0.0
LABEL vendor="The Last Pickle"

RUN gu install native-image

COPY reflectconfig.json .
COPY ${project.artifactId}-${project.version}.jar .

RUN native-image --verbose \
    --no-fallback \
    -H:ReflectionConfigurationFiles=reflectconfig.json \
    -H:EnableURLProtocols=https \
    -jar ${project.artifactId}-${project.version}.jar \
    -cp ${project.artifactId}-${project.version}.jar



# test the native image
FROM ubuntu

COPY --from=0 /opt/graalvm-ce-19.0.0/jre/lib/amd64/libsunec.so /
COPY --from=0 /${project.artifactId}-${project.version} /

RUN /${project.artifactId}-${project.version}



# create docker image with native
FROM ubuntu

COPY --from=0 /opt/graalvm-ce-19.0.0/jre/lib/amd64/libsunec.so /
COPY --from=0 /${project.artifactId}-${project.version} /

CMD /${project.artifactId}-${project.version}
