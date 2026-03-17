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
package feign.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import feign.Experimental;
import feign.codec.Codec;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;

@Experimental
public class Fastjson2Codec implements Codec, JsonCodec {

  private final Fastjson2Encoder encoder;
  private final Fastjson2Decoder decoder;

  public Fastjson2Codec() {
    this.encoder = new Fastjson2Encoder();
    this.decoder = new Fastjson2Decoder();
  }

  public Fastjson2Codec(JSONWriter.Feature[] writerFeatures, JSONReader.Feature[] readerFeatures) {
    this.encoder = new Fastjson2Encoder(writerFeatures);
    this.decoder = new Fastjson2Decoder(readerFeatures);
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
