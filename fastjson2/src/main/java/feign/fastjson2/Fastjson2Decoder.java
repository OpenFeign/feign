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

import static feign.Util.ensureClosed;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

/**
 * @author changjin wei(魏昌进)
 */
public class Fastjson2Decoder implements Decoder {

  private final JSONReader.Feature[] features;

  public Fastjson2Decoder() {
    this(new JSONReader.Feature[0]);
  }

  public Fastjson2Decoder(JSONReader.Feature[] features) {
    this.features = features;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, FeignException {
    if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    Reader reader = response.body().asReader(response.charset());
    try {
      return JSON.parseObject(reader, type, features);
    } catch (JSONException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    } finally {
      ensureClosed(reader);
    }
  }
}
