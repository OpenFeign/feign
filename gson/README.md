Gson Codec
===================

This module adds support for encoding and decoding JSON via the Gson library.

Add `GsonEncoder` and/or `GsonDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new GsonEncoder())
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```
