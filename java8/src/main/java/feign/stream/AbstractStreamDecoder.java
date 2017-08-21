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

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.StreamDecoder;

import static feign.Util.ensureClosed;

/**
 * Base decoder to support java8 streams.
 */
public abstract class AbstractStreamDecoder implements StreamDecoder {

  @Override
  public Object decode(final Response response, final Type type)
      throws IOException, DecodeException, FeignException {
    if (!(type instanceof ParameterizedType)) {
      throw new IllegalArgumentException(
          "Java8StreamDecoder supports only stream: unknown " + type);
    }
    final ParameterizedType parameterizedType = (ParameterizedType) type;
    if (!Stream.class.equals(parameterizedType.getRawType())) {
      throw new IllegalArgumentException(
          "Java8StreamDecoder supports only stream: unknown " + type);
    }
    final Class<?> streamedType = (Class<?>) parameterizedType.getActualTypeArguments()[0];

    final Integer streamSize = computeEstimatedStreamSize(streamedType, response);

    final Spliterator<?> spliterator;
    if (streamSize == null) {
      spliterator =
          Spliterators.spliteratorUnknownSize(provideStreamIterator(streamedType, response),
              streamCharacteristics());
    } else {
      spliterator =
          Spliterators.spliterator(provideStreamIterator(streamedType, response), streamSize,
              streamCharacteristics());
    }

    return StreamSupport.stream(spliterator, isParallelStream()).onClose(onStreamClose(streamedType, response));
  }

  /**
   * Provides an iterator to retrieve data from the response and type.
   * Implementation is responsible to close the response body.
   *
   * @param streamedType Type to stream as an iterator
   * @param response     Current response
   * @param <T>          Type of the iterator.
   */
  protected abstract <T> Iterator<? extends T> provideStreamIterator(
      final Class<? extends T> streamedType, final Response response);

  /**
   * Computes estimated stream size, null if the size is unknown.
   *
   * @param streamedType Type to stream
   * @param response     http response
   * @return Estimated stream size
   */
  protected <T> Integer computeEstimatedStreamSize(final Class<? extends T> streamedType,
                                               final Response response) {
    return null;
  }

  /**
   * @return True if the underlying iterator support parallel calls, false otherwise. Default false.
   */
  protected boolean isParallelStream() {
    return false;
  }

  /**
   * @return Supported stream characteristics
   */
  protected int streamCharacteristics() {
    return Spliterator.DISTINCT | Spliterator.NONNULL;
  }

  /**
   * Action to run when stream is closed.
   * Default action is to close underlying response.
   *
   * @param streamedType Streamed type
   * @param response Response Related stream response
   * @return Runnable on stream close
   */
  protected <T> Runnable onStreamClose(Class<? extends T> streamedType, Response response) {
    return () -> ensureClosed(response.body());
  }
}