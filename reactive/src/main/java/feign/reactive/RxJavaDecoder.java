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
package feign.reactive;

import feign.FeignException;
import feign.Response;
import feign.Types;
import feign.codec.Decoder;
import io.reactivex.Flowable;
import java.io.IOException;
import java.lang.reflect.Type;

public class RxJavaDecoder implements Decoder {

  private final Decoder delegate;

  public RxJavaDecoder(Decoder decoder) {
    this.delegate = decoder;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, FeignException {
    Class<?> rawType = Types.getRawType(type);
    if (rawType.isAssignableFrom(Flowable.class)) {
      Type lastType = Types.resolveLastTypeParameter(type, Flowable.class);
      return delegate.decode(response, lastType);
    }

    return delegate.decode(response, type);
  }
}
