/*
 * Copyright 2012-2022 The Feign Authors
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

import static org.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import feign.Param;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;

public class SpringContractTest {


  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockClient mockClient;
  private HealthResource resource;

  @Before
  public void setup() throws IOException {
    mockClient = new MockClient()
        .noContent(HttpMethod.GET, "/health")
        .noContent(HttpMethod.GET, "/health/1")
        .noContent(HttpMethod.GET, "/health/1?deep=true")
        .noContent(HttpMethod.GET, "/health/1?deep=true&dryRun=true")
        .noContent(HttpMethod.GET, "/health/name?deep=true&dryRun=true")
        .noContent(HttpMethod.POST, "/health/part/1")
        .noContent(HttpMethod.GET, "/health/header")
        .noContent(HttpMethod.GET, "/health/header/map")
        .noContent(HttpMethod.GET, "/health/header/pojo")
        .ok(HttpMethod.GET, "/health/generic", "{}");
    resource = Feign.builder()
        .contract(new SpringContract())
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .client(mockClient)
        .target(new MockTarget<>(HealthResource.class));
  }

  @Test
  public void noPath() {
    resource.getStatus();

    mockClient.verifyOne(HttpMethod.GET, "/health");
  }

  @Test
  public void testWithName() {
    resource.checkWithName("name", true, true);

    mockClient.verifyOne(HttpMethod.GET, "/health/name?deep=true&dryRun=true");
  }

  @Test
  public void testRequestPart() {
    resource.checkRequestPart("1", "hello", "6");

    final Request request = mockClient.verifyOne(HttpMethod.POST, "/health/part/1");
    assertThat(request.requestTemplate().methodMetadata().formParams(),
        contains("name1", "grade1"));
  }

  @Test
  public void testRequestHeader() {
    resource.checkRequestHeader("hello", "6");

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header");
    assertThat(request.headers(),
        hasEntry("name1", Arrays.asList("hello")));
    assertThat(request.headers(),
        hasEntry("grade1", Arrays.asList("6")));
  }

  @Test
  public void testRequestHeaderMap() {
    Map<String, String> map = new HashMap<>();
    map.put("name1", "hello");
    map.put("grade1", "6");
    resource.checkRequestHeaderMap(map);

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header/map");
    assertThat(request.headers(),
        hasEntry("name1", Arrays.asList("hello")));
    assertThat(request.headers(),
        hasEntry("grade1", Arrays.asList("6")));
  }

  @Test
  public void testRequestHeaderPojo() {
    HeaderMapUserObject object = new HeaderMapUserObject();
    object.setName("hello");
    object.setGrade("6");
    resource.checkRequestHeaderPojo(object);

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/header/pojo");
    assertThat(request.headers(),
        hasEntry("name1", Arrays.asList("hello")));
    assertThat(request.headers(),
        hasEntry("grade1", Arrays.asList("6")));
  }

  @Test
  public void requestParam() {
    resource.check("1", true);

    mockClient.verifyOne(HttpMethod.GET, "/health/1?deep=true");
  }

  @Test
  public void requestTwoParams() {
    resource.check("1", true, true);

    mockClient.verifyOne(HttpMethod.GET, "/health/1?deep=true&dryRun=true");
  }

  @Test
  public void inheritance() {
    final Data data = resource.getData(new Data());
    assertThat(data, notNullValue());

    final Request request = mockClient.verifyOne(HttpMethod.GET, "/health/generic");
    assertThat(request.headers(), hasEntry(
        "Content-Type",
        Arrays.asList("application/json")));
  }

  @Test
  public void composedAnnotation() {
    resource.check("1");

    mockClient.verifyOne(HttpMethod.GET, "/health/1");
  }

  @Test
  public void notAHttpMethod() {
    thrown.expectMessage("is not a method handled by feign");

    resource.missingResourceExceptionHandler();
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
