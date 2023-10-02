Moshi Codec
===================

This module adds support for encoding and decoding JSON via the Moshi library.

Add `MoshiEncoder` and/or `MoshiDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new MoshiEncoder())
                     .decoder(new MoshiDecoder())
                     .target(GitHub.class, "https://api.github.com");
```
