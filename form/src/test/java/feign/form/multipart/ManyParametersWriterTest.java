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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import feign.form.FormEncoder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ManyParametersWriter} writes arrays and {@link Iterable}s of parameters as one part per
 * element. Its predicate reads the first element reflectively, so a primitive array such as {@code
 * int[]} is recognised instead of failing on an {@code (Object[])} cast.
 */
class ManyParametersWriterTest {

  private static final ManyParametersWriter WRITER = new ManyParametersWriter();

  private static final String BOUNDARY = "boundary";

  private static final String TEXT_PART_HEADER = "Content-Type: text/plain; charset=UTF-8\r\n\r\n";

  @Test
  void arrayOfParametersIsApplicable() {
    assertThat(WRITER.isApplicable(new int[] {1, 2})).isTrue();
    assertThat(WRITER.isApplicable(new boolean[] {true, false})).isTrue();
    assertThat(WRITER.isApplicable(new String[] {"one", "two"})).isTrue();
  }

  @Test
  void byteArrayIsApplicableBecauseByteIsANumber() {
    assertThat(WRITER.isApplicable(new byte[] {1, 2})).isTrue();
  }

  @Test
  void charArrayIsNotApplicable() {
    assertThat(WRITER.isApplicable(new char[] {'a', 'b'})).isFalse();
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
  void valuesWithoutParametersAreNotApplicable() {
    assertThat(WRITER.isApplicable("one")).isFalse();
    assertThat(WRITER.isApplicable(List.of(new File("file.txt")))).isFalse();
  }

  @Test
  void iterableIsWrittenAsOnePartPerElement() {
    Output output = new Output(UTF_8);

    WRITER.write(output, BOUNDARY, "tags", List.of("one", "two"));

    assertThat(new String(output.toByteArray(), UTF_8))
        .isEqualTo(expectedPart("tags", "one") + expectedPart("tags", "two"));
  }

  @Test
  void arrayOfNumbersIsWrittenAsRepeatedParts() {
    List<String> parts = textParts(map("ids", new int[] {1, 2}, "times", new long[] {10L, 20L}));

    assertThat(parts).hasSize(4);
    assertThat(parts).allSatisfy(part -> assertThat(part).contains(TEXT_PART_HEADER));
    assertThat(payloadsOf(parts, "ids")).containsExactly("1", "2");
    assertThat(payloadsOf(parts, "times")).containsExactly("10", "20");
  }

  @Test
  void arrayOfBooleansIsWrittenAsRepeatedParts() {
    List<String> parts = textParts(map("flags", new boolean[] {true, false}));

    assertThat(parts).hasSize(2);
    assertThat(payloadsOf(parts, "flags")).containsExactly("true", "false");
  }

  @Test
  void byteArrayIsWrittenAsOneBinaryPart() {
    byte[] payload = {0, 1, 42, (byte) 0x80, (byte) 0xFF};

    List<byte[]> parts = splitParts(encodeMultipart(map("blob", payload)));

    assertThat(parts).hasSize(1);
    assertThat(new String(parts.get(0), UTF_8))
        .contains("Content-Disposition: form-data; name=\"blob\"")
        .contains("Content-Type: application/octet-stream")
        .contains("Content-Transfer-Encoding: binary");
    assertThat(parts.get(0)).endsWith(payload);
  }

  private static String expectedPart(String name, String payload) {
    return "--"
        + BOUNDARY
        + "\r\n"
        + "Content-Disposition: form-data; name=\""
        + name
        + "\"\r\n"
        + TEXT_PART_HEADER
        + payload
        + "\r\n";
  }

  private static Map<String, Object> map(Object... keysAndValues) {
    Map<String, Object> data = new LinkedHashMap<>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      data.put((String) keysAndValues[index], keysAndValues[index + 1]);
    }
    return data;
  }

  private static RequestTemplate encodeMultipart(Map<String, Object> data) {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "multipart/form-data");

    new FormEncoder().encode(data, Map.class, template);

    return template;
  }

  private static List<String> textParts(Map<String, Object> data) {
    List<String> parts = new ArrayList<>();
    for (byte[] part : splitParts(encodeMultipart(data))) {
      parts.add(new String(part, UTF_8));
    }
    return parts;
  }

  private static List<String> payloadsOf(List<String> parts, String name) {
    List<String> payloads = new ArrayList<>();
    for (String part : parts) {
      if (part.contains("name=\"" + name + "\"")) {
        payloads.add(part.substring(part.indexOf("\r\n\r\n") + 4));
      }
    }
    return payloads;
  }

  private static List<byte[]> splitParts(RequestTemplate template) {
    return splitParts(template.body(), boundaryOf(template));
  }

  /** Reads the boundary the processor announced in the {@code Content-Type} header. */
  private static String boundaryOf(RequestTemplate template) {
    String contentType = template.headers().get("Content-Type").iterator().next();
    assertThat(contentType).startsWith("multipart/form-data; charset=UTF-8; boundary=");
    return contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
  }

  private static List<byte[]> splitParts(byte[] body, String boundary) {
    byte[] delimiter = ("--" + boundary).getBytes(UTF_8);

    List<Integer> positions = new ArrayList<>();
    int position = indexOf(body, delimiter, 0);
    while (position >= 0) {
      positions.add(position);
      position = indexOf(body, delimiter, position + delimiter.length);
    }

    List<byte[]> parts = new ArrayList<>();
    for (int index = 0; index + 1 < positions.size(); index++) {
      int start = positions.get(index) + delimiter.length;
      int end = positions.get(index + 1);
      if (end - start < 4 || body[start] != '\r' || body[start + 1] != '\n') {
        continue; // the closing delimiter, "--<boundary>--", is not a part
      }
      parts.add(Arrays.copyOfRange(body, start + 2, end - 2));
    }
    return parts;
  }

  private static int indexOf(byte[] haystack, byte[] needle, int from) {
    for (int index = Math.max(from, 0); index <= haystack.length - needle.length; index++) {
      if (matchesAt(haystack, needle, index)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean matchesAt(byte[] haystack, byte[] needle, int offset) {
    for (int index = 0; index < needle.length; index++) {
      if (haystack[offset + index] != needle[index]) {
        return false;
      }
    }
    return true;
  }
}
