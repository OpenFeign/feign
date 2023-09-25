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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import feign.RequestTemplate;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collections;

public class MoshiEncoder implements Encoder {

  private final Moshi moshi;

  public MoshiEncoder() {
    this.moshi = new Moshi.Builder().build();
  }

  public MoshiEncoder(Moshi moshi) {
    this.moshi = moshi;
  }

  public MoshiEncoder(Iterable<JsonAdapter<?>> adapters) {
    this(MoshiFactory.create(adapters));
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    JsonAdapter<Object> jsonAdapter = moshi.adapter(bodyType).indent("  ");
    template.body(jsonAdapter.toJson(object));
  }
}
