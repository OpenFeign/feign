# Migration Guide — Feign v14 (Request Body Streaming)

This guide covers the breaking changes introduced in #3360 and explains how to update your code.

> **Target release:** `v14.rc.1`

---

## Overview

`feign.Request.Body` has been redesigned from a `byte[]`-backed concrete class into a **streaming-ready interface**.
Request bodies are no longer eagerly buffered in memory unless you explicitly use the `byte[]`/`String` factory methods.

For most users, regular Feign usage via interface annotations and `Feign.builder().target(...)` is unchanged.
The **breaking changes** primarily affect code that interacts directly with request body internals, including:

- Custom `Encoder` implementations
- Custom `Client` implementations
- Any code that directly reads `Request.body()`, `Request.length()`, `Request.charset()`, or `RequestTemplate.body()`/
  `RequestTemplate.requestBody()`

---

## Breaking Changes

### 1. `Request.Body` is now an interface

**Before:**

```java
// Body was a concrete class with public fields/methods
Request.Body body = Request.Body.create("hello", StandardCharsets.UTF_8);
byte[] bytes = body.asBytes();
String str = body.asString();
int len = body.length();
Optional<Charset> charset = body.getEncoding();
boolean binary = body.isBinary();
```

**After:**

```java
// Body is now an interface — use the factory methods
Request.Body body = Request.Body.of("hello", StandardCharsets.UTF_8);

// To read content, write it to a stream (note: these methods throw checked IOException):
byte[] bytes = body.writeToByteArray();
String str = body.writeToString(StandardCharsets.UTF_8);
long len = body.contentLength(); // -1 if unknown/streaming
boolean repeatable = body.isRepeatable();
```

**Removed methods on `Request.Body`:**

| Removed                         | Replacement                                                                           |
|---------------------------------|---------------------------------------------------------------------------------------|
| `Body.create(String)`           | `Body.of(String)`                                                                     |
| `Body.create(String, Charset)`  | `Body.of(String, Charset)`                                                            |
| `Body.create(byte[])`           | `Body.of(byte[])`                                                                     |
| `Body.create(byte[], Charset)`  | `Body.of(byte[], Charset)`                                                            |
| `Body.encoded(byte[], Charset)` | `Body.of(byte[], Charset)`                                                            |
| `Body.empty()`                  | Pass `null` for no body                                                               |
| `body.asBytes()`                | `body.writeToByteArray()`                                                             |
| `body.asString()`               | `body.writeToString(charset)`                                                         |
| `body.length()` → `int`         | `body.contentLength()` → `long` (returns `-1` if unknown)                             |
| `body.getEncoding()`            | Read charset from `Content-Type` header                                               |
| `body.isBinary()`               | No direct replacement; `body.isRepeatable()` may be useful depending on your use case |

---

### 2. `Request.body()` now returns `Optional<Request.Body>`

**Before:**

```java
byte[] body = request.body(); // nullable byte[]
if (body != null) {
    out.write(body);
}
```

**After:**

```java
// writeTo(OutputStream) throws checked IOException, so a plain Consumer lambda
// (as used in Optional.ifPresent) cannot propagate it. Use an explicit if-block instead.
Optional<Request.Body> body = request.body();
if (body.isPresent()) {
    body.get().writeTo(out);
}
```

---

### 3. `Request.length()` removed

**Before:**

```java
int length = request.length();
```

**After:**

```java
// contentLength() does not throw, so Optional.map is safe here.
long length = request.body()
    .map(Request.Body::contentLength)
    .orElse(0L);
```

---

### 4. `Request.charset()` removed

**Before:**

```java
Charset charset = request.charset();
```

**After:** Read the charset from the `Content-Type` request header. There is no longer a charset field on `Request`
itself.

---

### 5. `Request.isBinary()` removed

**Before:**

```java
boolean binary = request.isBinary();
```

**After:**

```java
// There is no direct replacement for request.isBinary().
// Depending on why you were checking it, request body repeatability may be useful:
boolean repeatable = request.body()
    .map(Request.Body::isRepeatable)
    .orElse(false);
```

---

### 6. `Request.create(...)` overloads removed

The `byte[]` + `Charset`-based `Request.create(...)` overloads have been removed.

**Before:**

```java
Request.create(HttpMethod.GET, url, headers, bodyBytes, charset);
Request.create(HttpMethod.GET, url, headers, bodyBytes, charset, requestTemplate);
// Deprecated String-based variant:
Request.create("GET", url, headers, bodyBytes, charset);
```

**After:**

```java
// With a body:
Request.create(HttpMethod.GET, url, headers, Request.Body.of(bodyBytes), requestTemplate);

// Without a body:
Request.create(HttpMethod.GET, url, headers, null, null);
```

---

### 7. `RequestTemplate` API changes

#### `RequestTemplate.body(String)` removed

**Before:**

```java
template.body("hello world");
```

**After:**

```java
template.body(Request.Body.of("hello world"));
```

#### `RequestTemplate.body(byte[], Charset)` deprecated

**Before:**

```java
template.body(bytes, StandardCharsets.UTF_8);
```

**After:**

```java
template.body(Request.Body.of(bytes, StandardCharsets.UTF_8));
// or, if charset is irrelevant for your use case:
template.body(Request.Body.of(bytes));
```

#### `RequestTemplate.body()` (returns `byte[]`) removed

**Before:**

```java
byte[] body = template.body();
```

**After:**

```java
// writeToByteArray() throws checked IOException, so a plain Function lambda
// (as used in Optional.map) cannot propagate it. Use an explicit if-block instead.
byte[] body = null;
Optional<Request.Body> requestBody = template.requestBody();
if (requestBody.isPresent()) {
    body = requestBody.get().writeToByteArray();
}
```

#### `RequestTemplate.requestBody()` is no longer `@Deprecated`

The method now returns `Optional<Request.Body>` and is the primary accessor.

#### `RequestTemplate.requestCharset()` removed

**Before:**

```java
Charset charset = template.requestCharset();
```

**After:** Read charset from the `Content-Type` header. There is no longer a charset tracked on the template itself.

---

### 8. Custom `Encoder` implementations

If you implement a custom `Encoder`, update calls to `template.body(...)`:

**Before:**

```java
template.body(serialized.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
// or
template.body(serialized);
```

**After:**

```java
template.body(Request.Body.of(serialized, StandardCharsets.UTF_8));
// or, for UTF-8 strings:
template.body(Request.Body.of(serialized));
```

---

### 9. Custom `Client` implementations

If you implement a custom `Client`, update how you write the request body:

**Before:**

```java
byte[] body = request.body();
if (body != null) {
    outputStream.write(body);
}
```

**After:**

```java
// writeTo(OutputStream) throws checked IOException, so a plain Consumer lambda
// (as used in Optional.ifPresent) cannot propagate it. Use an explicit if-block instead.
Optional<Request.Body> body = request.body();
if (body.isPresent()) {
    body.get().writeTo(outputStream);
}
```

For retry-capable clients, check `body.isRepeatable()` before attempting a retry — non-repeatable (streaming) bodies
cannot be re-sent.

---

### 10. `FeignException.errorReading(...)` — request body no longer captured

`FeignException` no longer captures the request body when a read error occurs, because the body may be a non-repeatable
stream. Code asserting `exception.contentUTF8()` returns the request body must be updated:

**Before:**

```java
assertThat(exception.contentUTF8()).isEqualTo("Request body");
```

**After:**

```java
assertThat(exception.contentUTF8()).isEmpty();
```

---

### 11. `mock` module — `RequestKey` no longer stores `Charset`

**Before:**

```java
RequestKey.builder(...).charset(StandardCharsets.UTF_8).build();
```

**After:** The `charset(Charset)` method has been removed from `RequestKey.Builder`. Remove it from your mock setup
code.

---

### 12. `Request.Body` no longer implements `Serializable`

`feign.Request.Body` previously implemented `java.io.Serializable`. This has been removed.
If you were serializing `Request.Body` objects (e.g., for caching or distributed tracing), you will need an alternative
serialization strategy.

---

### 13. Vert.x integration — `VertxFeign.Builder` now requires `.vertx(Vertx)`

**Before:**

```java
VertxFeign.builder()
    .webClient(webClient)
    .target(MyApi.class, url);
```

**After:**

```java
VertxFeign.builder()
    .vertx(vertx)           // required — NPE with descriptive message if missing
    .webClient(webClient)
    .target(MyApi.class, url);
```

---

## Implementing a Custom Streaming Body

If you want to stream a body (e.g., from a file or `InputStream`), implement `Request.Body` directly. Because
`writeTo(OutputStream)` itself declares `throws IOException`, the lambda **can** propagate it freely — the restriction
only applies to standard functional interfaces (`Consumer`, `Function`, etc.) that don't
declare checked exceptions.

```java
// One-shot InputStream — non-repeatable (isRepeatable() defaults to false)
Request.Body streamingBody = outputStream -> {
    try (InputStream in = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        // or, with Java 9+:
        // in.transferTo(outputStream);
    }
    // IOException propagates naturally — no try-catch needed here
};
template.body(streamingBody);
```

For a repeatable streaming body (e.g., backed by a file that can be re-read):

```java
public class FileBody implements Request.Body {
    private final Path path;

    public FileBody(Path path) {
        this.path = path;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            // or, with Java 9+:
            // in.transferTo(out);
        }
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long contentLength() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return super.contentLength(); // returns -1
        }
    }
}
```

---

## Spring Cloud OpenFeign Compatibility

`RequestTemplate#body(byte[], Charset)` is kept `@Deprecated` for backward compatibility with
`spring-cloud-openfeign-core`. Spring Cloud OpenFeign users are not required to make any changes immediately, but should
migrate to `body(Request.Body)` once the Spring team provides an updated release.
