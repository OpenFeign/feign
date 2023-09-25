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

### Map<String, Object> and Numbers
The default constructors of `MoshiEncoder` and `MoshiDecoder` decoder numbers in
`Map<String, Object>` as Integer type. This prevents reading `{"counter", "1"}`
as `Map.of("counter", 1.0)`.

To change this, please use constructors that accept a Moshi object.
