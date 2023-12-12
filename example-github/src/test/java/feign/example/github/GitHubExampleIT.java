/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.example.github;

import static org.assertj.core.api.Assertions.assertThat;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;

/**
 * Run main for {@link GitHubExampleIT}
 */
class GitHubExampleIT {

  @Test
  void runMain() throws Exception {
    final String jar = Arrays.stream(new File("target").listFiles())
        .filter(file -> file.getName().startsWith("feign-example-github")
            && file.getName().endsWith(".jar"))
        .findFirst()
        .map(File::getAbsolutePath)
        .get();

    final String line = "java -jar " + jar;
    final CommandLine cmdLine = CommandLine.parse(line);
    final int exitValue = new DefaultExecutor().execute(cmdLine);

    assertThat(exitValue).isEqualTo(0);
  }

}
