/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class DefaultEncoderTest {
  private static final Supplier<Stream<Arguments>> testCanEncode =
      () ->
          Stream.of(
              arguments(null, Object.class, true),
              arguments("string", String.class, true),
              arguments(new byte[] {1, 2, 3}, byte[].class, true),
              arguments(new File("file"), File.class, true),
              arguments(Path.of("path"), Path.class, true),
              arguments(new ByteArrayInputStream(new byte[] {1, 2, 3}), InputStream.class, true),
              arguments(Request.Body.of("body"), Request.Body.class, true),
              arguments(new DefaultEncoderTest(), DefaultEncoderTest.class, false));

  private final Encoder encoder = new DefaultEncoder();

  @Test
  void encodesStrings() throws Exception {
    String content = "This is my content";
    RequestTemplate template = new RequestTemplate();
    encoder.encode(content, String.class, template);
    Optional<Request.Body> optionalBody = template.requestBody();
    assertThat(optionalBody).isPresent();
    String body;
    try {
      body = optionalBody.get().writeToString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("Failed to read body", e);
    }
    assertThat(body).contains(content);
  }

  @Test
  void encodesByteArray() throws Exception {
    byte[] content = {12, 34, 56};
    RequestTemplate template = new RequestTemplate();
    encoder.encode(content, byte[].class, template);
    Optional<Request.Body> optionalBody = template.requestBody();
    assertThat(optionalBody).isPresent();
    byte[] body;
    try {
      body = optionalBody.get().writeToByteArray();
    } catch (IOException e) {
      throw new AssertionError("Failed to read body", e);
    }
    assertThat(body).isEqualTo(content);
  }

  @Test
  void refusesToEncodeOtherTypes() throws Exception {
    Throwable exception =
        assertThatExceptionOfType(EncodeException.class)
            .isThrownBy(() -> encoder.encode(Clock.systemUTC(), Clock.class, new RequestTemplate()))
            .actual();
    assertThat(exception.getMessage()).contains("is not a type supported by this encoder.");
  }

  @Test
  void shouldEncodeFile(@TempDir File tempDir) throws IOException {
    var file = new File(tempDir, "DefaultEncoderTest_shouldEncodeFile.txt");
    var template = new RequestTemplate();
    var expected = "File content";

    Files.writeString(file.toPath(), expected);

    encoder.encode(file, File.class, template);

    verifyBody(template, expected.length(), true, expected);
  }

  @Test
  void shouldEncodePath(@TempDir Path tempDir) throws IOException {
    var path = tempDir.resolve("DefaultEncoderTest_shouldEncodePath.txt");
    var template = new RequestTemplate();
    var expected = "Path content";

    Files.writeString(path, expected);

    encoder.encode(path, Path.class, template);

    verifyBody(template, expected.length(), true, expected);
  }

  @Test
  void shouldEncodeInputStream() throws IOException {
    var template = new RequestTemplate();
    var expected = "InputStream content";

    try (var inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8))) {
      encoder.encode(inputStream, InputStream.class, template);
    }

    verifyBody(template, -1, false, expected);
  }

  @Test
  void shouldEncodeRequestBody() {
    var template = new RequestTemplate();
    var expected = "Request body content";

    encoder.encode(Request.Body.of(expected), Request.Body.class, template);

    verifyBody(template, expected.length(), true, expected);
  }

  @ParameterizedTest
  @FieldSource
  void testCanEncode(Object object, Type bodyType, boolean expected) {
    var template = new RequestTemplate();
    var actual = encoder.canEncode(object, bodyType, template);

    assertEquals(expected, actual);
  }

  void verifyBody(
      RequestTemplate template, long contentLength, boolean repeatable, String expected) {
    var optionalBody = template.requestBody();
    assertThat(optionalBody).isPresent();

    var body = optionalBody.get();
    assertEquals(contentLength, body.contentLength());
    assertEquals(repeatable, body.isRepeatable());

    var actual = assertDoesNotThrow(() -> body.writeToString(StandardCharsets.UTF_8));
    assertEquals(expected, actual);
  }
}
