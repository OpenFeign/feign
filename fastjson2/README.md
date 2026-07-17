Fastjson2 Codec
===================

This module adds support for encoding and decoding JSON via Fastjson2.

Add `Fastjson2Codec` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .codec(new Fastjson2Codec())
                     .target(GitHub.class, "https://api.github.com");
```

You can also configure the encoder and decoder separately:

```java
GitHub github = Feign.builder()
                     .encoder(new Fastjson2Encoder())
                     .decoder(new Fastjson2Decoder())
                     .target(GitHub.class, "https://api.github.com");
```

If you want to customize, provide features to the `Fastjson2Codec`:

```java
GitHub github = Feign.builder()
                     .codec(new Fastjson2Codec(
                         new JSONWriter.Feature[]{JSONWriter.Feature.WriteNonStringValueAsString},
                         new JSONReader.Feature[]{JSONReader.Feature.EmptyStringAsNull}))
                     .target(GitHub.class, "https://api.github.com");
```
