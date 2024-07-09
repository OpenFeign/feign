/*
 * Copyright 2012-2024 The Feign Authors
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

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests interface inheritance defined per {@link Contract.Default} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
class DefaultContractInheritanceTest {

  Contract.Default contract = new Contract.Default();

  @Headers("Foo: Bar")
  interface SimpleParameterizedBaseApi<M> {

    @RequestLine("GET /api/{zoneId}")
    M get(@Param("key") String key);
  }

  interface SimpleParameterizedApi extends SimpleParameterizedBaseApi<String> {

  }

  @Test
  void simpleParameterizedBaseApi() throws Exception {
    final List<MethodMetadata> md = contract.parseAndValidateMetadata(SimpleParameterizedApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.getFirst().configKey()).isEqualTo("SimpleParameterizedApi#get(String)");
    assertThat(md.getFirst().returnType()).isEqualTo(String.class);
    assertThat(md.getFirst().template()).hasHeaders(entry("Foo", asList("Bar")));
  }

  @Test
  void parameterizedApiUnsupported() throws Exception {
    Throwable exception = assertThrows(IllegalStateException.class,
        () -> contract.parseAndValidateMetadata(SimpleParameterizedBaseApi.class));
    assertThat(exception.getMessage())
        .contains("Parameterized types unsupported: SimpleParameterizedBaseApi");
  }

  interface OverrideParameterizedApi extends SimpleParameterizedBaseApi<String> {

    @Override
    @RequestLine("GET /api/{zoneId}")
    String get(@Param("key") String key);
  }

  @Headers("Foo: Bar")
  interface SimpleBaseApi {

    @RequestLine("GET /api/{zoneId}")
    String get(@Param("key") String key);
  }

  interface OverrideSimpleApi extends SimpleBaseApi {

    @Override
    @RequestLine("GET /api/v2/{zoneId}")
    String get(@Param("key") String key);
  }

  @Test
  void overrideParameterizedApiSupported() {
    contract.parseAndValidateMetadata(OverrideParameterizedApi.class);
  }

  @Test
  void overrideSimpleApiSupported() {
    contract.parseAndValidateMetadata(OverrideSimpleApi.class);
  }

  interface Child<T> extends SimpleParameterizedBaseApi<List<T>> {

  }

  interface GrandChild extends Child<String> {

  }

  interface SingleInheritanceChild extends SimpleParameterizedBaseApi<String> {
  }

  interface FirstServiceBaseApi<T> {
    T get(String key);
  }

  interface SecondServiceBaseApi<T> {
    void update(String key, T value);
  }

  interface MultipleInheritanceDoneWrong
      extends SimpleParameterizedBaseApi<String>, FirstServiceBaseApi<String>,
      SecondServiceBaseApi<String> {
  }

  @Headers("Foo: Bar")
  interface MultipleInheritanceParameterizedBaseApi<T>
      extends FirstServiceBaseApi<T>, SecondServiceBaseApi<T> {

    @Override
    @RequestLine("GET /api/{zoneId}")
    T get(@Param("key") String key);

    @Override
    @RequestLine("POST /api/{zoneId}")
    @Body("%7B\"value\": \"{value}\"%7D")
    void update(@Param("key") String key, @Param("value") T value);
  }

  interface MultipleInheritanceDoneCorrectly
      extends MultipleInheritanceParameterizedBaseApi<String> {
  }

  interface ServiceApi<T> extends FirstServiceBaseApi<T>, SecondServiceBaseApi<T> {
  }

  @Headers("Foo: Bar")
  interface MultipleInheritanceApi extends ServiceApi<String> {

    @Override
    @RequestLine("GET /api/{zoneId}")
    String get(@Param("key") String key);

    @Override
    @RequestLine("POST /api/{zoneId}")
    @Body("%7B\"value\": \"{value}\"%7D")
    void update(@Param("key") String key, @Param("value") String value);
  }

  @Test
  void singleInheritance() {
    contract.parseAndValidateMetadata(SingleInheritanceChild.class);
  }

  @Test
  void multipleInheritanceDoneWrong() {
    Throwable exception = assertThrows(IllegalStateException.class,
        () -> contract.parseAndValidateMetadata(MultipleInheritanceDoneWrong.class));
    assertThat(exception.getMessage())
        .contains("Only single inheritance supported: MultipleInheritanceDoneWrong");
  }

  @Test
  void multipleInheritanceDoneCorrectly() {
    contract.parseAndValidateMetadata(MultipleInheritanceDoneCorrectly.class);
  }

  @Test
  void multipleInheritanceDoneCorrectly2() {
    contract.parseAndValidateMetadata(MultipleInheritanceApi.class);
  }

  @Test
  void multipleInheritanceSupported() {
    contract.parseAndValidateMetadata(GrandChild.class);
  }
}
