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
package feign;

import java.util.Arrays;

import static feign.Util.checkNotNull;
import static feign.Util.emptyToNull;

/**
 * <br><br><b>relationship to JAXRS 2.0</b><br>
 * <br>
 * Similar to {@code javax.ws.rs.client.WebTarget}, as it produces requests.
 * However, {@link RequestTemplate} is a closer match to {@code WebTarget}.
 *
 * @param <T> type of the interface this target applies to.
 */
public interface Target<T> {
  /* The type of the interface this target applies to. ex. {@code Route53}. */
  Class<T> type();

  /* configuration key associated with this target. For example, {@code route53}. */
  String name();

  /* base HTTP URL of the target. For example, {@code https://api/v2}. */
  String url();

  /**
   * Targets a template to this target, adding the {@link #url() base url} and
   * any target-specific headers or query parameters.
   * <br>
   * <br>
   * For example:
   * <br>
   * <pre>
   * public Request apply(RequestTemplate input) {
   *     input.insert(0, url());
   *     input.replaceHeader(&quot;X-Auth&quot;, currentToken);
   *     return input.asRequest();
   * }
   * </pre>
   * <br>
   * <br><br><b>relationship to JAXRS 2.0</b><br>
   * <br>
   * This call is similar to {@code javax.ws.rs.client.WebTarget.request()},
   * except that we expect transient, but necessary decoration to be applied
   * on invocation.
   */
  public Request apply(RequestTemplate input);

  public static class HardCodedTarget<T> implements Target<T> {
    private final Class<T> type;
    private final String name;
    private final String url;

    public HardCodedTarget(Class<T> type, String url) {
      this(type, url, url);
    }

    public HardCodedTarget(Class<T> type, String name, String url) {
      this.type = checkNotNull(type, "type");
      this.name = checkNotNull(emptyToNull(name), "name");
      this.url = checkNotNull(emptyToNull(url), "url");
    }

    @Override public Class<T> type() {
      return type;
    }

    @Override public String name() {
      return name;
    }

    @Override public String url() {
      return url;
    }

    /* no authentication or other special activity. just insert the url. */
    @Override public Request apply(RequestTemplate input) {
      if (input.url().indexOf("http") != 0)
        input.insert(0, url());
      return input.request();
    }

    @Override public int hashCode() {
      return Arrays.hashCode(new Object[]{type, name, url});
    }

    @Override public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (HardCodedTarget.class != obj.getClass())
        return false;
      HardCodedTarget<?> that = HardCodedTarget.class.cast(obj);
      return this.type.equals(that.type) && this.name.equals(that.name) && this.url.equals(that.url);
    }
  }
}
