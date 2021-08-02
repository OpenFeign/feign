/**
 * Copyright 2012-2021 The Feign Authors
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static feign.Util.*;
import static java.lang.String.format;

/**
 * Origin exception type for all Http Apis.
 */
public class FeignException extends RuntimeException {

  private static final String EXCEPTION_MESSAGE_TEMPLATE_NULL_REQUEST =
      "request should not be null";
  private static final String EXCEPTION_MESSAGE_TEMPLATE_NULL_RESPONSE =
      "response should not be null";
  private static final long serialVersionUID = 0;
  private final int status;
  @Deprecated
  private byte[] responseBody;
  @Deprecated
  private Map<String, Collection<String>> responseHeaders;
  private Request request;
  private Response response;

  protected FeignException(int status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.request = null;
  }

  @Deprecated
  protected FeignException(int status, String message, Throwable cause, byte[] responseBody,
      Map<String, Collection<String>> responseHeaders) {
    super(message, cause);
    this.status = status;
    this.responseBody = responseBody;
    this.responseHeaders = caseInsensitiveCopyOf(responseHeaders);
    this.request = null;
  }

  protected FeignException(int status, String message) {
    super(message);
    this.status = status;
    this.request = null;
  }

  @Deprecated
  protected FeignException(int status, String message, byte[] responseBody,
      Map<String, Collection<String>> responseHeaders) {
    super(message);
    this.status = status;
    this.responseBody = responseBody;
    this.responseHeaders = caseInsensitiveCopyOf(responseHeaders);
    this.request = null;
  }

  protected FeignException(int status, String message, Request request, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.request = checkRequestNotNull(request);
  }

  @Deprecated
  protected FeignException(int status, String message, Request request, Throwable cause,
      byte[] responseBody, Map<String, Collection<String>> responseHeaders) {
    super(message, cause);
    this.status = status;
    this.responseBody = responseBody;
    this.responseHeaders = caseInsensitiveCopyOf(responseHeaders);
    this.request = checkRequestNotNull(request);
  }

  protected FeignException(int status, String message, Request request) {
    super(message);
    this.status = status;
    this.request = checkRequestNotNull(request);
  }

  @Deprecated
  protected FeignException(int status, String message, Request request, byte[] responseBody,
      Map<String, Collection<String>> responseHeaders) {
    super(message);
    this.status = status;
    this.responseBody = responseBody;
    this.responseHeaders = caseInsensitiveCopyOf(responseHeaders);
    this.request = checkRequestNotNull(request);
  }

  protected FeignException(int status, String message, Response response) {
    super(message);
    this.status = status;
    this.response = checkResponseNotNull(response);
    this.request = checkRequestNotNull(response.request());
  }

  protected FeignException(int status, String message, Response response, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.response = checkResponseNotNull(response);
    this.request = checkRequestNotNull(response.request());
  }

  private Request checkRequestNotNull(Request request) {
    return checkNotNull(request, EXCEPTION_MESSAGE_TEMPLATE_NULL_REQUEST);
  }

  private Response checkResponseNotNull(Response response) {
    return checkNotNull(response, EXCEPTION_MESSAGE_TEMPLATE_NULL_RESPONSE);
  }

  public int status() {
    return this.status;
  }

  @Deprecated
  private static byte[] getBodyAsByteArray(Response response) {
    byte[] body = new byte[] {};
    try {
      if (response.body() != null) {
        body = Util.toByteArray(response.body().asInputStream());
      }
    } catch (IOException ignored) { // NOPMD
    }
    return body;
  }

  /**
   * The Response Body, if present.
   *
   * @return the body of the response.
   * @deprecated use {@link #responseBody()} instead.
   */
  @Deprecated
  public byte[] content() {
    if (hasResponse()) {
      return getBodyAsByteArray(response());
    }
    return this.responseBody;
  }

  /**
   * The Response body.
   *
   * @return an Optional wrapping the response body.
   */
  @Deprecated
  public Optional<ByteBuffer> responseBody() {
    if (!hasResponse() && this.responseBody == null) {
      return Optional.empty();
    }
    return Optional.of(ByteBuffer.wrap(content()));
  }

  @Deprecated
  public Map<String, Collection<String>> responseHeaders() {
    if (hasResponse()) {
      return response.headers();
    }
    if (this.responseHeaders == null) {
      return Collections.emptyMap();
    }
    return responseHeaders;
  }

  public Request request() {
    return this.request;
  }

  public boolean hasRequest() {
    return (this.request != null);
  }

  @Deprecated
  public String contentUTF8() {
    if (content() != null) {
      return new String(content(), UTF_8);
    } else {
      return "";
    }
  }

  public Response response() {
    return this.response;
  }

  public boolean hasResponse() {
    return (this.response != null);
  }

  // move to AsyncResponseHandler
  static FeignException errorReading(Response response, IOException cause) {
    Request request = response.request();
    return new FeignException(
        response.status(),
        format("%s reading %s %s", cause.getMessage(), request.httpMethod(), request.url()),
        response,
        cause);
  }

  public static FeignException errorStatus(String methodKey, Response response) {
    String message = new FeignExceptionMessageBuilder()
        .withResponse(response)
        .withMethodKey(methodKey)
        .build();

    if (isClientError(response.status())) {
      return clientErrorStatus(response.status(), message, response.request());
    }
    if (isServerError(response.status())) {
      return serverErrorStatus(response.status(), message, response);
    }
    return new FeignException(response.status(), message, response);
  }

  private static boolean isClientError(int status) {
    return status >= 400 && status < 500;
  }

  private static FeignClientException clientErrorStatus(int status,
                                                        String message,
                                                        Request request) {
    switch (status) {
      case 400:
        return new BadRequest(message, request);
      case 401:
        return new Unauthorized(message, request);
      case 403:
        return new Forbidden(message, request);
      case 404:
        return new NotFound(message, request);
      case 405:
        return new MethodNotAllowed(message, request);
      case 406:
        return new NotAcceptable(message, request);
      case 409:
        return new Conflict(message, request);
      case 410:
        return new Gone(message, request);
      case 415:
        return new UnsupportedMediaType(message, request);
      case 429:
        return new TooManyRequests(message, request);
      case 422:
        return new UnprocessableEntity(message, request);
      default:
        return new FeignClientException(status, message, request);
    }
  }

  private static boolean isServerError(int status) {
    return status >= 500 && status <= 599;
  }

  private static FeignServerException serverErrorStatus(int status,
                                                        String message,
                                                        Response response) {
    switch (status) {
      case 500:
        return new InternalServerError(message, response);
      case 501:
        return new NotImplemented(message, response);
      case 502:
        return new BadGateway(message, response);
      case 503:
        return new ServiceUnavailable(message, response);
      case 504:
        return new GatewayTimeout(message, response);
      default:
        return new FeignServerException(status, message, response);
    }
  }

  static FeignException errorExecuting(Request request, IOException cause) {
    return new RetryableException(
        -1,
        format("%s executing %s %s", cause.getMessage(), request.httpMethod(), request.url()),
        request.httpMethod(),
        cause,
        null, request);
  }

  public static class FeignClientException extends FeignException {
    public FeignClientException(int status, String message, Request request) {
      super(status, message, request);
    }
  }


  public static class BadRequest extends FeignClientException {
    public BadRequest(String message, Request request) {
      super(400, message, request);
    }
  }


  public static class Unauthorized extends FeignClientException {
    public Unauthorized(String message, Request request) {
      super(401, message, request);
    }
  }


  public static class Forbidden extends FeignClientException {
    public Forbidden(String message, Request request) {
      super(403, message, request);
    }
  }


  public static class NotFound extends FeignClientException {
    public NotFound(String message, Request request) {
      super(404, message, request);
    }
  }


  public static class MethodNotAllowed extends FeignClientException {
    public MethodNotAllowed(String message, Request request) {
      super(405, message, request);
    }
  }


  public static class NotAcceptable extends FeignClientException {
    public NotAcceptable(String message, Request request) {
      super(406, message, request);
    }
  }


  public static class Conflict extends FeignClientException {
    public Conflict(String message, Request request) {
      super(409, message, request);
    }
  }


  public static class Gone extends FeignClientException {
    public Gone(String message, Request request) {
      super(410, message, request);
    }
  }


  public static class UnsupportedMediaType extends FeignClientException {
    public UnsupportedMediaType(String message, Request request) {
      super(415, message, request);
    }
  }


  public static class TooManyRequests extends FeignClientException {
    public TooManyRequests(String message, Request request) {
      super(429, message, request);
    }
  }


  public static class UnprocessableEntity extends FeignClientException {
    public UnprocessableEntity(String message, Request request) {
      super(422, message, request);
    }
  }


  public static class FeignServerException extends FeignException {
    public FeignServerException(int status, String message, Response response) {
      super(status, message, response);
    }
  }


  public static class InternalServerError extends FeignServerException {
    public InternalServerError(String message, Response response) {
      super(500, message, response);
    }
  }


  public static class NotImplemented extends FeignServerException {
    public NotImplemented(String message, Response response) {
      super(501, message, response);
    }
  }


  public static class BadGateway extends FeignServerException {
    public BadGateway(String message, Response response) {
      super(502, message, response);
    }
  }


  public static class ServiceUnavailable extends FeignServerException {
    public ServiceUnavailable(String message, Response response) {
      super(503, message, response);
    }
  }


  public static class GatewayTimeout extends FeignServerException {
    public GatewayTimeout(String message, Response response) {
      super(504, message, response);
    }
  }

  private static class FeignExceptionMessageBuilder {

    private static final int MAX_BODY_BYTES_LENGTH = 400;
    private static final int MAX_BODY_CHARS_LENGTH = 200;

    private Response response;

    private byte[] body;
    private String methodKey;

    public FeignExceptionMessageBuilder withResponse(Response response) {
      this.response = response;
      this.body = getBodyAsByteArray(response);
      return this;
    }

    public FeignExceptionMessageBuilder withMethodKey(String methodKey) {
      this.methodKey = methodKey;
      return this;
    }

    public String build() {
      StringBuilder result = new StringBuilder();

      if (response.reason() != null) {
        result.append(format("[%d %s]", response.status(), response.reason()));
      } else {
        result.append(format("[%d]", response.status()));
      }
      result.append(format(" during [%s] to [%s] [%s]", response.request().httpMethod(),
          response.request().url(), methodKey));

      result.append(format(": [%s]", getBodyAsString(body, response.headers())));

      return result.toString();
    }

    private static String getBodyAsString(byte[] body, Map<String, Collection<String>> headers) {
      Charset charset = getResponseCharset(headers);
      if (charset == null) {
        charset = Util.UTF_8;
      }
      return getResponseBody(body, charset);
    }

    private static String getResponseBody(byte[] body, Charset charset) {
      if (body.length < MAX_BODY_BYTES_LENGTH) {
        return new String(body, charset);
      }
      return getResponseBodyPreview(body, charset);
    }

    private static String getResponseBodyPreview(byte[] body, Charset charset) {
      try {
        Reader reader = new InputStreamReader(new ByteArrayInputStream(body), charset);
        CharBuffer result = CharBuffer.allocate(MAX_BODY_CHARS_LENGTH);

        reader.read(result);
        reader.close();
        ((Buffer) result).flip();
        return result.toString() + "... (" + body.length + " bytes)";
      } catch (IOException e) {
        return e.toString() + ", failed to parse response";
      }
    }

    private static Charset getResponseCharset(Map<String, Collection<String>> headers) {

      Collection<String> strings = headers.get("content-type");
      if (strings == null || strings.isEmpty()) {
        return null;
      }

      Pattern pattern = Pattern.compile(".*charset=([^\\s|^;]+).*");
      Matcher matcher = pattern.matcher(strings.iterator().next());
      if (!matcher.lookingAt()) {
        return null;
      }

      String group = matcher.group(1);
      if (!Charset.isSupported(group)) {
        return null;
      }
      return Charset.forName(group);

    }
  }
}
