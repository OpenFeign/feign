JSON-java Codec
===================

This module adds support for encoding and decoding [JSON][] via [JSON-java][].

Add `JsonEncoder` and/or `JsonDecoder` to your `Feign.Builder` like so:

```java
api = Feign.builder()
           .decoder(new JsonDecoder())
           .encoder(new JsonEncoder())
           .target(GitHub.class, "https://api");
```

[JSON]: https://www.json.org/json-en.html
[JSON-java]: https://github.com/stleary/JSON-java
