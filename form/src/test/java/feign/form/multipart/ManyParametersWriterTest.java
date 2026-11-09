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
package feign.form.multipart;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import feign.form.FormEncoder;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ManyParametersWriterTest {

  private static final ManyParametersWriter WRITER = new ManyParametersWriter();

  private static final String FIXED_BOUNDARY = "boundary";

  private static final String TEXT_PLAIN_CONTENT_TYPE_HEADER =
      "Content-Type: text/plain; charset=UTF-8\r\n\r\n";

  @Test
  void intBooleanAndStringArraysAreApplicable() {
    assertThat(WRITER.isApplicable(new int[] {1, 2})).isTrue();
    assertThat(WRITER.isApplicable(new boolean[] {true, false})).isTrue();
    assertThat(WRITER.isApplicable(new String[] {"one", "two"})).isTrue();
  }

  @Test
  void byteArrayIsApplicableBecauseByteIsANumber() {
    assertThat(WRITER.isApplicable(new byte[] {1, 2})).isTrue();
  }

  @Test
  void emptyArrayIsNotApplicable() {
    assertThat(WRITER.isApplicable(new int[0])).isFalse();
    assertThat(WRITER.isApplicable(new String[0])).isFalse();
  }

  @Test
  void iterableOfParametersIsApplicable() {
    assertThat(WRITER.isApplicable(List.of(1, 2))).isTrue();
    assertThat(WRITER.isApplicable(List.of("one", "two"))).isTrue();
  }

  @Test
  void emptyIterableIsNotApplicable() {
    assertThat(WRITER.isApplicable(List.of())).isFalse();
  }

  @Test
  void scalarAndIterableOfFilesAreNotApplicable() {
    assertThat(WRITER.isApplicable("one")).isFalse();
    assertThat(WRITER.isApplicable(List.of(new File("file.txt")))).isFalse();
  }

  @Test
  void iterableIsWrittenAsOnePartPerElement() {
    Output output = new Output(UTF_8);

    WRITER.write(output, FIXED_BOUNDARY, "tags", List.of("one", "two"));

    assertThat(new String(output.toByteArray(), UTF_8))
        .isEqualTo(formatDelimitedTextPart("tags", "one") + formatDelimitedTextPart("tags", "two"));
  }

  @Test
  void intAndLongArraysAreWrittenAsOneTextPartPerElement() {
    Output output = new Output(UTF_8);

    WRITER.write(output, FIXED_BOUNDARY, "ids", new int[] {1, 2});
    WRITER.write(output, FIXED_BOUNDARY, "times", new long[] {10L, 20L});

    assertThat(new String(output.toByteArray(), UTF_8))
        .isEqualTo(
            formatDelimitedTextPart("ids", "1")
                + formatDelimitedTextPart("ids", "2")
                + formatDelimitedTextPart("times", "10")
                + formatDelimitedTextPart("times", "20"));
  }

  @Test
  void primitiveBooleanArrayIsWrittenAsOnePartPerElement() {
    Output output = new Output(UTF_8);

    WRITER.write(output, FIXED_BOUNDARY, "flags", new boolean[] {true, false});

    assertThat(new String(output.toByteArray(), UTF_8))
        .isEqualTo(
            formatDelimitedTextPart("flags", "true") + formatDelimitedTextPart("flags", "false"));
  }

  @Test
  void byteArrayIsWrittenAsOneBinaryPartInsteadOfRepeatedParts() {
    String body =
        new String(
            encodeMultipart(Map.<String, Object>of("blob", new byte[] {0, 1, 42})), ISO_8859_1);

    assertThat(Pattern.compile("name=\"blob\"").matcher(body).results()).hasSize(1);
    assertThat(body).contains("Content-Type: application/octet-stream");
  }

  private static String formatDelimitedTextPart(String name, String payload) {
    return "--"
        + FIXED_BOUNDARY
        + "\r\n"
        + "Content-Disposition: form-data; name=\""
        + name
        + "\"\r\n"
        + TEXT_PLAIN_CONTENT_TYPE_HEADER
        + payload
        + "\r\n";
  }

  private static byte[] encodeMultipart(Map<String, Object> formFields) {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "multipart/form-data");

    new FormEncoder().encode(formFields, Map.class, template);

    return template.body();
  }
}
