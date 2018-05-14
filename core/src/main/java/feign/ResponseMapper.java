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
package feign;

import java.lang.reflect.Type;

/**
 * Map function to apply to the response before decoding it.
 *
 * <pre>
 * {@code
 * new ResponseMapper() {
 *      &#64;Override
 *      public Response map(Response response, Type type) {
 *          try {
 *            return response
 *              .toBuilder()
 *              .body(Util.toString(response.body().asReader()).toUpperCase().getBytes())
 *              .build();
 *          } catch (IOException e) {
 *              throw new RuntimeException(e);
 *          }
 *      }
 *  };
 * }
 * </pre>
 */
public interface ResponseMapper {

  Response map(Response response, Type type);
}
