/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.mock;

import static feign.Util.toByteArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import feign.Body;
import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.gson.GsonDecoder;

public class MockClientTest {

  interface GitHub {

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}/contributors?client_id={client_id}")
    List<Contributor> contributors(@Param("client_id") String clientId,
                                   @Param("owner") String owner,
                                   @Param("repo") String repo);

    @RequestLine("PATCH /repos/{owner}/{repo}/contributors")
    List<Contributor> patchContributors(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("POST /repos/{owner}/{repo}/contributors")
    @Body("%7B\"login\":\"{login}\",\"type\":\"{type}\"%7D")
    Contributor create(@Param("owner") String owner,
                       @Param("repo") String repo,
                       @Param("login") String login,
                       @Param("type") String type);

  }

  static class Contributor {

    String login;

    int contributions;

  }

  class AssertionDecoder implements Decoder {

    private final Decoder delegate;

    public AssertionDecoder(Decoder delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      assertThat(response.request(), notNullValue());

      return delegate.decode(response, type);
    }

  }

  private GitHub github;
  private MockClient mockClient;

  @Before
  public void setup() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/fixtures/contributors.json")) {
      byte[] data = toByteArray(input);
      mockClient = new MockClient();
      github = Feign.builder().decoder(new AssertionDecoder(new GsonDecoder()))
          .client(mockClient.ok(HttpMethod.GET, "/repos/netflix/feign/contributors", data)
              .ok(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=55")
              .ok(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=7 7",
                  new ByteArrayInputStream(data))
              .ok(HttpMethod.POST, "/repos/netflix/feign/contributors",
                  "{\"login\":\"velo\",\"contributions\":0}")
              .noContent(HttpMethod.PATCH, "/repos/velo/feign-mock/contributors")
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=1234567890",
                  HttpsURLConnection.HTTP_NOT_FOUND)
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=123456789",
                  HttpsURLConnection.HTTP_INTERNAL_ERROR, new ByteArrayInputStream(data))
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=123456789",
                  HttpsURLConnection.HTTP_INTERNAL_ERROR, "")
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=123456789",
                  HttpsURLConnection.HTTP_INTERNAL_ERROR, data))
          .target(new MockTarget<>(GitHub.class));
    }
  }

  @Test
  public void hitMock() {
    List<Contributor> contributors = github.contributors("netflix", "feign");
    assertThat(contributors, hasSize(30));
    mockClient.verifyStatus();
  }

  @Test
  public void missMock() {
    try {
      github.contributors("velo", "feign-mock");
      fail();
    } catch (FeignException e) {
      assertThat(e.getMessage(), Matchers.containsString("404"));
    }
  }

  @Test
  public void missHttpMethod() {
    try {
      github.patchContributors("netflix", "feign");
      fail();
    } catch (FeignException e) {
      assertThat(e.getMessage(), Matchers.containsString("404"));
    }
  }

  @Test
  public void paramsEncoding() {
    List<Contributor> contributors = github.contributors("7 7", "netflix", "feign");
    assertThat(contributors, hasSize(30));
    mockClient.verifyStatus();
  }

  @Test
  public void verifyInvocation() {
    Contributor contribution =
        github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    // making sure it received a proper response
    assertThat(contribution, notNullValue());
    assertThat(contribution.login, equalTo("velo"));
    assertThat(contribution.contributions, equalTo(0));

    List<Request> results =
        mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 1);
    assertThat(results, hasSize(1));

    byte[] body = mockClient.verifyOne(HttpMethod.POST, "/repos/netflix/feign/contributors").body();
    assertThat(body, notNullValue());

    String message = new String(body);
    assertThat(message, containsString("velo_at_github"));
    assertThat(message, containsString("preposterous hacker"));

    mockClient.verifyStatus();
  }

  @Test
  public void verifyNone() {
    github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 1);

    try {
      mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 0);
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), containsString("Do not wanted"));
      assertThat(e.getMessage(), containsString("POST"));
      assertThat(e.getMessage(), containsString("/repos/netflix/feign/contributors"));
    }

    try {
      mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 3);
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), containsString("Wanted"));
      assertThat(e.getMessage(), containsString("POST"));
      assertThat(e.getMessage(), containsString("/repos/netflix/feign/contributors"));
      assertThat(e.getMessage(), containsString("'3'"));
      assertThat(e.getMessage(), containsString("'1'"));
    }
  }

  @Test
  public void verifyNotInvoked() {
    mockClient.verifyNever(HttpMethod.POST, "/repos/netflix/feign/contributors");
    List<Request> results =
        mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 0);
    assertThat(results, hasSize(0));
    try {
      mockClient.verifyOne(HttpMethod.POST, "/repos/netflix/feign/contributors");
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), containsString("Wanted"));
      assertThat(e.getMessage(), containsString("POST"));
      assertThat(e.getMessage(), containsString("/repos/netflix/feign/contributors"));
      assertThat(e.getMessage(), containsString("never invoked"));
    }
  }

  @Test
  public void verifyNegative() {
    try {
      mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", -1);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("non negative"));
    }
  }

  @Test
  public void verifyMultipleRequests() {
    mockClient.verifyNever(HttpMethod.POST, "/repos/netflix/feign/contributors");

    github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    Request result = mockClient.verifyOne(HttpMethod.POST, "/repos/netflix/feign/contributors");
    assertThat(result, notNullValue());

    github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    List<Request> results =
        mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 2);
    assertThat(results, hasSize(2));

    github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    results = mockClient.verifyTimes(HttpMethod.POST, "/repos/netflix/feign/contributors", 3);
    assertThat(results, hasSize(3));

    mockClient.verifyStatus();
  }

  @Test
  public void resetRequests() {
    mockClient.verifyNever(HttpMethod.POST, "/repos/netflix/feign/contributors");

    github.create("netflix", "feign", "velo_at_github", "preposterous hacker");
    Request result = mockClient.verifyOne(HttpMethod.POST, "/repos/netflix/feign/contributors");
    assertThat(result, notNullValue());

    mockClient.resetRequests();

    mockClient.verifyNever(HttpMethod.POST, "/repos/netflix/feign/contributors");
  }

}
