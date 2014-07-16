JAXB Codec
===================

This module adds support for encoding and decoding XML via JAXB.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
//The context factory should be reused across requests.  Recreating it will be a performance
//penalty as it has to recreate the JAXBContext
JAXBContextFactory ctxFactory = new JAXBContextFactory(); 

Response response = Feign.builder()
                     .encoder(new JAXBEncoder(ctxFactory))
                     .decoder(new JAXBDecoder(ctxFactory))
                     .target(Response.class, "https://api.test.com");
```

Alternatively, you can add the encoder and decoder to your Dagger object graph using the provided JAXBModule like so:

```java
Response response = Feign.create(Response.class, "https://api.test.com", new JAXBModule());
```