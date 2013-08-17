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
 * An {@code Observer} is asynchronous equivalent to an {@code Iterable}.
 * <br>
 * Each call to {@link #subscribe(Observer)} implies a new
 * {@link Request HTTP request}.
 *
 * @param <T> expected value to decode incrementally from the http response.
 */
public interface Observable<T> {

  /**
   * Calling subscribe will initiate a new HTTP request which will be
   * {@link feign.codec.IncrementalDecoder incrementally decoded} into the
   * {@code observer} until it is finished or
   * {@link feign.Subscription#unsubscribe()} is called.
   *
   * @param observer
   * @return a {@link Subscription} with which you can stop the streaming of
   *         events to the {@code observer}.
   */
  public Subscription subscribe(Observer<T> observer);
}
