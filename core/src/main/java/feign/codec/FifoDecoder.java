/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.codec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import feign.FeignException;
import feign.Response;

public class FifoDecoder implements Decoder {

  private final List<TypedDecoder> decoders = new ArrayList<>();

  private final Decoder defaultDecoder;

  public FifoDecoder() {
    this(new Decoder.Default());
  }

  public FifoDecoder(Decoder defaultDecoder) {
    this.defaultDecoder = defaultDecoder;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {

    for (TypedDecoder decoder : decoders) {
      if (decoder.canDecode(response, type)) {
        return decoder.decode(response, type);
      }
    }

    return this.defaultDecoder.decode(response, type);
  }

  public FifoDecoder append(TypedDecoder decoder) {
    decoders.add(decoder);

    return this;
  }

}
