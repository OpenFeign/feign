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

import static java.lang.String.format;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.PredicatedDecoder;
import feign.utils.ContentTypeParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;

public class InputStreamAndReaderDecoder implements PredicatedDecoder {

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {

    if (InputStream.class.equals(type)) return response.body().asInputStream();

    if (Reader.class.equals(type))
      return response
          .body()
          .asReader(
              ContentTypeParser.parseContentTypeFromHeaders(response.headers())
                  .map(ctr -> ctr.getCharset().orElse(Util.UTF_8))
                  .orElse(Util.UTF_8));

    throw new DecodeException(
        response.status(),
        format("%s is not a type supported by this decoder.", type),
        response.request());
  }

  @Override
  public boolean canDecode(Response response, Type type) {
    if (InputStream.class.equals(type)) return true;
    if (Reader.class.equals(type)) return true;

    return false;
  }
}
