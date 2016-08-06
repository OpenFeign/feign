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
 * Zero or more {@code RequestPostProcessor} may be configured for purposes such as adding headers to
 * all requests.  No guarantees are give with regards to the order that postprocessor are applied.
 * This processor will be executed after all processing of the {@link RequestTemplate} took place and
 * the response has been received (or an exception took place).
 *
 * Example of usage might be distributed tracing. You want to be sure that some operations are taking
 * place when the request was successfully sent. You might want to perform other actions if the request
 * failed and wasn't retried. On the other hand you might act differently when the request failed
 * and retry will take place.
 *
 * For example: <br>
 * <pre>
 * public void apply(RequestTemplate input, RetryableException e) {
 *     // do sth meaningful after the response was received
 * }
 * </pre>
 * <br> <br><b>Configuration</b><br> <br> {@code RequestPreProcessor} are configured via {@link
 * Feign.Builder#requestPostProcessors}.
 */
public interface RequestPostProcessor {

  /**
   * Called for every request. Can support the following states of {@link RequestTemplate#retried} and
   * values of {@link RetryableException}.
   *
   * <li>
   *   <ul>retried set to {@code false} and {@link RetryableException} null - request sent successfully</ul>
   *   <ul>retried set to {@code true} and {@link RetryableException} not null - request failed to be sent and retry has been successful</ul>
   *   <ul>retried set to {@code false} and {@link RetryableException} not null - request failed to be sent and exceeded number of possible retries</ul>
   * </li>
   *
   * Depending on values of {@link RequestTemplate} and state of {@link RetryableException} you can
   * perform different decisions after the response has been received / exception was thrown.
   */
  void apply(RequestTemplate template, RetryableException e);
}
