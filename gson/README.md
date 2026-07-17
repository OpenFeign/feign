Gson Codec
===================

This module adds support for encoding and decoding JSON via the Gson library.

Add `GsonCodec` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .codec(new GsonCodec())
                     .target(GitHub.class, "https://api.github.com");
```

You can also configure the encoder and decoder separately:

```java
GitHub github = Feign.builder()
                     .encoder(new GsonEncoder())
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Map<String, Object> and Numbers
The default constructors of `GsonEncoder` and `GsonDecoder` decoder numbers in
`Map<String, Object>` as Integer type. This prevents reading `{"counter", "1"}`
as `Map.of("counter", 1.0)`.

To change this, please use constructors that accept a Gson object.
