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
package feign.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RequestHeaders {

  public static class Builder {

    private Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();

    private Builder() {}

    public Builder add(String key, Collection<String> values) {
      if (!headers.containsKey(key)) {
        headers.put(key, values);
      } else {
        Collection<String> previousValues = headers.get(key);
        previousValues.addAll(values);
        headers.put(key, previousValues);
      }
      return this;
    }

    public Builder add(String key, String value) {
      if (!headers.containsKey(key)) {
        headers.put(key, new ArrayList<String>(Arrays.asList(value)));
      } else {
        final Collection<String> values = headers.get(key);
        values.add(value);
        headers.put(key, values);
      }
      return this;
    }

    public RequestHeaders build() {
      return new RequestHeaders(this);
    }

  }

  public static final Map<String, Collection<String>> EMPTY = Collections.emptyMap();

  public static Builder builder() {
    return new Builder();
  }

  public static RequestHeaders of(Map<String, Collection<String>> headers) {
    return new RequestHeaders(headers);
  }

  private Map<String, Collection<String>> headers;

  private RequestHeaders(Builder builder) {
    this.headers = builder.headers;
  }

  private RequestHeaders(Map<String, Collection<String>> headers) {
    this.headers = headers;
  }

  public int size() {
    return headers.size();
  }

  public int sizeOf(String key) {
    if (!headers.containsKey(key)) {
      return 0;
    }
    return headers.get(key).size();
  }

  public Collection<String> fetch(String key) {
    return headers.get(key);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final RequestHeaders other = (RequestHeaders) obj;
    return this.headers.equals(other.headers);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
      builder.append(entry).append(',').append(' ');
    }
    if (builder.length() > 0) {
      return builder.substring(0, builder.length() - 2);
    }
    return "no";
  }

}
