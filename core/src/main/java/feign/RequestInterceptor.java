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
package feign;

/**
 * Zero or more {@code RequestInterceptors} may be configured for purposes such as adding headers to
 * all requests.  No guarantees are give with regards to the order that interceptors are applied.
 * Once interceptors are applied, {@link Target#apply(RequestTemplate)} is called to create the
 * immutable http request sent via {@link Client#execute(Request, feign.Request.Options)}. <br> <br>
 * For example: <br>
 * <pre>
 * public void apply(RequestTemplate input) {
 *     input.replaceHeader(&quot;X-Auth&quot;, currentToken);
 * }
 * </pre>
 * <br> <br><b>Configuration</b><br> <br> {@code RequestInterceptors} are configured via {@link
 * Feign.Builder#requestInterceptors}. <br> <br><b>Implementation notes</b><br> <br> Do not add
 * parameters, such as {@code /path/{foo}/bar } in your implementation of {@link
 * #apply(RequestTemplate)}. <br> Interceptors are applied after the template's parameters are
 * {@link RequestTemplate#resolve(java.util.Map) resolved}.  This is to ensure that you can
 * implement signatures are interceptors. <br> <br><br><b>Relationship to Retrofit 1.x</b><br> <br>
 * This class is similar to {@code RequestInterceptor.intercept()}, except that the implementation
 * can read, remove, or otherwise mutate any part of the request template.
 */
public interface RequestInterceptor {

  /**
   * Called for every request. Add data using methods on the supplied {@link RequestTemplate}.
   */
  void apply(RequestTemplate template);
}
