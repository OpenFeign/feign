/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.auth;

import java.nio.charset.Charset;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import static feign.Util.ISO_8859_1;
import static feign.Util.checkNotNull;

/**
 * An interceptor that adds the request header needed to use HTTP basic authentication.
 */
public class BasicAuthRequestInterceptor implements RequestInterceptor {

  private final String headerValue;

  /**
   * Creates an interceptor that authenticates all requests with the specified username and password
   * encoded using ISO-8859-1.
   *
   * @param username the username to use for authentication
   * @param password the password to use for authentication
   */
  public BasicAuthRequestInterceptor(String username, String password) {
    this(username, password, ISO_8859_1);
  }

  /**
   * Creates an interceptor that authenticates all requests with the specified username and password
   * encoded using the specified charset.
   *
   * @param username the username to use for authentication
   * @param password the password to use for authentication
   * @param charset  the charset to use when encoding the credentials
   */
  public BasicAuthRequestInterceptor(String username, String password, Charset charset) {
    checkNotNull(username, "username");
    checkNotNull(password, "password");
    this.headerValue = "Basic " + base64Encode((username + ":" + password).getBytes(charset));
  }

  /*
   * This uses a Sun internal method; if we ever encounter a case where this method is not available, the appropriate
   * response would be to pull the necessary portions of Guava's BaseEncoding class into Util.
   */
  private static String base64Encode(byte[] bytes) {
    return Base64.encode(bytes);
  }

  @Override
  public void apply(RequestTemplate template) {
    template.header("Authorization", headerValue);
  }
}

