Moshi Codec
===================

This module adds support for encoding and decoding JSON via the Moshi library.

Add `MoshiCodec` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .codec(new MoshiCodec())
                     .target(GitHub.class, "https://api.github.com");
```

You can also configure the encoder and decoder separately:

```java
GitHub github = Feign.builder()
                     .encoder(new MoshiEncoder())
                     .decoder(new MoshiDecoder())
                     .target(GitHub.class, "https://api.github.com");
```
