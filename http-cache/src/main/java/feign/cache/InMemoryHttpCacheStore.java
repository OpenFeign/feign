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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Unbounded {@link HttpCacheStore} backed by a {@link ConcurrentHashMap}. Suitable for tests and
 * small deployments. Plug in a custom store backed by Caffeine, Redis, etc. for production.
 */
@Experimental
public final class InMemoryHttpCacheStore implements HttpCacheStore {

  private final ConcurrentMap<String, CachedEntry> entries = new ConcurrentHashMap<>();

  @Override
  public CachedEntry get(String key) {
    return entries.get(key);
  }

  @Override
  public void put(String key, CachedEntry entry) {
    entries.put(key, entry);
  }

  @Override
  public void invalidate(String key) {
    entries.remove(key);
  }

  public int size() {
    return entries.size();
  }
}
