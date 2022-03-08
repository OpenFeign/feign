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
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import static feign.Util.UTF_8;

/**
 * @author mroccyen
 */
public interface IteratorDecoder extends Decoder {
  /**
   * Decodes an http response into an iterator. If you need to wrap exceptions, please do so via
   * {@link DecodeException}.
   *
   * @param response the response to decode
   * @param type {@link java.lang.reflect.Method#getGenericReturnType() generic return type} of the
   *        method corresponding to this {@code response}.
   * @return instance of {@code type}
   * @throws IOException will be propagated safely to the caller.
   * @throws DecodeException when decoding failed due to a checked exception besides IOException.
   * @throws FeignException when decoding succeeds, but conveys the operation failed.
   */
  Iterator<?> decodeIterator(Response response, Type type)
      throws IOException, DecodeException, FeignException;

  class LineToIteratorDecoder implements IteratorDecoder {

    private final Decoder delegate;

    public LineToIteratorDecoder(Decoder delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
      return delegate.decode(response, type);
    }

    @Override
    public Iterator<?> decodeIterator(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      BufferedReader bufferedReader = new BufferedReader(response.body().asReader(UTF_8));
      return bufferedReader.lines().iterator();
    }
  }
}
