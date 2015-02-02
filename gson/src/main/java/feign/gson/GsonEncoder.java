/*
 * Copyright 2013 Netflix, Inc.
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
package feign.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import java.lang.reflect.Type;
import java.util.Collections;

import feign.RequestTemplate;
import feign.codec.Encoder;

public class GsonEncoder implements Encoder {

  private final Gson gson;

  public GsonEncoder(Iterable<TypeAdapter<?>> adapters) {
    this(GsonFactory.create(adapters));
  }

  public GsonEncoder() {
    this(Collections.<TypeAdapter<?>>emptyList());
  }

  public GsonEncoder(Gson gson) {
    this.gson = gson;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    template.body(gson.toJson(object, bodyType));
  }
}
