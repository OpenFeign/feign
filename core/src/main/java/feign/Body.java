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
import java.lang.annotation.Target;
import java.util.Map;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A possibly templated body of a PUT or POST command. variables wrapped in curly braces are
 * expanded before the request is submitted. <br>
 * ex. <br>
 * 
 * <pre>
 * &#064;Body(&quot;&lt;v01:getResourceRecordsOfZone&gt;&lt;zoneName&gt;{zoneName}&lt;/zoneName&gt;&lt;rrType&gt;0&lt;/rrType&gt;&lt;/v01:getResourceRecordsOfZone&gt;&quot;)
 * List&lt;Record&gt; listByZone(&#64;Param(&quot;zoneName&quot;) String zoneName);
 * </pre>
 * 
 * <br>
 * Note that if you'd like curly braces literally in the body, urlencode them first.
 *
 * @see RequestTemplate#expand(String, Map)
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Body {

  String value();
}
