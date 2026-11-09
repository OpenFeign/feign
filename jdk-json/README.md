JDK JSON Codec
===================

This module lets you encode and decode JSON using the JDK's built-in [Simple JSON API][JEP 540] (`jdk.incubator.json`).
You do not need to use any third party JSON libraries.

This API is an incubator module for JDK 28, so this module requires Java 28 or higher. You also must resolve the module explicitly:

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

* `JdkJsonDecoder` decodes into `JsonValue` or any of its subtypes (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`, `JsonNull`). It can also return the raw `String` body.
  If the JSON is malformed (like having duplicate member names), it throws a `DecodeException` caused by a `JsonParseException`. If the JSON type is wrong (for example, you get an array but asked for a `JsonObject`), it throws a `DecodeException`.
  `404` (if you use `dismiss404()`), `204`, and empty bodies decode to `null`.
* `JdkJsonEncoder` writes any `JsonValue` as compact JSON.

## Building

The module is part of the `jdk28-modules` Maven profile and needs a JDK 28 entry in `~/.m2/toolchains.xml`:

```
./mvnw -Pjdk28-modules -pl jdk-json -am install
```

[JEP 540]: https://openjdk.org/jeps/540
