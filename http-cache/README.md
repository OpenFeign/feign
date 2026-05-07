Feign HTTP Cache
================

A `MethodInterceptor` that performs conditional HTTP revalidation using `ETag` and
`Last-Modified` headers. On the first call, the decoded response is stored alongside
its validators. Subsequent calls send `If-None-Match` / `If-Modified-Since` headers,
and a `304 Not Modified` response short-circuits to the cached value.

Marked `@Experimental` while the underlying `feign.MethodInterceptor` API stabilises.

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-http-cache</artifactId>
  <version>${feign.version}</version>
</dependency>
```

Usage
-----

```java
HttpCacheStore store = new InMemoryHttpCacheStore();

Api api = Feign.builder()
    .methodInterceptor(new HttpCacheInterceptor(store))
    .target(Api.class, "https://example.com");
```

Plug in a different store (Caffeine, Redis, etc.) by implementing `HttpCacheStore`.

Customising
-----------

- `keyFn`: derive the cache key from an `Invocation` (e.g. include a tenant header).
- `cacheable`: decide which `RequestTemplate`s participate. The default is `GET` /
  `HEAD` only.

```java
HttpCacheInterceptor configured = new HttpCacheInterceptor(store)
    .key(invocation -> invocation.methodMetadata().configKey()
        + "|" + invocation.requestTemplate().url()
        + "|" + invocation.requestTemplate().headers().get("X-Tenant"))
    .cacheable(template -> "GET".equalsIgnoreCase(template.method()));
```

Caveats
-------

- 304 detection relies on the configured `ErrorDecoder` raising a `FeignException`
  for non-2xx responses (the default). A custom error decoder that swallows or
  transforms 304 will break cache hits.
- The store retains the **decoded** response object. Decoding happens once, on the
  first 200; on a 304 the cached object is returned as-is. Mutations on the returned
  object propagate to subsequent callers — return immutable values from your decoder
  if that matters.
- `Cache-Control` directives beyond `no-store` are not interpreted; freshness windows
  (`max-age`, etc.) are not enforced. Every cached entry triggers revalidation.
