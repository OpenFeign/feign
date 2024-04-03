Fastjson2 Codec
===================

This module adds support for encoding and decoding JSON via Fastjson2.

Add `Fastjson2Encoder` and/or `Fastjson2Decoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new Fastjson2Encoder())
                     .decoder(new Fastjson2Decoder())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize, provide it to the `Fastjson2Encoder` and `Fastjson2Decoder`:

```java
GitHub github = Feign.builder()
                     .encoder(new Fastjson2Encoder(new JSONWriter.Feature[]{JSONWriter.Feature.WriteNonStringValueAsString})
                     .decoder(new Fastjson2Decoder(new JSONReader.Feature[]{JSONReader.Feature.EmptyStringAsNull}))
                     .target(GitHub.class, "https://api.github.com");
```
