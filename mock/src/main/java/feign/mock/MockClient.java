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

import static feign.Util.UTF_8;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import feign.Client;
import feign.Request;
import feign.Response;
import feign.Util;

public class MockClient implements Client {

  class RequestResponse {

    private final RequestKey requestKey;

    private final Response.Builder responseBuilder;

    public RequestResponse(RequestKey requestKey, Response.Builder responseBuilder) {
      this.requestKey = requestKey;
      this.responseBuilder = responseBuilder;
    }

  }

  private final List<RequestResponse> responses = new ArrayList<RequestResponse>();

  private final Map<RequestKey, List<Request>> requests = new HashMap<RequestKey, List<Request>>();

  private boolean sequential;

  private Iterator<RequestResponse> responseIterator;

  public MockClient() {}

  public MockClient(boolean sequential) {
    this.sequential = sequential;
  }

  @Override
  public synchronized Response execute(Request request, Request.Options options)
      throws IOException {
    RequestKey requestKey = RequestKey.create(request);
    Response.Builder responseBuilder;
    if (sequential) {
      responseBuilder = executeSequential(requestKey);
    } else {
      responseBuilder = executeAny(request, requestKey);
    }

    return responseBuilder.request(request).build();
  }

  private Response.Builder executeSequential(RequestKey requestKey) {
    Response.Builder responseBuilder;
    if (responseIterator == null) {
      responseIterator = responses.iterator();
    }
    if (!responseIterator.hasNext()) {
      throw new VerificationAssertionError("Received excessive request %s", requestKey);
    }

    RequestResponse expectedRequestResponse = responseIterator.next();
    if (!expectedRequestResponse.requestKey.equalsExtended(requestKey)) {
      throw new VerificationAssertionError("Expected: \n%s,\nbut was: \n%s",
          expectedRequestResponse.requestKey,
          requestKey);
    }

    responseBuilder = expectedRequestResponse.responseBuilder;
    return responseBuilder;
  }

  private Response.Builder executeAny(Request request, RequestKey requestKey) {
    Response.Builder responseBuilder;
    if (requests.containsKey(requestKey)) {
      requests.get(requestKey).add(request);
    } else {
      requests.put(requestKey, new ArrayList<Request>(Arrays.asList(request)));
    }

    responseBuilder = getResponseBuilder(request, requestKey);
    return responseBuilder;
  }

  private Response.Builder getResponseBuilder(Request request, RequestKey requestKey) {
    Response.Builder responseBuilder = null;
    for (RequestResponse requestResponse : responses) {
      if (requestResponse.requestKey.equalsExtended(requestKey)) {
        responseBuilder = requestResponse.responseBuilder;
        // Don't break here, last one should win to be compatible with
        // previous
        // releases of this library!
      }
    }
    if (responseBuilder == null) {
      responseBuilder =
          Response.builder().status(HttpURLConnection.HTTP_NOT_FOUND).reason("Not mocker")
              .headers(request.headers());
    }
    return responseBuilder;
  }

  public MockClient ok(HttpMethod method, String url, InputStream responseBody) throws IOException {
    return ok(RequestKey.builder(method, url).build(), responseBody);
  }

  public MockClient ok(HttpMethod method, String url, String responseBody) {
    return ok(RequestKey.builder(method, url).build(), responseBody);
  }

  public MockClient ok(HttpMethod method, String url, byte[] responseBody) {
    return ok(RequestKey.builder(method, url).build(), responseBody);
  }

  public MockClient ok(HttpMethod method, String url) {
    return ok(RequestKey.builder(method, url).build());
  }

  public MockClient ok(RequestKey requestKey, InputStream responseBody) throws IOException {
    return ok(requestKey, Util.toByteArray(responseBody));
  }

  public MockClient ok(RequestKey requestKey, String responseBody) {
    return ok(requestKey, responseBody.getBytes(UTF_8));
  }

  public MockClient ok(RequestKey requestKey, byte[] responseBody) {
    return add(requestKey, HttpURLConnection.HTTP_OK, responseBody);
  }

  public MockClient ok(RequestKey requestKey) {
    return ok(requestKey, (byte[]) null);
  }

  public MockClient add(HttpMethod method, String url, int status, InputStream responseBody)
      throws IOException {
    return add(RequestKey.builder(method, url).build(), status, responseBody);
  }

  public MockClient add(HttpMethod method, String url, int status, String responseBody) {
    return add(RequestKey.builder(method, url).build(), status, responseBody);
  }

  public MockClient add(HttpMethod method, String url, int status, byte[] responseBody) {
    return add(RequestKey.builder(method, url).build(), status, responseBody);
  }

  public MockClient add(HttpMethod method, String url, int status) {
    return add(RequestKey.builder(method, url).build(), status);
  }

  /**
   * @param response
   *        <ul>
   *        <li>the status defaults to 0, not 200!</li>
   *        <li>the internal feign-code requires the headers to be set</li>
   *        </ul>
   */
  public MockClient add(HttpMethod method, String url, Response.Builder response) {
    return add(RequestKey.builder(method, url).build(), response);
  }

  public MockClient add(RequestKey requestKey, int status, InputStream responseBody)
      throws IOException {
    return add(requestKey, status, Util.toByteArray(responseBody));
  }

  public MockClient add(RequestKey requestKey, int status, String responseBody) {
    return add(requestKey, status, responseBody.getBytes(UTF_8));
  }

  public MockClient add(RequestKey requestKey, int status, byte[] responseBody) {
    return add(requestKey,
        Response.builder().status(status).reason("Mocked").headers(RequestHeaders.EMPTY)
            .body(responseBody));
  }

  public MockClient add(RequestKey requestKey, int status) {
    return add(requestKey, status, (byte[]) null);
  }

  public MockClient add(RequestKey requestKey, Response.Builder response) {
    responses.add(new RequestResponse(requestKey, response));
    return this;
  }

  public MockClient add(HttpMethod method, String url, Response response) {
    return this.add(method, url, response.toBuilder());
  }

  public MockClient noContent(HttpMethod method, String url) {
    return add(method, url, HttpURLConnection.HTTP_NO_CONTENT);
  }

  public Request verifyOne(HttpMethod method, String url) {
    return verifyTimes(method, url, 1).get(0);
  }

  public List<Request> verifyTimes(final HttpMethod method, final String url, final int times) {
    if (times < 0) {
      throw new IllegalArgumentException("times must be a non negative number");
    }

    if (times == 0) {
      verifyNever(method, url);
      return Collections.emptyList();
    }

    RequestKey requestKey = RequestKey.builder(method, url).build();
    if (!requests.containsKey(requestKey)) {
      throw new VerificationAssertionError("Wanted: '%s' but never invoked!", requestKey);
    }

    List<Request> result = requests.get(requestKey);
    if (result.size() != times) {
      throw new VerificationAssertionError("Wanted: '%s' to be invoked: '%s' times but got: '%s'!",
          requestKey,
          times, result.size());
    }

    return result;
  }

  public void verifyNever(HttpMethod method, String url) {
    RequestKey requestKey = RequestKey.builder(method, url).build();
    if (requests.containsKey(requestKey)) {
      throw new VerificationAssertionError("Do not wanted: '%s' but was invoked!", requestKey);
    }
  }

  /**
   * To be called in an &#64;After method:
   *
   * <pre>
   * &#64;After
   * public void tearDown() {
   *   mockClient.verifyStatus();
   * }
   * </pre>
   */
  public void verifyStatus() {
    if (sequential) {
      boolean unopenedIterator = responseIterator == null && !responses.isEmpty();
      if (unopenedIterator || responseIterator.hasNext()) {
        throw new VerificationAssertionError("More executions were expected");
      }
    }
  }

  public void resetRequests() {
    requests.clear();
  }


}
