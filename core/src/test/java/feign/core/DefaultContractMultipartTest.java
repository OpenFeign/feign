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
package feign.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.Contract;
import feign.MethodMetadata;
import feign.Param;
import feign.Part;
import feign.PartMetadata;
import feign.RequestLine;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class DefaultContractMultipartTest {
  private static final Contract CONTRACT = new DefaultContract();

  @Test
  void shouldParseMultiHeaderPart() {
    var expected =
        new PartMetadata(
            0,
            String.class,
            Map.of(
                "Content-Disposition", List.of("form-data; name=\"file\""),
                "Content-Type", List.of("text/plain")),
            true);
    var metadata = CONTRACT.parseAndValidateMetadata(StandardPart.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldExpandShorthandValueToContentDisposition() {
    var expected =
        new PartMetadata(
            0,
            String.class,
            Map.of("Content-Disposition", List.of("form-data; name=\"file\"")),
            true);
    var metadata = CONTRACT.parseAndValidateMetadata(ShorthandPart.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldPreserveExplodeFlag() throws NoSuchMethodException {
    var type =
        PartWithExplodeDisabled.class.getMethod("upload", List.class).getGenericParameterTypes()[0];
    var expected =
        new PartMetadata(
            0, type, Map.of("Content-Disposition", List.of("form-data; name=\"file\"")), false);
    var metadata = CONTRACT.parseAndValidateMetadata(PartWithExplodeDisabled.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldParseHeadersAttribute() {
    var expected =
        new PartMetadata(
            0,
            String.class,
            Map.of(
                "Content-Disposition", List.of("form-data; name=\"file\""),
                "Content-Type", List.of("text/plain")),
            true);
    var metadata = CONTRACT.parseAndValidateMetadata(PartWithHeadersAttribute.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldRejectBothValueAndHeaders() {
    assertThrows(
        IllegalStateException.class,
        () -> CONTRACT.parseAndValidateMetadata(PartWithBothValueAndHeaders.class));
  }

  @Test
  void shouldRejectNeitherValueNorHeaders() {
    assertThrows(
        IllegalStateException.class,
        () -> CONTRACT.parseAndValidateMetadata(PartWithNeitherValueNorHeaders.class));
  }

  @Test
  void shouldResolveGenericType() throws NoSuchMethodException {
    var type =
        PartWithGenericType.class.getMethod("upload", List.class).getGenericParameterTypes()[0];
    var expected =
        new PartMetadata(
            0, type, Map.of("Content-Disposition", List.of("form-data; name=\"file\"")), true);
    var metadata = CONTRACT.parseAndValidateMetadata(PartWithGenericType.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldRejectBodyWithPart() {
    assertThrows(
        IllegalStateException.class,
        () -> CONTRACT.parseAndValidateMetadata(PartWithBodyConflict.class));
  }

  @Test
  void shouldAllowParamWithPart() {
    var expected =
        new PartMetadata(
            0,
            String.class,
            Map.of("Content-Disposition", List.of("form-data; name=\"file\"")),
            true);
    var metadata = CONTRACT.parseAndValidateMetadata(PartWithFormParamAllowed.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, expected));
  }

  @Test
  void shouldAcceptMultipleParts() {
    var part0 =
        new PartMetadata(
            0,
            String.class,
            Map.of("Content-Disposition", List.of("form-data; name=\"file\"")),
            true);
    var part1 =
        new PartMetadata(
            1,
            byte[].class,
            Map.of("Content-Disposition", List.of("form-data; name=\"data\"")),
            true);
    var metadata = CONTRACT.parseAndValidateMetadata(MultipleParts.class);

    assertThat(metadata)
        .singleElement()
        .extracting(MethodMetadata::partMetadata, InstanceOfAssertFactories.MAP)
        .isEqualTo(Map.of(0, part0, 1, part1));
  }

  interface StandardPart {
    @RequestLine("POST /upload")
    void upload(
        @Part({"Content-Disposition: form-data; name=\"file\"", "Content-Type: text/plain"})
            String file);
  }

  interface ShorthandPart {
    @RequestLine("POST /upload")
    void upload(@Part("file") String file);
  }

  interface PartWithExplodeDisabled {
    @RequestLine("POST /upload")
    void upload(@Part(value = "file", explode = false) List<String> files);
  }

  interface PartWithHeadersAttribute {
    @RequestLine("POST /upload")
    void upload(
        @Part(
                headers = {
                  "Content-Disposition: form-data; name=\"file\"",
                  "Content-Type: text/plain"
                })
            String file);
  }

  interface PartWithBothValueAndHeaders {
    @RequestLine("POST /upload")
    void upload(@Part(value = "file", headers = "Content-Type: text/plain") String file);
  }

  interface PartWithNeitherValueNorHeaders {
    @RequestLine("POST /upload")
    void upload(@Part String file);
  }

  interface PartWithGenericType {
    @RequestLine("POST /upload")
    void upload(@Part("file") List<String> files);
  }

  interface PartWithBodyConflict {
    @RequestLine("POST /upload")
    void upload(@Part("file") String file, String body);
  }

  interface PartWithFormParamAllowed {
    @RequestLine("POST /upload")
    void upload(@Part("file") String file, @Param("form") String form);
  }

  interface MultipleParts {
    @RequestLine("POST /upload")
    void upload(@Part("file") String file, @Part("data") byte[] data);
  }
}
