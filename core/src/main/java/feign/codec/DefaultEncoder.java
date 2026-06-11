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
package feign.codec;

import static java.lang.String.format;

import feign.RequestTemplate;
import java.lang.reflect.Type;

public class DefaultEncoder implements Encoder {

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    if (bodyType == String.class) {
      template.body(object.toString());
    } else if (bodyType == byte[].class) {
      template.body((byte[]) object, null);
    } else if (object != null) {
      throw new EncodeException(
          format("%s is not a type supported by this encoder.", object.getClass()));
    }
  }
}
