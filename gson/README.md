Gson Codec
===================

This module adds support for encoding and decoding json via the Gson library.

Add `GsonCodec` to your `Feign.Builder` like so:

```java
GsonCodec codec = new GsonCodec();
GitHub github = Feign.builder()
                     .encoder(codec)
                     .decoder(codec)
                     .target(GitHub.class, "https://api.github.com");
```

Or.. to your object graph like so:

```java
GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());
```
