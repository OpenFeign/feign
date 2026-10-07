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
package feign.form;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.CollectionFormat;
import feign.RequestTemplate;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * URL encoded forms expand array values into repeated fields. Primitive arrays have to expand like
 * reference arrays, so their elements are read reflectively instead of being cast to {@code
 * Object[]}.
 */
class FormEncoderPrimitiveArrayTest {

  private static final String URLENCODED = "application/x-www-form-urlencoded; charset=utf-8";

  @Test
  void booleanArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("flags", new boolean[] {true, false})).isEqualTo("flags=true&flags=false");
  }

  @Test
  void byteArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("sizes", new byte[] {1, 2})).isEqualTo("sizes=1&sizes=2");
  }

  @Test
  void charArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("letters", new char[] {'a', 'b'})).isEqualTo("letters=a&letters=b");
  }

  @Test
  void doubleArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("ratios", new double[] {1.25, 2.5})).isEqualTo("ratios=1.25&ratios=2.5");
  }

  @Test
  void floatArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("ratios", new float[] {1.5F, 2.5F})).isEqualTo("ratios=1.5&ratios=2.5");
  }

  @Test
  void intArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("sizes", new int[] {1, 2})).isEqualTo("sizes=1&sizes=2");
  }

  @Test
  void longArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("times", new long[] {10L, 20L})).isEqualTo("times=10&times=20");
  }

  @Test
  void shortArrayIsExplodedIntoRepeatedFields() {
    assertThat(encode("counts", new short[] {3, 4})).isEqualTo("counts=3&counts=4");
  }

  @Test
  void arrayValuesHonourTheCollectionFormatOfTheRequest() {
    assertThat(encode("sizes", new int[] {1, 2}, CollectionFormat.CSV)).isEqualTo("sizes=1%2C2");
  }

  @Test
  void emptyPrimitiveArrayAddsNoField() {
    assertThat(encode("sizes", new int[0])).isEmpty();
  }

  @Test
  void referenceArrayIsStillExplodedIntoRepeatedFields() {
    assertThat(encode("tags", new String[] {"one", "two"})).isEqualTo("tags=one&tags=two");
  }

  @Test
  void iterableIsStillExplodedIntoRepeatedFields() {
    assertThat(encode("tags", Arrays.asList("one", "two"))).isEqualTo("tags=one&tags=two");
  }

  @Test
  void scalarValueIsEncodedAsASingleField() {
    assertThat(encode("timeout", Duration.ofSeconds(5))).isEqualTo("timeout=PT5S");
  }

  private static String encode(String name, Object value) {
    return encode(name, value, null);
  }

  private static String encode(String name, Object value, CollectionFormat collectionFormat) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(name, value);
    return encode(data, collectionFormat);
  }

  private static String encode(Map<String, Object> data, CollectionFormat collectionFormat) {
    RequestTemplate template = new RequestTemplate();
    if (collectionFormat != null) {
      template.collectionFormat(collectionFormat);
    }
    template.header("Content-Type", URLENCODED);

    new FormEncoder().encode(data, Map.class, template);

    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=UTF-8");
    return new String(template.body(), UTF_8);
  }
}
