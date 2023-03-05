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
package feign.jaxrs;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import feign.MethodMetadata;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class JAXRSContractTestSupport<E> {

  private static final List<String> STRING_LIST = null;

  protected abstract MethodMetadata parseAndValidateMetadata(
                                                             Class<?> targetType,
                                                             String method,
                                                             Class<?>... parameterTypes)
      throws NoSuchMethodException;

  protected abstract E createContract();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  protected E contract = createContract();

  @Test
  public void httpMethods() throws Exception {
    assertThat(parseAndValidateMetadata(methodsClass(), "post").template()).hasMethod("POST");

    assertThat(parseAndValidateMetadata(methodsClass(), "put").template()).hasMethod("PUT");

    assertThat(parseAndValidateMetadata(methodsClass(), "get").template()).hasMethod("GET");

    assertThat(parseAndValidateMetadata(methodsClass(), "delete").template()).hasMethod("DELETE");
  }

  @Test
  public void customMethodWithoutPath() throws Exception {
    assertThat(parseAndValidateMetadata(customMethodClass(), "patch").template())
        .hasMethod("PATCH")
        .hasUrl("/");
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "none").template())
        .hasPath("/")
        .hasQueries();

    assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "one").template())
        .hasPath("/")
        .hasQueries(entry("Action", asList("GetUser")));

    assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "two").template())
        .hasPath("/")
        .hasQueries(entry("Action", asList("GetUser")), entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "three").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")),
            entry("limit", asList("1")));

    assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "empty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()),
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));
  }

  @Test
  public void producesAddsAcceptHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "produces");

    /* multiple @Produces annotations should be additive */
    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/json")),
            entry("Accept", asList("application/xml")));
  }

  @Test
  public void producesMultipleAddsAcceptHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(producesAndConsumesClass(), "producesMultiple");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", Collections.singletonList("application/json")),
            entry("Accept", asList("application/xml", "text/plain")));
  }

  @Test
  public void producesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesNada");

    parseAndValidateMetadata(producesAndConsumesClass(), "producesNada");
  }

  @Test
  public void producesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesEmpty");

    parseAndValidateMetadata(producesAndConsumesClass(), "producesEmpty");
  }

  @Test
  public void consumesAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "consumes");

    /* multiple @Consumes annotations are additive */
    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")), entry("Accept", asList("text/html")));
  }

  @Test
  public void consumesMultipleAddsContentTypeHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(producesAndConsumesClass(), "consumesMultiple");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Accept", Collections.singletonList("text/html")));
  }

  @Test
  public void consumesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesNada");

    parseAndValidateMetadata(producesAndConsumesClass(), "consumesNada");
  }

  @Test
  public void consumesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesEmpty");

    parseAndValidateMetadata(producesAndConsumesClass(), "consumesEmpty");
  }

  @Test
  public void producesAndConsumesOnClassAddsHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(producesAndConsumesClass(), "producesAndConsumes");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/json")),
            entry("Accept", asList("text/html")));
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(bodyParamsClass(), "post", List.class);

    assertThat(md.bodyIndex()).isEqualTo(0);
    assertThat(md.bodyType())
        .isEqualTo(JAXRSContractTestSupport.class.getDeclaredField("STRING_LIST").getGenericType());
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");

    parseAndValidateMetadata(bodyParamsClass(), "tooMany", List.class, List.class);
  }

  @Test
  public void emptyPathOnType() throws Exception {
    assertThat(parseAndValidateMetadata(emptyPathOnTypeClass(), "base").template()).hasUrl("/");
  }

  @Test
  public void emptyPathOnTypeSpecific() throws Exception {
    assertThat(parseAndValidateMetadata(emptyPathOnTypeClass(), "get").template())
        .hasUrl("/specific");
  }

  @Test
  public void parsePathMethod() throws Exception {
    assertThat(parseAndValidateMetadata(pathOnTypeClass(), "base").template()).hasUrl("/base");

    assertThat(parseAndValidateMetadata(pathOnTypeClass(), "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void emptyPathOnMethod() throws Exception {
    assertThat(parseAndValidateMetadata(pathOnTypeClass(), "emptyPath").template()).hasUrl("/base");
  }

  @Test
  public void emptyPathParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("PathParam.value() was empty on parameter 0");

    parseAndValidateMetadata(pathOnTypeClass(), "emptyPathParam", String.class);
  }

  @Test
  public void pathParamWithSpaces() throws Exception {
    assertThat(
        parseAndValidateMetadata(pathOnTypeClass(), "pathParamWithSpaces", String.class)
            .template())
                .hasUrl("/base/{param}");
  }

  @Test
  public void regexPathOnMethodOrType() throws Exception {
    assertThat(
        parseAndValidateMetadata(pathOnTypeClass(), "pathParamWithRegex", String.class)
            .template())
                .hasUrl("/base/regex/{param}");

    assertThat(
        parseAndValidateMetadata(
            pathOnTypeClass(), "pathParamWithMultipleRegex", String.class, String.class)
                .template())
                    .hasUrl("/base/regex/{param1}/{param2}");

    assertThat(
        parseAndValidateMetadata(
            complexPathOnTypeClass(),
            "pathParamWithMultipleRegex",
            String.class,
            String.class)
                .template())
                    .hasUrl("/{baseparam}/regex/{param1}/{param2}");
  }

  @Test
  public void withPathAndURIParams() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            withURIParamClass(), "uriParam", String.class, URI.class, String.class);

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("1")),
            // Skips 1 as it is a url index!
            entry(2, asList("2")));

    assertThat(md.urlIndex()).isEqualTo(1);
  }

  @Test
  public void pathAndQueryParams() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            withPathAndQueryParamsClass(),
            "recordsByNameAndType",
            int.class,
            String.class,
            String.class);

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("domainId")), entry(1, asList("name")), entry(2, asList("type")));
  }

  @Test
  public void emptyQueryParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("QueryParam.value() was empty on parameter 0");

    parseAndValidateMetadata(withPathAndQueryParamsClass(), "empty", String.class);
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            formParamsClass(), "login", String.class, String.class, String.class);

    assertThat(md.formParams()).containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("customer_name")),
            entry(1, asList("user_name")),
            entry(2, asList("password")));
  }

  /** Body type is only for the body param. */
  @Test
  public void formParamsDoesNotSetBodyType() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            formParamsClass(), "login", String.class, String.class, String.class);

    assertThat(md.bodyType()).isNull();
  }

  @Test
  public void emptyFormParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("FormParam.value() was empty on parameter 0");

    parseAndValidateMetadata(formParamsClass(), "emptyFormParam", String.class);
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(headerParamsClass(), "logout", String.class);

    assertThat(md.template()).hasHeaders(entry("Auth-Token", asList("{Auth-Token}")));

    assertThat(md.indexToName()).containsExactly(entry(0, asList("Auth-Token")));
  }

  @Test
  public void emptyHeaderParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("HeaderParam.value() was empty on parameter 0");

    parseAndValidateMetadata(headerParamsClass(), "emptyHeaderParam", String.class);
  }

  @Test
  public void pathsWithoutSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(pathsWithoutAnySlashesClass(), "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(pathsWithSomeSlashesClass(), "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeOtherSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(pathsWithSomeOtherSlashesClass(), "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void classWithRootPathParsesCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(classRootPathClass(), "get").template())
        .hasUrl("/specific");
  }

  @Test
  public void classPathWithTrailingSlashParsesCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(classPathWithTrailingSlashClass(), "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void methodPathWithoutLeadingSlashParsesCorrectly() throws Exception {
    assertThat(
        parseAndValidateMetadata(methodWithFirstPathThenGetWithoutLeadingSlashClass(), "get")
            .template())
                .hasUrl("/base/specific");
  }

  @Test
  public void producesWithHeaderParamContainAllHeaders() throws Exception {
    assertThat(
        parseAndValidateMetadata(
            mixedAnnotationsClass(),
            "getWithHeaders",
            String.class,
            String.class,
            String.class)
                .template())
                    .hasHeaders(entry("Accept", Arrays.asList("application/json", "{Accept}")))
                    .hasQueries(
                        entry("multiple", Arrays.asList("stuff", "{multiple}")),
                        entry("another", Collections.singletonList("{another}")));
  }

  protected abstract Class<?> methodsClass();

  protected abstract Class<?> customMethodClass();

  protected abstract Class<?> withQueryParamsInPathClass();

  protected abstract Class<?> producesAndConsumesClass();

  protected abstract Class<?> bodyParamsClass();

  protected abstract Class<?> emptyPathOnTypeClass();

  protected abstract Class<?> pathOnTypeClass();

  protected abstract Class<?> complexPathOnTypeClass();

  protected abstract Class<?> withURIParamClass();

  protected abstract Class<?> withPathAndQueryParamsClass();

  protected abstract Class<?> formParamsClass();

  protected abstract Class<?> headerParamsClass();

  protected abstract Class<?> pathsWithoutAnySlashesClass();

  protected abstract Class<?> pathsWithSomeSlashesClass();

  protected abstract Class<?> pathsWithSomeOtherSlashesClass();

  protected abstract Class<?> classRootPathClass();

  protected abstract Class<?> classPathWithTrailingSlashClass();

  protected abstract Class<?> methodWithFirstPathThenGetWithoutLeadingSlashClass();

  protected abstract Class<?> mixedAnnotationsClass();
}
