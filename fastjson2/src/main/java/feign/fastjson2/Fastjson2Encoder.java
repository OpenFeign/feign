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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;

/**
 * @author changjin wei(魏昌进)
 */
public class Fastjson2Encoder implements Encoder {

  private final JSONWriter.Feature[] features;

  public Fastjson2Encoder() {
    this(new JSONWriter.Feature[0]);
  }

  public Fastjson2Encoder(JSONWriter.Feature[] features) {
    this.features = features;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    template.body(JSON.toJSONBytes(object, features), Util.UTF_8);
  }
}
