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
import feign.FeignException;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import feign.interceptor.Invocation;
import feign.interceptor.MethodInterceptor;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A {@link MethodInterceptor} that adds conditional revalidation headers ({@code If-None-Match} /
 * {@code If-Modified-Since}) to outgoing requests and, on a {@code 304 Not Modified} response,
 * returns the previously decoded value from {@link HttpCacheStore} without re-decoding.
 *
 * <p>Successful responses (2xx) carrying an {@code ETag} or {@code Last-Modified} header are
 * stored. Responses with {@code Cache-Control: no-store} are skipped.
 *
 * <p>Default scope is HTTP {@code GET} and {@code HEAD}; override via {@link #cacheable(Function)}.
 *
 * <p><b>Important:</b> 304 detection relies on the configured {@link feign.codec.ErrorDecoder}
 * raising a {@link FeignException} for non-2xx responses (the default behaviour). If a custom error
 * decoder swallows or transforms 304 responses, the cache hit path will not fire.
 */
@Experimental
public final class HttpCacheInterceptor implements MethodInterceptor {

  private static final Pattern NO_STORE = Pattern.compile("(?i)\\bno-store\\b");

  private final HttpCacheStore store;
  private final Function<Invocation, String> keyFn;
  private final Function<RequestTemplate, Boolean> cacheable;

  public HttpCacheInterceptor(HttpCacheStore store) {
    this(store, HttpCacheInterceptor::defaultKey, HttpCacheInterceptor::defaultCacheable);
  }

  private HttpCacheInterceptor(
      HttpCacheStore store,
      Function<Invocation, String> keyFn,
      Function<RequestTemplate, Boolean> cacheable) {
    this.store = store;
    this.keyFn = keyFn;
    this.cacheable = cacheable;
  }

  /** Override how cache keys are derived from an invocation. */
  public HttpCacheInterceptor key(Function<Invocation, String> keyFn) {
    return new HttpCacheInterceptor(store, keyFn, cacheable);
  }

  /** Override which requests participate in the cache. */
  public HttpCacheInterceptor cacheable(Function<RequestTemplate, Boolean> cacheable) {
    return new HttpCacheInterceptor(store, keyFn, cacheable);
  }

  @Override
  public Object intercept(Invocation invocation, Chain chain) throws Throwable {
    RequestTemplate template = invocation.requestTemplate();
    if (!Boolean.TRUE.equals(cacheable.apply(template))) {
      return chain.next(invocation);
    }
    String key = keyFn.apply(invocation);
    CachedEntry hit = store.get(key);
    addConditionalHeaders(template, hit);
    try {
      Object result = chain.next(invocation);
      maybeStore(key, result, invocation.response());
      return result;
    } catch (FeignException e) {
      if (e.status() == 304 && hit != null) {
        return hit.value();
      }
      throw e;
    }
  }

  private static void addConditionalHeaders(RequestTemplate template, CachedEntry hit) {
    if (hit == null) {
      return;
    }
    if (hit.etag() != null) {
      template.header("If-None-Match", hit.etag());
    }
    if (hit.lastModified() != null) {
      template.header("If-Modified-Since", hit.lastModified());
    }
  }

  private void maybeStore(String key, Object result, Response response) {
    if (response == null) {
      return;
    }
    int status = response.status();
    if (status < 200 || status >= 300) {
      return;
    }
    Map<String, Collection<String>> headers = response.headers();
    if (containsNoStore(headers)) {
      return;
    }
    String etag = firstHeader(headers, "ETag");
    String lastMod = firstHeader(headers, "Last-Modified");
    if (etag == null && lastMod == null) {
      return;
    }
    store.put(key, new CachedEntry(result, etag, lastMod, Instant.now()));
  }

  private static String defaultKey(Invocation invocation) {
    RequestTemplate template = invocation.requestTemplate();
    return invocation.methodMetadata().configKey() + "|" + template.method() + " " + template.url();
  }

  private static Boolean defaultCacheable(RequestTemplate template) {
    String method = template.method();
    return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
  }

  private static boolean containsNoStore(Map<String, Collection<String>> headers) {
    for (String value : Util.valuesOrEmpty(headers, "Cache-Control")) {
      if (value != null && NO_STORE.matcher(value).find()) {
        return true;
      }
    }
    return false;
  }

  private static String firstHeader(Map<String, Collection<String>> headers, String name) {
    Collection<String> values = headers.get(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.iterator().next();
  }
}
