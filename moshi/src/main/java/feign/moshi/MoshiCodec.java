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
package feign.moshi;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import feign.Experimental;
import feign.codec.Codec;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;

@Experimental
public class MoshiCodec implements Codec, JsonCodec {

  private final MoshiEncoder encoder;
  private final MoshiDecoder decoder;

  public MoshiCodec() {
    this(new Moshi.Builder().build());
  }

  public MoshiCodec(Iterable<JsonAdapter<?>> adapters) {
    this.encoder = new MoshiEncoder(adapters);
    this.decoder = new MoshiDecoder(adapters);
  }

  public MoshiCodec(Moshi moshi) {
    this.encoder = new MoshiEncoder(moshi);
    this.decoder = new MoshiDecoder(moshi);
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
