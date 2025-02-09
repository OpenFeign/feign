/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.stream;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;

public class InputStreamAndReaderDecoder implements Decoder {
  private final Decoder delegateDecoder;

  public InputStreamAndReaderDecoder(Decoder delegate) {
    this.delegateDecoder = delegate;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {

    if (InputStream.class.equals(type)) return response.body().asInputStream();

    if (Reader.class.equals(type)) return response.body().asReader();

    if (delegateDecoder == null) return null;

    return delegateDecoder.decode(response, type);
  }
}
