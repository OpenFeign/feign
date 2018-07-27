/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.hystrix;

import static feign.Util.resolveLastTypeParameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import com.netflix.hystrix.HystrixCommand;
import feign.Contract;
import feign.MethodMetadata;
import rx.Completable;
import rx.Observable;
import rx.Single;

/**
 * This special cases methods that return {@link HystrixCommand}, {@link Observable}, or
 * {@link Single} so that they are decoded properly.
 * 
 * <p>
 * For example, {@literal HystrixCommand<Foo>} and {@literal Observable<Foo>} will decode
 * {@code Foo}.
 */
// Visible for use in custom Hystrix invocation handlers
public final class HystrixDelegatingContract implements Contract {

  private final Contract delegate;

  public HystrixDelegatingContract(Contract delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
    List<MethodMetadata> metadatas = this.delegate.parseAndValidatateMetadata(targetType);

    for (MethodMetadata metadata : metadatas) {
      Type type = metadata.returnType();

      if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(HystrixCommand.class)) {
        Type actualType = resolveLastTypeParameter(type, HystrixCommand.class);
        metadata.returnType(actualType);
      } else if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(Observable.class)) {
        Type actualType = resolveLastTypeParameter(type, Observable.class);
        metadata.returnType(actualType);
      } else if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(Single.class)) {
        Type actualType = resolveLastTypeParameter(type, Single.class);
        metadata.returnType(actualType);
      } else if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(Completable.class)) {
        metadata.returnType(void.class);
      }
    }

    return metadatas;
  }
}
