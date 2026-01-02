Jackson 3 Codec
===================

This module adds support for encoding and decoding JSON via Jackson 3.

**Note:** Jackson 3 requires Java 17 or higher.

Add `Jackson3Encoder` and/or `Jackson3Decoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new Jackson3Encoder())
                     .decoder(new Jackson3Decoder())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize the `JsonMapper` that is used, provide it to the `Jackson3Encoder` and `Jackson3Decoder`:

```java
JsonMapper mapper = JsonMapper.builder()
        .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

GitHub github = Feign.builder()
                     .encoder(new Jackson3Encoder(mapper))
                     .decoder(new Jackson3Decoder(mapper))
                     .target(GitHub.class, "https://api.github.com");
```

## Migration from Jackson 2 to Jackson 3

The main differences are:

- Package changes: `com.fasterxml.jackson` → `tools.jackson` (except for `com.fasterxml.jackson.annotation`)
- GroupId changes: `com.fasterxml.jackson.core` → `tools.jackson.core`
- `ObjectMapper` is immutable and must be configured via `JsonMapper.builder()`
- Java 17 minimum requirement
