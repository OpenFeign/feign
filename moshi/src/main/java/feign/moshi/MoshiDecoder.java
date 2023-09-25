/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.moshi;

import com.google.common.io.CharStreams;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonEncodingException;
import com.squareup.moshi.Moshi;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import static feign.Util.UTF_8;
import static feign.Util.ensureClosed;

public class MoshiDecoder implements Decoder {
  private final Moshi moshi;

  public MoshiDecoder(Moshi moshi) {
    this.moshi = moshi;
  }

  public MoshiDecoder() {
    this.moshi = new Moshi.Builder().build();
  }

  public MoshiDecoder(Iterable<JsonAdapter<?>> adapters) {
    this(MoshiFactory.create(adapters));
  }


  @Override
  public Object decode(Response response, Type type) throws IOException {
    JsonAdapter<Object> jsonAdapter = moshi.adapter(type);

    if (response.status() == 404 || response.status() == 204)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;

    Reader reader = response.body().asReader(UTF_8);

    try {
      return parseJson(jsonAdapter, reader);
    } catch (JsonDataException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    } finally {
      ensureClosed(reader);
    }
  }

  private Object parseJson(JsonAdapter<Object> jsonAdapter, Reader reader) throws IOException {
    String targetString = CharStreams.toString(reader);
    return jsonAdapter.fromJson(targetString);
  }
}

