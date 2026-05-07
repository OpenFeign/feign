/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.cache;

import feign.Experimental;
import java.time.Instant;

/** Immutable cache record produced from a successful response with revalidation headers. */
@Experimental
public final class CachedEntry {

  private final Object value;
  private final String etag;
  private final String lastModified;
  private final Instant storedAt;

  public CachedEntry(Object value, String etag, String lastModified, Instant storedAt) {
    this.value = value;
    this.etag = etag;
    this.lastModified = lastModified;
    this.storedAt = storedAt;
  }

  public Object value() {
    return value;
  }

  public String etag() {
    return etag;
  }

  public String lastModified() {
    return lastModified;
  }

  public Instant storedAt() {
    return storedAt;
  }
}
