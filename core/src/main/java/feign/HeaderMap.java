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

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Map;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A template parameter that can be applied to a Map that contains header entries, where the keys
 * are Strings that are the header field names and the values are the header field values. The
 * headers specified by the map will be applied to the request after all other processing, and will
 * take precedence over any previously specified header parameters. <br>
 * This parameter is useful in cases where different header fields and values need to be set on an
 * API method on a per-request basis in a thread-safe manner and independently of Feign client
 * construction. A concrete example of a case like this are custom metadata header fields (e.g. as
 * "x-amz-meta-*" or "x-goog-meta-*") where the header field names are dynamic and the range of keys
 * cannot be determined a priori. The {@link Headers} annotation does not allow this because the
 * header fields that it defines are static (it is not possible to add or remove fields on a
 * per-request basis), and doing this using a custom {@link Target} or {@link RequestInterceptor}
 * can be cumbersome (it requires more code for per-method customization, it is difficult to
 * implement in a thread-safe manner and it requires customization when the Feign client for the API
 * is built). <br>
 * 
 * <pre>
 * ...
 * &#64;RequestLine("GET /servers/{serverId}")
 * void get(&#64;Param("serverId") String serverId, &#64;HeaderMap Map<String, Object>);
 * ...
 * </pre>
 * 
 * The annotated parameter must be an instance of {@link Map}, and the keys must be Strings. The
 * header field value of a key will be the value of its toString method, except in the following
 * cases: <br>
 * <br>
 * <ul>
 * <li>if the value is null, the value will remain null (rather than converting to the String
 * "null")
 * <li>if the value is an {@link Iterable}, it is converted to a {@link List} of String objects
 * where each value in the list is either null if the original value was null or the value's
 * toString representation otherwise.
 * </ul>
 * <br>
 * Once this conversion is applied, the query keys and resulting String values follow the same
 * contract as if they were set using {@link RequestTemplate#header(String, String...)}.
 */
@Retention(RUNTIME)
@java.lang.annotation.Target(PARAMETER)
public @interface HeaderMap {
}
