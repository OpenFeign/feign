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
 * For example: <br>
 * <pre>
 * public void apply(RequestTemplate input) {
 *     // do sth meaningful after the response was received
 * }
 * </pre>
 * <br> <br><b>Configuration</b><br> <br> {@code RequestPreProcessor} are configured via {@link
 * Feign.Builder#requestPostProcessors}.
 */
public interface RequestPostProcessor {

  /**
   * Called for every request. Add data using methods on the supplied {@link RequestTemplate}.
   *
   * @param e - RetryableException if the request is to be retried. Can be null if there was no exception.
   */
  void apply(RequestTemplate template, RetryableException e);
}
