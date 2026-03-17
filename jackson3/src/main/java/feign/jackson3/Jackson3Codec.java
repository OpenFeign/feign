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
package feign.jackson3;

import feign.Experimental;
import feign.codec.Codec;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

@Experimental
public class Jackson3Codec implements Codec, JsonCodec {

  private final Jackson3Encoder encoder;
  private final Jackson3Decoder decoder;

  public Jackson3Codec() {
    this(JsonMapper.builder().build());
  }

  public Jackson3Codec(Iterable<JacksonModule> modules) {
    this.encoder = new Jackson3Encoder(modules);
    this.decoder = new Jackson3Decoder(modules);
  }

  public Jackson3Codec(JsonMapper mapper) {
    this.encoder = new Jackson3Encoder(mapper);
    this.decoder = new Jackson3Decoder(mapper);
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
