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
package feign.core.codec;

import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;

public class DefaultDecoder extends StringDecoder {

  /**
   * Accepts exactly what {@link #decode} handles: everything {@link StringDecoder} accepts, plus a
   * {@code byte[]} return type.
   *
   * @param response {@inheritDoc}
   * @param type {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean canDecode(Response response, Type type) {
    if (byte[].class.equals(type)) return true;
    if (InputStream.class.equals(type)) return true;
    if (Reader.class.equals(type)) return true;
    if (super.canDecode(response, type)) return true;

    return false;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    if (byte[].class.equals(type)) return Util.toByteArray(response.body().asInputStream());
    if (InputStream.class.equals(type)) return response.body().asInputStream();
    if (Reader.class.equals(type)) return response.body().asReader(response.charset());

    return super.decode(response, type);
  }
}
