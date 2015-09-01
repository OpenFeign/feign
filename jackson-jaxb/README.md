Jackson-Jaxb Codec
===================

This module adds support for encoding and decoding JSON via JAXB.

Add `JacksonJaxbJsonEncoder` and/or `JacksonJaxbJsonDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new JacksonJaxbJsonEncoder())
                     .decoder(new JacksonJaxbJsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize the `ObjectMapper` that is used, provide it to the `JacksonJaxbJsonEncoder` and `JacksonJaxbJsonDecoder`:

```java
ObjectMapper mapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

GitHub github = Feign.builder()
                     .encoder(new JacksonJaxbJsonEncoder(mapper))
                     .decoder(new JacksonJaxbJsonDecoder(mapper))
                     .target(GitHub.class, "https://api.github.com");
```
