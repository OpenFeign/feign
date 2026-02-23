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

import feign.Response;
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Type;

public class DefaultDecoder extends StringDecoder {

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    if (byte[].class.equals(type)) {
      return Util.toByteArray(response.body().asInputStream());
    }
    return super.decode(response, type);
  }
}
