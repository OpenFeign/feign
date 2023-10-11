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
import feign.codec.DecodeException;
import feign.codec.Decoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class ReactiveDecoder implements Decoder {

    private final Decoder delegate;

    public ReactiveDecoder(Decoder decoder) {
        this.delegate = decoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        Class<?> rawType = Types.getRawType(type);
        if (rawType.isAssignableFrom(Mono.class)) {
            Type lastType = Types.resolveLastTypeParameter(type, Mono.class);
            return Mono.fromCallable(() -> delegate.decode(response, lastType));
        }
        if (rawType.isAssignableFrom(Flux.class)) {
            Type lastType = Types.resolveLastTypeParameter(type, Flux.class);
            Type listType = Types.parameterize(List.class, lastType);
            Object decoded = delegate.decode(response, listType);
            if (decoded instanceof Iterable) {
                return Flux.fromIterable((Iterable) decoded);
            } else {
                String errorMessage = "Expected type Iterable, but was: " + decoded.getClass();
                throw new DecodeException(response.status(), errorMessage, response.request());
            }
        }

        return delegate.decode(response, type);
    }
}
