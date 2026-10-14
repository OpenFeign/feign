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
package feign.jdkjson;

import static java.lang.String.format;

import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.PredicatedEncoder;
import java.lang.reflect.Type;
import jdk.incubator.json.JsonValue;

public class JdkJsonEncoder implements Encoder, PredicatedEncoder {

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (object == null) {
      return;
    }
    if (!(object instanceof JsonValue jsonBody)) {
      throw new EncodeException(format("%s is not a type supported by this encoder.", bodyType));
    }
    template.body(jsonBody.toString());
  }

  @Override
  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return Util.isJsonContentType(template);
  }
}
