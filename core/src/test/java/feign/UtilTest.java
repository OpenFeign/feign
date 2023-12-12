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
package feign;

import static feign.Util.caseInsensitiveCopyOf;
import static feign.Util.emptyToNull;
import static feign.Util.removeValues;
import static feign.Util.resolveLastTypeParameter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import feign.codec.Decoder;

class UtilTest {

  @Test
  void removesEmptyStrings() {
    String[] values = new String[] {"", null};
    assertThat(removeValues(values, value -> emptyToNull(value) == null, String.class))
        .isEmpty();
  }

  @Test
  void removesEvenNumbers() {
    Integer[] values = {22, 23};
    assertThat(removeValues(values, number -> number % 2 == 0, Integer.class))
        .containsExactly(23);
  }

  @Test
  void emptyValueOf() throws Exception {
    assertThat(Util.emptyValueOf(boolean.class)).isEqualTo(false);
    assertThat(Util.emptyValueOf(Boolean.class)).isEqualTo(false);
    assertThat((byte[]) Util.emptyValueOf(byte[].class)).isEmpty();
    assertThat(Util.emptyValueOf(Collection.class)).isEqualTo(Collections.emptyList());
    assertThat(((Iterator<?>) Util.emptyValueOf(Iterator.class)).hasNext()).isFalse();
    assertThat(Util.emptyValueOf(List.class)).isEqualTo(Collections.emptyList());
    assertThat(Util.emptyValueOf(Map.class)).isEqualTo(Collections.emptyMap());
    assertThat(Util.emptyValueOf(Set.class)).isEqualTo(Collections.emptySet());
    assertThat(Util.emptyValueOf(Optional.class)).isEqualTo(Optional.empty());
  }

  /** In other words, {@code List<String>} is as empty as {@code List<?>}. */
  @Test
  void emptyValueOf_considersRawType() throws Exception {
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    assertThat((List<?>) Util.emptyValueOf(listStringType)).isEmpty();
  }

  /** Ex. your {@code Foo} object would be null, but so would things like Number. */
  @Test
  void emptyValueOf_nullForUndefined() throws Exception {
    assertThat(Util.emptyValueOf(Number.class)).isNull();
    assertThat(Util.emptyValueOf(Parameterized.class)).isNull();
  }

  @Test
  void resolveLastTypeParameterWhenNotSubtype() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertThat(last).isEqualTo(listStringType);
  }

  @Test
  void lastTypeFromInstance() throws Exception {
    Parameterized<?> instance = new ParameterizedSubtype();
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertThat(last).isEqualTo(String.class);
  }

  @Test
  void lastTypeFromAnonymous() throws Exception {
    Parameterized<?> instance = new Parameterized<Reader>() {};
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertThat(last).isEqualTo(Reader.class);
  }

  @Test
  void resolveLastTypeParameterWhenWildcard() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_WILDCARD_LIST_STRING")
            .getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertThat(last).isEqualTo(listStringType);
  }

  @Test
  void resolveLastTypeParameterWhenParameterizedSubtype() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_LIST_STRING")
            .getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertThat(last).isEqualTo(listStringType);
  }

  @Test
  void unboundWildcardIsObject() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_UNBOUND").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertThat(last).isEqualTo(Object.class);
  }

  @Test
  void checkArgumentInputFalseNotNullNullOutputIllegalArgumentException() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      // Arrange
      final boolean expression = false;
      final String errorMessageTemplate = "";
      final Object[] errorMessageArgs = null;
      Util.checkArgument(expression, errorMessageTemplate, errorMessageArgs);
      // Method is not expected to return due to exception thrown
    });
    // Method is not expected to return due to exception thrown
  }

  @Test
  void checkNotNullInputNullNotNullNullOutputNullPointerException() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
      // Arrange
      final Object reference = null;
      final String errorMessageTemplate = "";
      final Object[] errorMessageArgs = null;
      Util.checkNotNull(reference, errorMessageTemplate, errorMessageArgs);
      // Method is not expected to return due to exception thrown
    });
    // Method is not expected to return due to exception thrown
  }

  @Test
  void checkNotNullInputZeroNotNull0OutputZero() {
    // Arrange
    final Object reference = 0;
    final String errorMessageTemplate = "   ";
    final Object[] errorMessageArgs = {};
    // Act
    final Object retval = Util.checkNotNull(reference, errorMessageTemplate, errorMessageArgs);
    // Assert result
    assertThat(retval).isEqualTo(0);
  }

  @Test
  void checkStateInputFalseNotNullNullOutputIllegalStateException() {
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
      // Arrange
      final boolean expression = false;
      final String errorMessageTemplate = "";
      final Object[] errorMessageArgs = null;
      Util.checkState(expression, errorMessageTemplate, errorMessageArgs);
      // Method is not expected to return due to exception thrown
    });
    // Method is not expected to return due to exception thrown
  }

  @Test
  void emptyToNullInputNotNullOutputNotNull() {
    // Arrange
    final String string = "AAAAAAAA";
    // Act
    final String retval = Util.emptyToNull(string);
    // Assert result
    assertThat(retval).isEqualTo("AAAAAAAA");
  }

  @Test
  void emptyToNullInputNullOutputNull() {
    // Arrange
    final String string = null;
    // Act
    final String retval = Util.emptyToNull(string);
    // Assert result
    assertThat(retval).isNull();
  }

  @Test
  void isBlankInputNotNullOutputFalse() {
    // Arrange
    final String value = "AAAAAAAA";
    // Act
    final boolean retval = Util.isBlank(value);
    // Assert result
    assertThat(retval).isEqualTo(false);
  }

  @Test
  void isBlankInputNullOutputTrue() {
    // Arrange
    final String value = null;
    // Act
    final boolean retval = Util.isBlank(value);
    // Assert result
    assertThat(retval).isEqualTo(true);
  }

  @Test
  void isNotBlankInputNotNullOutputFalse() {
    // Arrange
    final String value = "";
    // Act
    final boolean retval = Util.isNotBlank(value);
    // Assert result
    assertThat(retval).isEqualTo(false);
  }

  @Test
  void isNotBlankInputNotNullOutputTrue() {
    // Arrange
    final String value = "AAAAAAAA";
    // Act
    final boolean retval = Util.isNotBlank(value);
    // Assert result
    assertThat(retval).isEqualTo(true);
  }

  @Test
  void caseInsensitiveCopyOfMap() {
    // Arrange
    Map<String, Collection<String>> sourceMap = new HashMap<>();

    sourceMap.put("First", Arrays.asList("abc", "qwerty", "xyz"));
    sourceMap.put("camelCase", Collections.singleton("123"));
    // Act
    Map<String, Collection<String>> actualMap = caseInsensitiveCopyOf(sourceMap);
    // Assert result
    assertThat(actualMap)
        .hasEntrySatisfying("First", value -> {
          assertThat(value).contains("xyz", "abc", "qwerty");
        }).hasEntrySatisfying("first", value -> {
          assertThat(value).contains("xyz", "abc", "qwerty");
        }).hasEntrySatisfying("CAMELCASE", value -> {
          assertThat(value).contains("123");
        }).hasEntrySatisfying("camelcase", value -> {
          assertThat(value).contains("123");
        });
  }

  @Test
  void copyIsUnmodifiable() {
    // Arrange
    Map<String, Collection<String>> sourceMap = new HashMap<>();

    sourceMap.put("First", Arrays.asList("abc", "qwerty", "xyz"));
    sourceMap.put("camelCase", Collections.singleton("123"));
    // Act
    Map<String, Collection<String>> unmodifiableMap = caseInsensitiveCopyOf(sourceMap);
    // Assert result
    assertThatThrownBy(() -> unmodifiableMap.put("new", Collections.singleton("223322")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> unmodifiableMap.get("camelCase").clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullMap() {
    // Act
    Map<String, Collection<String>> actualMap = caseInsensitiveCopyOf(null);
    // Assert result
    assertThat(actualMap)
        .isNotNull()
        .isEmpty();
  }

  interface LastTypeParameter {
    List<String> LIST_STRING = null;
    Parameterized<List<String>> PARAMETERIZED_LIST_STRING = null;
    Parameterized<? extends List<String>> PARAMETERIZED_WILDCARD_LIST_STRING = null;
    ParameterizedDecoder<List<String>> PARAMETERIZED_DECODER_LIST_STRING = null;
    ParameterizedDecoder<?> PARAMETERIZED_DECODER_UNBOUND = null;
  }

  interface ParameterizedDecoder<T extends List<String>> extends Decoder {

  }

  interface Parameterized<T> {

  }

  static class ParameterizedSubtype implements Parameterized<String> {

  }
}
