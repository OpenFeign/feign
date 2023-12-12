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

import static feign.Util.UTF_8;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import feign.Feign.ResponseMappingDecoder;
import feign.Request.HttpMethod;
import feign.Target.HardCodedTarget;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import feign.querymap.BeanQueryMapEncoder;
import feign.querymap.FieldQueryMapEncoder;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.SocketPolicy;
import okio.Buffer;

@SuppressWarnings("deprecation")
public class FeignUnderAsyncTest {
  public final MockWebServer server = new MockWebServer();

  @Test
  void iterableQueryParams() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.queryParams("user", Arrays.asList("apple", "pear"));

    assertThat(server.takeRequest()).hasPath("/?1=user&2=apple&2=pear");
  }

  @Test
  void postTemplateParamsResolve() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.login("netflix", "denominator", "password");

    assertThat(server.takeRequest()).hasBody(
        "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
  }

  @Test
  void postFormParams() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.form("netflix", "denominator", "password");

    assertThat(server.takeRequest())
        .hasBody(
            "{\"customer_name\":\"netflix\",\"user_name\":\"denominator\",\"password\":\"password\"}");
  }

  @Test
  void postBodyParam() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.body(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest())
        .hasHeaders(entry("Content-Length", Collections.singletonList("32")))
        .hasBody("[netflix, denominator, password]");
  }

  /**
   * The type of a parameter value may not be the desired type to encode as. Prefer the interface
   * type.
   */
  @Test
  void bodyTypeCorrespondsWithParameterType() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final AtomicReference<Type> encodedType = new AtomicReference<>();
    TestInterface api = new TestInterfaceBuilder().encoder(new Encoder.Default() {
      @Override
      public void encode(Object object, Type bodyType, RequestTemplate template) {
        encodedType.set(bodyType);
      }
    }).target("http://localhost:" + server.getPort());

    api.body(Arrays.asList("netflix", "denominator", "password"));

    server.takeRequest();

    assertThat(encodedType.get()).isEqualTo(new TypeToken<List<String>>() {}.getType());
  }

  @Test
  void postGZIPEncodedBodyParam() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.gzipBody(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest()).hasNoHeaderNamed("Content-Length")
        .hasGzippedBody("[netflix, denominator, password]".getBytes(UTF_8));
  }

  @Test
  void postDeflateEncodedBodyParam() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.deflateBody(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest()).hasNoHeaderNamed("Content-Length")
        .hasDeflatedBody("[netflix, denominator, password]".getBytes(UTF_8));
  }

  @Test
  void singleInterceptor() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().requestInterceptor(new ForwardedForInterceptor())
        .target("http://localhost:" + server.getPort());

    api.post();

    assertThat(server.takeRequest())
        .hasHeaders(entry("X-Forwarded-For", Collections.singletonList("origin.host.com")));
  }

  @Test
  void multipleInterceptor() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().requestInterceptor(new ForwardedForInterceptor())
        .requestInterceptor(new UserAgentInterceptor())
        .target("http://localhost:" + server.getPort());

    api.post();

    assertThat(server.takeRequest()).hasHeaders(
        entry("X-Forwarded-For", Collections.singletonList("origin.host.com")),
        entry("User-Agent", Collections.singletonList("Feign")));
  }

  @Test
  void customExpander() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.expand(new TestClock(1234L));

    assertThat(server.takeRequest()).hasPath("/?clock=1234");
  }

  @Test
  void customExpanderListParam() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.expandList(Arrays.asList(new TestClock(1234L), new TestClock(12345L)));

    assertThat(server.takeRequest()).hasPath("/?clock=1234&clock=12345");
  }

  @Test
  void customExpanderNullParam() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.expandList(Arrays.asList(new TestClock(1234l), null));

    assertThat(server.takeRequest()).hasPath("/?clock=1234");
  }

  @Test
  void headerMap() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    Map<String, Object> headerMap = new LinkedHashMap<>();
    headerMap.put("Content-Type", "myContent");
    headerMap.put("Custom-Header", "fooValue");
    api.headerMap(headerMap);

    assertThat(server.takeRequest()).hasHeaders(entry("Content-Type", Arrays.asList("myContent")),
        entry("Custom-Header", Arrays.asList("fooValue")));
  }

  @Test
  void headerMapWithHeaderAnnotations() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    Map<String, Object> headerMap = new LinkedHashMap<>();
    headerMap.put("Custom-Header", "fooValue");
    api.headerMapWithHeaderAnnotations(headerMap);

    // header map should be additive for headers provided by annotations
    assertThat(server.takeRequest()).hasHeaders(
        entry("Content-Encoding", Collections.singletonList("deflate")),
        entry("Custom-Header", Collections.singletonList("fooValue")));

    server.enqueue(new MockResponse());
    headerMap.put("Content-Encoding", "overrideFromMap");

    api.headerMapWithHeaderAnnotations(headerMap);

    /*
     * @HeaderMap map values no longer override @Header parameters. This caused confusion as it is
     * valid to have more than one value for a header.
     */
    assertThat(server.takeRequest()).hasHeaders(
        entry("Content-Encoding", Arrays.asList("deflate", "overrideFromMap")),
        entry("Custom-Header", Collections.singletonList("fooValue")));
  }

  @Test
  void queryMap() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    Map<String, Object> queryMap = new LinkedHashMap<>();
    queryMap.put("name", "alice");
    queryMap.put("fooKey", "fooValue");
    api.queryMap(queryMap);

    assertThat(server.takeRequest()).hasPath("/?name=alice&fooKey=fooValue");
  }

  @Test
  void queryMapIterableValuesExpanded() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    Map<String, Object> queryMap = new LinkedHashMap<>();
    queryMap.put("name", Arrays.asList("Alice", "Bob"));
    queryMap.put("fooKey", "fooValue");
    queryMap.put("emptyListKey", new ArrayList<>());
    queryMap.put("emptyStringKey", ""); // empty values are ignored.
    api.queryMap(queryMap);

    assertThat(server.takeRequest())
        .hasPath("/?name=Alice&name=Bob&fooKey=fooValue&emptyStringKey");
  }

  @Test
  void queryMapWithQueryParams() throws Exception {
    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    server.enqueue(new MockResponse());
    Map<String, Object> queryMap = new LinkedHashMap<>();
    queryMap.put("fooKey", "fooValue");
    api.queryMapWithQueryParams("alice", queryMap);
    // query map should be expanded after built-in parameters
    assertThat(server.takeRequest()).hasPath("/?name=alice&fooKey=fooValue");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<>();
    queryMap.put("name", "bob");
    api.queryMapWithQueryParams("alice", queryMap);
    // queries are additive
    assertThat(server.takeRequest()).hasPath("/?name=alice&name=bob");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<>();
    queryMap.put("name", null);
    api.queryMapWithQueryParams("alice", queryMap);
    // null value for a query map key removes query parameter
    assertThat(server.takeRequest()).hasPath("/?name=alice");
  }

  @Test
  void queryMapValueStartingWithBrace() throws Exception {
    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    server.enqueue(new MockResponse());
    Map<String, Object> queryMap = new LinkedHashMap<>();
    queryMap.put("name", "{alice");
    api.queryMap(queryMap);
    assertThat(server.takeRequest()).hasPath("/?name=%7Balice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<>();
    queryMap.put("{name", "alice");
    api.queryMap(queryMap);
    assertThat(server.takeRequest()).hasPath("/?%7Bname=alice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<>();
    queryMap.put("name", "%7Balice");
    api.queryMapEncoded(queryMap);
    assertThat(server.takeRequest()).hasPath("/?name=%7Balice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<>();
    queryMap.put("%7Bname", "%7Balice");
    api.queryMapEncoded(queryMap);
    assertThat(server.takeRequest()).hasPath("/?%7Bname=%7Balice");
  }

  @Test
  void queryMapPojoWithFullParams() throws Exception {
    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    CustomPojo customPojo = new CustomPojo("Name", 3);

    server.enqueue(new MockResponse());
    api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("name=Name", "number=3"));
  }

  @Test
  void queryMapPojoWithPartialParams() throws Exception {
    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    CustomPojo customPojo = new CustomPojo("Name", null);

    server.enqueue(new MockResponse());
    api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasPath("/?name=Name");
  }

  @Test
  void queryMapPojoWithEmptyParams() throws Exception {
    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    CustomPojo customPojo = new CustomPojo(null, null);

    server.enqueue(new MockResponse());
    api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  void configKeyFormatsAsExpected() throws Exception {
    assertThat(Feign.configKey(TestInterface.class, TestInterface.class.getDeclaredMethod("post")))
        .isEqualTo("TestInterface#post()");
    assertThat(Feign.configKey(TestInterface.class,
        TestInterface.class.getDeclaredMethod("uriParam", String.class, URI.class, String.class)))
            .isEqualTo("TestInterface#uriParam(String,URI,String)");
  }

  @Test
  void configKeyUsesChildType() throws Exception {
    assertThat(Feign.configKey(List.class, Iterable.class.getDeclaredMethod("iterator")))
        .isEqualTo("List#iterator()");
  }

  @Test
  void canOverrideErrorDecoder() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("foo"));

    TestInterface api = new TestInterfaceBuilder().errorDecoder(new IllegalArgumentExceptionOn400())
        .target("http://localhost:" + server.getPort());

    Throwable exception = assertThrows(IllegalArgumentException.class, () -> api.post());
    assertThat(exception.getMessage()).contains("bad zone name");
  }

  @Test
  void retriesLostConnectionBeforeRead() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.post();

    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void overrideTypeSpecificDecoder() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = new TestInterfaceBuilder().decoder((response, type) -> "fail")
        .target("http://localhost:" + server.getPort());

    assertThat("fail").isEqualTo(api.post());
  }

  @Test
  void doesntRetryAfterResponseIsSent() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = new TestInterfaceBuilder()
        .decoder((response, type) -> {
          throw new IOException("timeout");
        })
        .target("http://localhost:" + server.getPort());

    Throwable exception = assertThrows(FeignException.class, () -> api.post());
    assertThat(exception.getMessage()).contains("timeout reading POST http://");
  }

  @Test
  void throwsFeignExceptionIncludingBody() {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = AsyncFeign.builder().decoder((response, type) -> {
      throw new IOException("timeout");
    })
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.body("Request body");
    } catch (FeignException e) {
      assertThat(e.getMessage())
          .isEqualTo("timeout reading POST http://localhost:" + server.getPort() + "/");
      assertThat(e.contentUTF8()).isEqualTo("Request body");
    }
  }

  @Test
  void throwsFeignExceptionWithoutBody() {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = AsyncFeign.builder().decoder((response, type) -> {
      throw new IOException("timeout");
    })
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.noContent();
    } catch (FeignException e) {
      assertThat(e.getMessage())
          .isEqualTo("timeout reading POST http://localhost:" + server.getPort() + "/");
      assertThat(e.contentUTF8()).isEqualTo("");
    }
  }

  @Test
  void whenReturnTypeIsResponseNoErrorHandling() {
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("Location", Collections.singletonList("http://bar.com"));
    final Response response = Response.builder().status(302).reason("Found").headers(headers)
        .request(Request.create(HttpMethod.GET, "/", Collections.emptyMap(), null, Util.UTF_8))
        .body(new byte[0]).build();

    ExecutorService execs = Executors.newSingleThreadExecutor();

    // fake client as Client.Default follows redirects.
    TestInterface api = AsyncFeign.<Void>builder()
        .client(new AsyncClient.Default<>((request, options) -> response, execs))
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.response().headers()).hasEntrySatisfying("Location", value -> {
      assertThat(value).contains("http://bar.com");
    });

    execs.shutdown();
  }

  private static class MockRetryer implements Retryer {
    boolean tripped;

    @Override
    public void continueOrPropagate(RetryableException e) {
      if (tripped) {
        throw new RuntimeException("retryer instance should never be reused");
      }
      tripped = true;
      return;
    }

    @Override
    public Retryer clone() {
      return new MockRetryer();
    }
  }

  @Test
  void okIfDecodeRootCauseHasNoMessage() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = new TestInterfaceBuilder().decoder((response, type) -> {
      throw new RuntimeException();
    })
        .target("http://localhost:" + server.getPort());

    assertThrows(DecodeException.class, () -> api.post());
  }

  @Test
  void decodingExceptionGetWrappedInDismiss404Mode() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));

    TestInterface api = new TestInterfaceBuilder().dismiss404().decoder((response, type) -> {
      assertEquals(404, response.status());
      throw new NoSuchElementException();
    }).target("http://localhost:" + server.getPort());
    DecodeException exception = assertThrows(DecodeException.class, () -> api.post());
    assertThat(exception).hasCauseInstanceOf(NoSuchElementException.class);
  }

  @Test
  void decodingDoesNotSwallow404ErrorsInDismiss404Mode() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      server.enqueue(new MockResponse().setResponseCode(404));

      TestInterface api = new TestInterfaceBuilder().dismiss404()
          .errorDecoder(new IllegalArgumentExceptionOn404())
          .target("http://localhost:" + server.getPort());
      api.queryMap(Collections.<String, Object>emptyMap());
    });
  }

  @Test
  void okIfEncodeRootCauseHasNoMessage() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = new TestInterfaceBuilder()
        .encoder((object, bodyType, template) -> {
          throw new RuntimeException();
        })
        .target("http://localhost:" + server.getPort());

    assertThrows(EncodeException.class, () -> api.body(Arrays.asList("foo")));
  }

  @Test
  void equalsHashCodeAndToStringWork() {
    Target<TestInterface> t1 = new HardCodedTarget<>(TestInterface.class, "http://localhost:8080");
    Target<TestInterface> t2 = new HardCodedTarget<>(TestInterface.class, "http://localhost:8888");
    Target<OtherTestInterface> t3 =
        new HardCodedTarget<>(OtherTestInterface.class, "http://localhost:8080");
    TestInterface i1 = AsyncFeign.builder().target(t1);
    TestInterface i2 = AsyncFeign.builder().target(t1);
    TestInterface i3 = AsyncFeign.builder().target(t2);
    OtherTestInterface i4 = AsyncFeign.builder().target(t3);

    assertThat(i1).isEqualTo(i2).isNotEqualTo(i3).isNotEqualTo(i4);

    assertThat(i1.hashCode()).isEqualTo(i2.hashCode()).isNotEqualTo(i3.hashCode())
        .isNotEqualTo(i4.hashCode());

    assertThat(i1.toString()).isEqualTo(i2.toString()).isNotEqualTo(i3.toString())
        .isNotEqualTo(i4.toString());

    assertThat(t1).isNotEqualTo(i1);

    assertThat(t1.hashCode()).isEqualTo(i1.hashCode());

    assertThat(t1.toString()).isEqualTo(i1.toString());
  }

  @Test
  void decodeLogicSupportsByteArray() throws Exception {
    byte[] expectedResponse = {12, 34, 56};
    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse)));

    OtherTestInterface api = AsyncFeign.builder().target(OtherTestInterface.class,
        "http://localhost:" + server.getPort());

    assertThat(api.binaryResponseBody()).containsExactly(expectedResponse);
  }

  @Test
  void encodeLogicSupportsByteArray() throws Exception {
    byte[] expectedRequest = {12, 34, 56};
    server.enqueue(new MockResponse());

    OtherTestInterface api = AsyncFeign.builder().target(OtherTestInterface.class,
        "http://localhost:" + server.getPort());

    api.binaryRequestBody(expectedRequest);

    assertThat(server.takeRequest()).hasBody(expectedRequest);
  }

  @Test
  void encodedQueryParam() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api = new TestInterfaceBuilder().target("http://localhost:" + server.getPort());

    api.encodedQueryParam("5.2FSi+");

    assertThat(server.takeRequest()).hasPath("/?trim=5.2FSi%2B");
  }

  @Test
  void responseMapperIsAppliedBeforeDelegate() throws IOException {
    ResponseMappingDecoder decoder =
        new ResponseMappingDecoder(upperCaseResponseMapper(), new StringDecoder());
    String output = (String) decoder.decode(responseWithText("response"), String.class);

    assertThat(output).isEqualTo("RESPONSE");
  }

  private ResponseMapper upperCaseResponseMapper() {
    return (response, type) -> {
      try {
        return response.toBuilder()
            .body(Util.toString(response.body().asReader(UTF_8)).toUpperCase().getBytes()).build();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private Response responseWithText(String text) {
    return Response.builder().body(text, Util.UTF_8).status(200)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(new HashMap<>()).build();
  }

  @Test
  void mapAndDecodeExecutesMapFunction() throws Exception {
    server.enqueue(new MockResponse().setBody("response!"));

    TestInterface api =
        AsyncFeign.builder().mapAndDecode(upperCaseResponseMapper(), new StringDecoder())
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat("RESPONSE!").isEqualTo(api.post());
  }

  @Test
  void beanQueryMapEncoderWithPrivateGetterIgnored() throws Exception {
    TestInterface api = new TestInterfaceBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
        .target("http://localhost:" + server.getPort());

    PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();
    propertyPojo.setPrivateGetterProperty("privateGetterProperty");
    propertyPojo.setName("Name");
    propertyPojo.setNumber(1);

    server.enqueue(new MockResponse());
    api.queryMapPropertyPojo(propertyPojo);
    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("name=Name", "number=1"));
  }

  @Test
  void queryMap_with_child_pojo() throws Exception {
    TestInterface api = new TestInterfaceBuilder().queryMapEndcoder(new FieldQueryMapEncoder())
        .target("http://localhost:" + server.getPort());

    ChildPojo childPojo = new ChildPojo();
    childPojo.setChildPrivateProperty("first");
    childPojo.setParentProtectedProperty("second");
    childPojo.setParentPublicProperty("third");

    server.enqueue(new MockResponse());
    api.queryMapPropertyInheritence(childPojo);
    assertThat(server.takeRequest()).hasQueryParams("parentPublicProperty=third",
        "parentProtectedProperty=second",
        "childPrivateProperty=first");
  }

  @Test
  void beanQueryMapEncoderWithNullValueIgnored() throws Exception {
    TestInterface api = new TestInterfaceBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
        .target("http://localhost:" + server.getPort());

    PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();
    propertyPojo.setName(null);
    propertyPojo.setNumber(1);

    server.enqueue(new MockResponse());
    api.queryMapPropertyPojo(propertyPojo);
    assertThat(server.takeRequest()).hasQueryParams("number=1");
  }

  @Test
  void beanQueryMapEncoderWithEmptyParams() throws Exception {
    TestInterface api = new TestInterfaceBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
        .target("http://localhost:" + server.getPort());

    PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();

    server.enqueue(new MockResponse());
    api.queryMapPropertyPojo(propertyPojo);
    assertThat(server.takeRequest()).hasQueryParams("/");
  }

  interface TestInterface {

    @RequestLine("POST /")
    Response response();

    @RequestLine("POST /")
    String post() throws TestInterfaceException;

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(@Param("customer_name") String customer,
               @Param("user_name") String user,
               @Param("password") String password);

    @RequestLine("POST /")
    void body(List<String> contents);

    @RequestLine("POST /")
    String body(String content);

    @RequestLine("POST /")
    String noContent();

    @RequestLine("POST /")
    @Headers("Content-Encoding: gzip")
    void gzipBody(List<String> contents);

    @RequestLine("POST /")
    @Headers("Content-Encoding: deflate")
    void deflateBody(List<String> contents);

    @RequestLine("POST /")
    void form(@Param("customer_name") String customer,
              @Param("user_name") String user,
              @Param("password") String password);

    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Param("1") String one, URI endpoint, @Param("2") String two);

    @RequestLine("GET /?1={1}&2={2}")
    Response queryParams(@Param("1") String one, @Param("2") Iterable<String> twos);

    @RequestLine("POST /?clock={clock}")
    void expand(@Param(value = "clock", expander = ClockToMillis.class) Clock clock);

    @RequestLine("GET /?clock={clock}")
    void expandList(@Param(value = "clock", expander = ClockToMillis.class) List<Clock> clocks);

    @RequestLine("GET /?clock={clock}")
    void expandArray(@Param(value = "clock", expander = ClockToMillis.class) Clock[] clocks);

    @RequestLine("GET /")
    void headerMap(@HeaderMap Map<String, Object> headerMap);

    @RequestLine("GET /")
    @Headers("Content-Encoding: deflate")
    void headerMapWithHeaderAnnotations(@HeaderMap Map<String, Object> headerMap);

    @RequestLine("GET /")
    void queryMap(@QueryMap Map<String, Object> queryMap);

    @RequestLine("GET /")
    void queryMapEncoded(@QueryMap(encoded = true) Map<String, Object> queryMap);

    @RequestLine("GET /?name={name}")
    void queryMapWithQueryParams(@Param("name") String name,
                                 @QueryMap Map<String, Object> queryMap);

    @RequestLine("GET /?trim={trim}")
    void encodedQueryParam(@Param(value = "trim") String trim);

    @RequestLine("GET /")
    void queryMapPojo(@QueryMap CustomPojo object);

    @RequestLine("GET /")
    void queryMapPropertyPojo(@QueryMap PropertyPojo object);

    @RequestLine("GET /")
    void queryMapPropertyInheritence(@QueryMap ChildPojo object);

    class ClockToMillis implements Param.Expander {

      @Override
      public String expand(Object value) {
        return String.valueOf(((Clock) value).millis());
      }
    }
  }

  class TestInterfaceException extends Exception {
    TestInterfaceException(String message) {
      super(message);
    }
  }

  interface OtherTestInterface {

    @RequestLine("POST /")
    String post();

    @RequestLine("POST /")
    byte[] binaryResponseBody();

    @RequestLine("POST /")
    void binaryRequestBody(byte[] contents);
  }

  static class ForwardedForInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
      template.header("X-Forwarded-For", "origin.host.com");
    }
  }

  static class UserAgentInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
      template.header("User-Agent", "Feign");
    }
  }

  static class IllegalArgumentExceptionOn400 extends ErrorDecoder.Default {

    @Override
    public Exception decode(String methodKey, Response response) {
      if (response.status() == 400) {
        return new IllegalArgumentException("bad zone name");
      }
      return super.decode(methodKey, response);
    }
  }

  static class IllegalArgumentExceptionOn404 extends ErrorDecoder.Default {

    @Override
    public Exception decode(String methodKey, Response response) {
      if (response.status() == 404) {
        return new IllegalArgumentException("bad zone name");
      }
      return super.decode(methodKey, response);
    }
  }

  static final class TestInterfaceBuilder {

    private final AsyncFeign.AsyncBuilder<Void> delegate =
        AsyncFeign.<Void>builder().decoder(new Decoder.Default())
            .encoder((object, bodyType, template) -> {
              if (object instanceof Map) {
                template.body(new Gson().toJson(object));
              } else {
                template.body(object.toString());
              }
            });

    TestInterfaceBuilder requestInterceptor(RequestInterceptor requestInterceptor) {
      delegate.requestInterceptor(requestInterceptor);
      return this;
    }

    TestInterfaceBuilder encoder(Encoder encoder) {
      delegate.encoder(encoder);
      return this;
    }

    TestInterfaceBuilder decoder(Decoder decoder) {
      delegate.decoder(decoder);
      return this;
    }

    TestInterfaceBuilder errorDecoder(ErrorDecoder errorDecoder) {
      delegate.errorDecoder(errorDecoder);
      return this;
    }

    TestInterfaceBuilder dismiss404() {
      delegate.dismiss404();
      return this;
    }

    TestInterfaceBuilder queryMapEndcoder(QueryMapEncoder queryMapEncoder) {
      delegate.queryMapEncoder(queryMapEncoder);
      return this;
    }

    TestInterface target(String url) {
      return delegate.target(TestInterface.class, url);
    }
  }

  class TestClock extends Clock {

    private long millis;

    public TestClock(long millis) {
      this.millis = millis;
    }

    @Override
    public ZoneId getZone() {
      throw new UnsupportedOperationException("This operation is not supported.");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }

}
