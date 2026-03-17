Jackson Codec
===================

This module adds support for encoding and decoding JSON via Jackson.

Add `JacksonCodec` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .codec(new JacksonCodec())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize the `ObjectMapper` that is used, provide it to the `JacksonCodec`:

```java
ObjectMapper mapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

GitHub github = Feign.builder()
                     .codec(new JacksonCodec(mapper))
                     .target(GitHub.class, "https://api.github.com");
```

You can also configure the encoder and decoder separately:

```java
GitHub github = Feign.builder()
                     .encoder(new JacksonEncoder())
                     .decoder(new JacksonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```
