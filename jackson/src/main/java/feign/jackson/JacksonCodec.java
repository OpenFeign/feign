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
package feign.jackson;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Experimental;
import feign.codec.Codec;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;

@Experimental
public class JacksonCodec implements Codec, JsonCodec {

  private final JacksonEncoder encoder;
  private final JacksonDecoder decoder;

  public JacksonCodec() {
    this(new ObjectMapper());
  }

  public JacksonCodec(Iterable<Module> modules) {
    this.encoder = new JacksonEncoder(modules);
    this.decoder = new JacksonDecoder(modules);
  }

  public JacksonCodec(ObjectMapper mapper) {
    this.encoder = new JacksonEncoder(mapper);
    this.decoder = new JacksonDecoder(mapper);
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
