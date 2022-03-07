/*
 * Copyright 2012-2022 The Feign Authors
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
package feign.stream;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static feign.Util.UTF_8;
import static feign.Util.ensureClosed;

/**
 * Iterator based decoder that support streaming.
 * <p>
 * <p>
 * Example: <br>
 * 
 * <pre>
 * <code>
 * Feign.builder()
 *   .decoder(StreamDecoder.create(new Decoder.Default(), JacksonIteratorDecoder.create()))
 *   .doNotCloseAfterDecode() // Required for streaming
 *   .target(GitHub.class, "https://api.github.com");
 * interface GitHub {
 *  {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Stream<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }</code>
 * </pre>
 * 
 * @author mroccyen
 */
public final class StreamDecoder implements Decoder {

  private final Decoder delegate;
  private final Decoder iteratorDecoder;

  StreamDecoder(Decoder delegate, Decoder iteratorDecoder) {
    this.delegate = delegate;
    this.iteratorDecoder = iteratorDecoder;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, FeignException {
    if (!isStream(type)) {
      return delegate.decode(response, type);
    }
    ParameterizedType streamType = (ParameterizedType) type;
    Iterator<?> iterator = (Iterator<?>) iteratorDecoder.decode(
        response, new IteratorParameterizedType(streamType));

    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator, 0), false)
        .onClose(() -> {
          if (iterator instanceof Closeable) {
            ensureClosed((Closeable) iterator);
          } else {
            ensureClosed(response);
          }
        });
  }

  private boolean isStream(Type type) {
    if (!(type instanceof ParameterizedType)) {
      return false;
    }
    ParameterizedType parameterizedType = (ParameterizedType) type;
    return parameterizedType.getRawType().equals(Stream.class);
  }

  public static StreamDecoder create(Decoder delegate, Decoder iteratorDecoder) {
    return new StreamDecoder(delegate, iteratorDecoder);
  }

  static final class IteratorParameterizedType implements ParameterizedType {

    private final ParameterizedType streamType;

    IteratorParameterizedType(ParameterizedType streamType) {
      this.streamType = streamType;
    }

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
      return null;
    }
  }

  public static class DefaultIteratorDecoder implements Decoder {
    @Override
    public Object decode(Response response, Type type) throws IOException {
      BufferedReader bufferedReader = new BufferedReader(response.body().asReader(UTF_8));
      return bufferedReader.lines().iterator();
    }
  }

}
