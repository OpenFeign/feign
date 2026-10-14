JDK JSON Codec
===================

This module adds support for encoding and decoding JSON via the JDK's built-in [Simple JSON API][JEP 540] (`jdk.incubator.json`).
No third party JSON library is needed.

The API is an incubator module targeted at JDK 28, so this module requires Java 28+ and the module must be resolved explicitly:

```
java --add-modules jdk.incubator.json ...
```

Incubator APIs can change incompatibly between JDK releases, so expect this module to follow along.

## Dependency

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-jdk-json</artifactId>
  <version>${feign.version}</version>
</dependency>
```

## Usage

Add `JdkJsonEncoder` and/or `JdkJsonDecoder` to your `Feign.Builder`:

```java
interface GitHub {

  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  JsonArray contributors(@Param("owner") String owner, @Param("repo") String repo);

  @RequestLine("POST /repos/{owner}/{repo}/issues")
  @Headers("Content-Type: application/json")
  JsonObject createIssue(@Param("owner") String owner, @Param("repo") String repo, JsonValue issue);
}

GitHub github = Feign.builder()
    .decoder(new JdkJsonDecoder())
    .encoder(new JdkJsonEncoder())
    .target(GitHub.class, "https://api.github.com");

for (JsonValue contributor : github.contributors("OpenFeign", "feign").asList()) {
  System.out.println(contributor.get("login").asString());
}

JsonObject issue = github.createIssue("OpenFeign", "feign", JsonObject.of(Map.of(
    "title", JsonString.of("Found a bug"),
    "labels", JsonArray.of(List.of(JsonString.of("bug"))))));
```

The JDK API has no data binding, so the codec works with the JSON tree:

* `JdkJsonDecoder` decodes into `JsonValue` or any of its subtypes (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`, `JsonNull`), or the raw `String` body.
  Malformed JSON, including duplicate member names, fails with a `DecodeException` caused by `JsonParseException`; a document of the wrong JSON type (e.g. an array for a `JsonObject` method) fails with a `DecodeException`.
  `404` (with `dismiss404()`), `204` and empty bodies decode to `null`.
* `JdkJsonEncoder` writes any `JsonValue` as compact JSON.

## Building

The module is part of the `jdk28` Maven profile and needs a JDK 28 entry in `~/.m2/toolchains.xml`:

```
./mvnw -Pjdk28 -pl jdk-json -am install
```

[JEP 540]: https://openjdk.org/jeps/540
