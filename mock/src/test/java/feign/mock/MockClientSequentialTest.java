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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.fail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Before;
import org.junit.Test;
import feign.Body;
import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.gson.GsonDecoder;

public class MockClientSequentialTest {

  interface GitHub {

    @Headers({"Name: {owner}"})
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

  private GitHub githubSequential;
  private MockClient mockClientSequential;

  @Before
  public void setup() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/fixtures/contributors.json")) {
      byte[] data = toByteArray(input);
      RequestHeaders headers = RequestHeaders
          .builder()
          .add("Name", "netflix")
          .build();
      mockClientSequential = new MockClient(true);
      githubSequential = Feign.builder().decoder(new AssertionDecoder(new GsonDecoder()))
          .client(mockClientSequential
              .add(RequestKey
                  .builder(HttpMethod.GET, "/repos/netflix/feign/contributors")
                  .headers(headers).build(), HttpsURLConnection.HTTP_OK, data)
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=55",
                  HttpsURLConnection.HTTP_NOT_FOUND)
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors?client_id=7 7",
                  HttpsURLConnection.HTTP_INTERNAL_ERROR, new ByteArrayInputStream(data))
              .add(HttpMethod.GET, "/repos/netflix/feign/contributors",
                  Response.builder().status(HttpsURLConnection.HTTP_OK)
                      .headers(RequestHeaders.EMPTY).body(data)))
          .target(new MockTarget<>(GitHub.class));
    }
  }

  @Test
  public void sequentialRequests() throws Exception {
    githubSequential.contributors("netflix", "feign");
    try {
      githubSequential.contributors("55", "netflix", "feign");
      fail();
    } catch (FeignException e) {
      assertThat(e.status(), equalTo(HttpsURLConnection.HTTP_NOT_FOUND));
    }
    try {
      githubSequential.contributors("7 7", "netflix", "feign");
      fail();
    } catch (FeignException e) {
      assertThat(e.status(), equalTo(HttpsURLConnection.HTTP_INTERNAL_ERROR));
    }
    githubSequential.contributors("netflix", "feign");

    mockClientSequential.verifyStatus();
  }

  @Test
  public void sequentialRequestsCalledTooLess() throws Exception {
    githubSequential.contributors("netflix", "feign");
    try {
      mockClientSequential.verifyStatus();
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), startsWith("More executions"));
    }
  }

  @Test
  public void sequentialRequestsCalledTooMany() throws Exception {
    sequentialRequests();

    try {
      githubSequential.contributors("netflix", "feign");
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), containsString("excessive"));
    }
  }

  @Test
  public void sequentialRequestsInWrongOrder() throws Exception {
    try {
      githubSequential.contributors("7 7", "netflix", "feign");
      fail();
    } catch (VerificationAssertionError e) {
      assertThat(e.getMessage(), startsWith("Expected: \nRequest ["));
    }
  }

}
