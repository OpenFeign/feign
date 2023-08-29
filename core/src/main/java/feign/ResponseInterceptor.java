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
package feign;

import static feign.FeignException.errorReading;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * Interceptor for purposes such as verify or modify headers of response, verify the business status
 * of decoded object. Once interceptors are applied,
 * {@link ResponseInterceptor#intercept(Response, Function)} is called around decode method called
 */
public interface ResponseInterceptor {

  /**
   * Called for response around decode, must either manually invoke {@link Chain#next(Context)} or
   * manually create a new response object
   *
   * @param invocationContext information surrounding the response been decoded
   * @return decoded response
   */
  Object intercept(Context context, Chain chain) throws IOException;

  /**
   * Return a new {@link ResponseInterceptor} that invokes the current interceptor first and then
   * the one that is passed in.
   * 
   * @param nextInterceptor the interceptor to delegate to after the current
   * @return a new interceptor that chains the two
   */
  default ResponseInterceptor andThen(ResponseInterceptor nextInterceptor) {
    return (ic, chain) -> intercept(ic,
        nextContext -> nextInterceptor.intercept(nextContext, chain));
  }

  /**
   * Contract for delegation to the rest of the chain.
   */
  public interface Chain {
    Chain DEFAULT = ic -> {
      try {
        return ic.decoder().decode(ic.response(), ic.returnType());
      } catch (final FeignException e) {
        throw e;
      } catch (final RuntimeException e) {
        throw new DecodeException(ic.response().status(), e.getMessage(),
            ic.response().request(), e);
      } catch (IOException e) {
        throw errorReading(ic.response().request(), ic.response(), e);
      }
    };

    /**
     * Delegate to the rest of the chain to execute the request.
     * 
     * @param context the request to execute the {@link Chain} .
     * @return the response
     */
    Object next(Context context) throws IOException;
  }

  /**
   * Apply this interceptor to the given {@code Chain} resulting in an intercepted chain.
   * 
   * @param chain the chain to add interception around
   * @return a new chain instance
   */
  default Chain apply(Chain chain) {
    return request -> intercept(request, chain);
  }

  public class Context {

    private final Decoder decoder;
    private final Type returnType;
    private final Response response;

    Context(Decoder decoder, Type returnType, Response response) {
      this.decoder = decoder;
      this.returnType = returnType;
      this.response = response;
    }

    public Decoder decoder() {
      return decoder;
    }

    public Type returnType() {
      return returnType;
    }

    public Response response() {
      return response;
    }

  }

}
