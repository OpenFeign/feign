/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.stream;

import java.util.Iterator;
import java.util.function.BiFunction;

import feign.Response;

/**
 * Iterator based decoder that support streaming.
 * For example: <br>
 * <pre>
 * Feign.builder()
 * .decoder(new Java8StreamDecoder((type, response) -> JacksonIterator.<Contributor>builder().of(type).mapper(mapper).response(response).build()))
 * .target(GitHub.class, "https://api.github.com");
 * </pre>
 */
public class Java8StreamDecoder extends AbstractStreamDecoder {

  private final BiFunction<Class<?>, Response, Iterator<?>> iteratorProvider;

  public Java8StreamDecoder(final BiFunction<Class<?>, Response, Iterator<?>> iteratorProvider) {
    this.iteratorProvider = iteratorProvider;
  }

  @Override
  protected <T> Iterator<? extends T> provideStreamIterator(final Class<? extends T> streamedType,
                                                            final Response response) {
    return (Iterator<T>) iteratorProvider.apply(streamedType, response);
  }
}
