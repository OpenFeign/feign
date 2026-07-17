Jackson Jr Codec
===================

This module adds support for encoding and decoding JSON via Jackson Jr. This
is a significantly smaller and faster version of Jackson, which may be of benefit
in smaller runtime containers.

Add `JacksonJrEncoder` and/or `JacksonJrDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new JacksonJrEncoder())
                     .decoder(new JacksonJrDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize the `JSON` object that is used, provide it to the `JacksonJrEncoder` and `JacksonJrDecoder`:

```java
JSON json = Json.builder()
                .register(JacksonAnnotationExtension.builder()
                    .withVisibility(JsonAutoDetect.Value.defaultVisibility())
                    .build())
                .build();

GitHub github = Feign.builder()
                     .encoder(new JacksonJrEncoder(json))
                     .decoder(new JacksonJrDecoder(json))
                     .target(GitHub.class, "https://api.github.com");
```

Customisation is also possible by passing `JacksonJrExtension` objects
into the constructor of the `JacksonJrEncoder` or `JacksonJrDecoder`:

```java
List<JacksonJrExtension> extensions = singletonList(JacksonAnnotationExtension.builder()
        .withVisibility(JsonAutoDetect.Value.defaultVisibility())
        .build());
GitHub github = Feign.builder()
                     .encoder(new JacksonJrEncoder(extensions))
                     .decoder(new JacksonJrDecoder(extensions))
                     .target(GitHub.class, "https://api.github.com");
```
