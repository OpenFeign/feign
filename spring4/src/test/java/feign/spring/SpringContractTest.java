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
package feign.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import feign.Feign;
import feign.Param;
import feign.Request;
import feign.Response;
import feign.ResponseMapper;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;

class SpringContractTest {

  private MockClient mockClient;
  private HealthResource resource;

  @BeforeEach
  void setup() throws IOException {
    Response.Builder response = Response.builder()
        .status(200)
        .body("hello world", StandardCharsets.UTF_8)
        .headers(Collections.singletonMap("Content-Type",
            Collections.singletonList("text/plain")));
    mockClient = new MockClient()
        .noContent(HttpMethod.GET, "/health")
        .noContent(HttpMethod.GET, "/health/1")
        .noContent(HttpMethod.GET, "/health/optional")
        .noContent(HttpMethod.GET, "/health/optional?param=value")
        .noContent(HttpMethod.GET, "/health/optional?param")
        .noContent(HttpMethod.GET, "/health/1?deep=true")
        .noContent(HttpMethod.GET, "/health/1?deep=true&dryRun=true")
        .noContent(HttpMethod.GET, "/health/name?deep=true&dryRun=true")
        .noContent(HttpMethod.POST, "/health/part/1")
        .noContent(HttpMethod.GET, "/health/header")
        .noContent(HttpMethod.GET, "/health/header/map")
        .noContent(HttpMethod.GET, "/health/header/pojo")
        .ok(HttpMethod.GET, "/health/generic", "{}")
        .add(HttpMethod.POST, "/health/text", response);
    resource = Feign.builder()
        .contract(new SpringContract())
        .encoder(new JacksonEncoder())
        .mapAndDecode(new TextResponseMapper(), new JacksonDecoder())
        .client(mockClient)
        .target(new MockTarget<>(HealthResource.class));
  }

  class TextResponseMapper implements ResponseMapper {
    @Override
    public Response map(Response response, Type type) {
      Map<String, Collection<String>> headers = response.headers();
      if (headers == null || headers.isEmpty()) {
        return response;
      }
      Collection<String> head = headers.get("Content-Type");
      if (head == null || head.isEmpty()) {
        return response;
      }
      String contentType = head.iterator().next();
      if (contentType.startsWith("text/plain")) {
        try {
          Reader reader = response.body().asReader(StandardCharsets.UTF_8);
          char[] buff = new char[1024];
          String text = "";
          int n = 0;
          while ((n = reader.read(buff)) > 0) {
            text += new String(buff, 0, n);
          }
          response = response.toBuilder().body("\"" + text + "\"", StandardCharsets.UTF_8).build();
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
      }
      return response;
    }
  }

  @Test
  void noPath() {
    resource.getStatus();

    mockClient.verifyOne(HttpMethod.GET, "/health");
  }

  @Test
  void withName() {
    resource.checkWithName("name", true, true);

    mockClient.verifyOne(HttpMethod.GET, "/health/name?deep=true&dryRun=true");
  }

  @Test
  void optionalPresent() {
    resource.checkWithOptional(Optional.of("value"));

    mockClient.verifyOne(HttpMethod.GET, "/health/optional?param=value");
  }

  @Test
  void optionalNotPresent() {
    resource.checkWithOptional(Optional.empty());

    mockClient.verifyOne(HttpMethod.GET, "/health/optional");
  }

  @Test
  void optionalEmptyValue() {
    resource.checkWithOptional(Optional.of(""));

    mockClient.verifyOne(HttpMethod.GET, "/health/optional?param");
  }

  @Test
  void optionalNullable() {
    resource.checkWithOptional(Optional.ofNullable(null));

    mockClient.verifyOne(HttpMethod.GET, "/health/optional");
  }

  @Test
  void requestPart() {
    resource.checkRequestPart("1", "hello", "6");

    final Request request = mockClient.verifyOne(HttpMethod.POST, "/health/part/1");
    assertThat(request.requestTemplate().methodMetadata().formParams()).containsExactly("name1",
        "grade1");
  }

  @Test
  void requestHeader() {
    resource.checkRequestHeader("hello", "6");

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header");
    assertThat(request.headers()).containsEntry("name1", Arrays.asList("hello"));
    assertThat(request.headers()).containsEntry("grade1", Arrays.asList("6"));
  }

  @Test
  void requestHeaderMap() {
    Map<String, String> map = new HashMap<>();
    map.put("name1", "hello");
    map.put("grade1", "6");
    resource.checkRequestHeaderMap(map);

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header/map");
    assertThat(request.headers()).containsEntry("name1", Arrays.asList("hello"));
    assertThat(request.headers()).containsEntry("grade1", Arrays.asList("6"));
  }

  @Test
  void requestHeaderPojo() {
    HeaderMapUserObject object = new HeaderMapUserObject();
    object.setName("hello");
    object.setGrade("6");
    resource.checkRequestHeaderPojo(object);

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header/pojo");
    assertThat(request.headers()).containsEntry("name1", Arrays.asList("hello"));
    assertThat(request.headers()).containsEntry("grade1", Arrays.asList("6"));
  }

  @Test
  void requestParam() {
    resource.check("1", true);

    mockClient.verifyOne(HttpMethod.GET, "/health/1?deep=true");
  }

  @Test
  void requestTwoParams() {
    resource.check("1", true, true);

    mockClient.verifyOne(HttpMethod.GET, "/health/1?deep=true&dryRun=true");
  }

  @Test
  void inheritance() {
    final Data data = resource.getData(new Data());
    assertThat(data).isNotNull();

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/generic");
    assertThat(request.headers()).containsEntry("Content-Type", Arrays.asList("application/json"));
  }

  @Test
  void composedAnnotation() {
    resource.check("1");

    mockClient.verifyOne(HttpMethod.GET, "/health/1");
  }

  @Test
  void notAHttpMethod() {
    Throwable exception =
        assertThrows(Exception.class, () -> resource.missingResourceExceptionHandler());
    assertThat(exception.getMessage()).contains("is not a method handled by feign");
  }

  @Test
  void consumeAndProduce() {
    resource.produceText(new HashMap<>());
    Request request = mockClient.verifyOne(HttpMethod.POST, "/health/text");
    assertThat(request.headers()).containsEntry("Content-Type", Arrays.asList("application/json"));
    assertThat(request.headers()).containsEntry("Accept", Arrays.asList("text/plain"));
  }

  interface GenericResource<DTO> {

    @RequestMapping(value = "generic", method = RequestMethod.GET)
    public @ResponseBody DTO getData(@RequestBody DTO input);

  }

  @RestController
  @RequestMapping(value = "/health", produces = "text/html")
  interface HealthResource extends GenericResource<Data> {

    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody String getStatus();

    @RequestMapping(method = RequestMethod.POST, value = "/text",
        produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public String produceText(@RequestBody Map<String, Object> data);

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public void check(
                      @PathVariable("id") String campaignId,
                      @RequestParam(value = "deep", defaultValue = "false") boolean deepCheck);

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public void check(
                      @PathVariable("id") String campaignId,
                      @RequestParam(value = "deep", defaultValue = "false") boolean deepCheck,
                      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun);

    @GetMapping(value = "/{id}")
    public void check(@PathVariable("id") String campaignId);

    @ResponseStatus(value = HttpStatus.NOT_FOUND,
        reason = "This customer is not found in the system")
    @ExceptionHandler(MissingResourceException.class)
    void missingResourceExceptionHandler();

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    void checkWithName(
                       @PathVariable(name = "id") String campaignId,
                       @RequestParam(name = "deep", defaultValue = "false") boolean deepCheck,
                       @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun);

    @RequestMapping(value = "/optional", method = RequestMethod.GET)
    void checkWithOptional(@RequestParam(name = "param") Optional<String> param);

    @RequestMapping(value = "/part/{id}", method = RequestMethod.POST)
    void checkRequestPart(@PathVariable(name = "id") String campaignId,
                          @RequestPart(name = "name1") String name,
                          @RequestPart(name = "grade1") String grade);

    @RequestMapping(value = "/header", method = RequestMethod.GET)
    void checkRequestHeader(@RequestHeader(name = "name1") String name,
                            @RequestHeader(name = "grade1") String grade);

    @RequestMapping(value = "/header/map", method = RequestMethod.GET)
    void checkRequestHeaderMap(@RequestHeader Map<String, String> headerMap);

    @RequestMapping(value = "/header/pojo", method = RequestMethod.GET)
    void checkRequestHeaderPojo(@RequestHeader HeaderMapUserObject object);

  }

  class HeaderMapUserObject {
    @Param("name1")
    private String name;
    @Param("grade1")
    private String grade;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getGrade() {
      return grade;
    }

    public void setGrade(String grade) {
      this.grade = grade;
    }
  }
}
