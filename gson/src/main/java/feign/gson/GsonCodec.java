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
package feign.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import feign.Experimental;
import feign.codec.Codec;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;

@Experimental
public class GsonCodec implements Codec, JsonCodec {

  private final GsonEncoder encoder;
  private final GsonDecoder decoder;

  public GsonCodec() {
    this(new Gson());
  }

  public GsonCodec(Iterable<TypeAdapter<?>> adapters) {
    this.encoder = new GsonEncoder(adapters);
    this.decoder = new GsonDecoder(adapters);
  }

  public GsonCodec(Gson gson) {
    this.encoder = new GsonEncoder(gson);
    this.decoder = new GsonDecoder(gson);
  }

  @Override
  public JsonEncoder encoder() {
    return encoder;
  }

  @Override
  public JsonDecoder decoder() {
    return decoder;
  }
}
