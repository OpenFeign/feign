JAXB Codec
===================

This module adds support for encoding and decoding XML via JAXB.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
Response response = Feign.builder()
                     .encoder(new JacksonEncoder())
                     .decoder(new JacksonDecoder())
                     .target(Response.class, "https://api.test.com");
```