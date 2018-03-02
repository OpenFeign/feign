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

import java.io.Closeable;
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
import feign.codec.Decoder;

import static feign.Util.ensureClosed;

/**
 * Iterator based decoder that support streaming.
 *
 * <p>Example: <br>
 * <pre><code>
 * Feign.builder()
 *   .decoder(new StreamDecoder(new JacksonIteratorDecoder()))
 *   .closeAfterDecode(false) // Required for streaming
 *   .target(GitHub.class, "https://api.github.com");
 * interface GitHub {
 *  {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Stream<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }</code></pre>
 * @author Pierrick HYMBERT
 */
public class StreamDecoder implements Decoder {

  private final Decoder iteratorDecoder;

  public StreamDecoder(final Decoder iteratorDecoder) {
    this.iteratorDecoder = iteratorDecoder;
  }

  @Override
  public Object decode(final Response response, final Type type)
      throws IOException, FeignException {
    if (!(type instanceof ParameterizedType)) {
      throw new IllegalArgumentException(
          "StreamDecoder supports only stream: unknown " + type);
    }
    final ParameterizedType streamType = (ParameterizedType) type;
    if (!Stream.class.equals(streamType.getRawType())) {
      throw new IllegalArgumentException(
          "StreamDecoder supports only stream: unknown " + type);
    }
    final Integer streamSize = computeEstimatedStreamSize(streamType, response);

    final Spliterator<?> spliterator;
    final Iterator<?> iterator = provideIterator(streamType, response);
    if (streamSize == null) {
      spliterator =
          Spliterators.spliteratorUnknownSize(iterator,
              streamCharacteristics());
    } else {
      spliterator =
          Spliterators.spliterator(iterator, streamSize,
              streamCharacteristics());
    }

    return StreamSupport.stream(spliterator, isParallelStream())
                        .onClose(onStreamClose(streamType, iterator, response));
  }

  /**
   * Provides an iterator to retrieve data from the response and type.
   * Implementation is responsible to close the response body.
   *
   * @param streamType Type of the stream
   * @param response          Current response
   * @param <T>               Type of the iterator.
   */
  @SuppressWarnings("unchecked")
  protected <T> Iterator<? extends T> provideIterator(final ParameterizedType streamType,
                                                      final Response response) throws IOException {
    return (Iterator) iteratorDecoder.decode(response, new ParameterizedType() {

      @Override
      public Type[] getActualTypeArguments() {
        return streamType.getActualTypeArguments();
      }

      @Override
      public Type getRawType() {
        return Iterator.class;
      }

      @Override
      public Type getOwnerType() {
        return streamType.getOwnerType();
      }
    });
  }

  /**
   * Computes estimated stream size, null if the size is unknown.
   *
   * @param streamType Type of stream
   * @param response          http response
   * @return Estimated stream size
   */
  protected <T> Integer computeEstimatedStreamSize(final ParameterizedType streamType,
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
   * @param streamType type of the stream
   * @param iterator Stream based iterator
   *@param response Response Related stream response  @return Runnable on stream close
   */
  protected <T> Runnable onStreamClose(ParameterizedType streamType, Iterator<?> iterator, Response response) {
    return () -> {
      if (iterator instanceof Closeable) {
        ensureClosed((Closeable) iterator);
      } else {
        ensureClosed(response);
      }
    };
  }
}