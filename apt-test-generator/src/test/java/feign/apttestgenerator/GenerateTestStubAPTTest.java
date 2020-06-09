/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.apttestgenerator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import java.io.File;

/**
 * Test for {@link GenerateTestStubAPT}
 */
public class GenerateTestStubAPTTest {

  private final File main = new File("../example-github/src/main/java/").getAbsoluteFile();

  @Test
  public void test() throws Exception {
    final Compilation compilation =
        javac()
            .withProcessors(new GenerateTestStubAPT())
            .compile(JavaFileObjects.forResource(
                new File(main, "example/github/GitHubExample.java")
                    .toURI()
                    .toURL()));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("example.github.GitHubStub")
        .hasSourceEquivalentTo(JavaFileObjects.forResource(
            new File("src/test/java/example/github/GitHubStub.java")
                .toURI()
                .toURL()));
  }

}
