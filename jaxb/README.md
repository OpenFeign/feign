JAXB Codec
===================

This module adds support for encoding and decoding XML via JAXB.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
    .withMarshallerJAXBEncoding("UTF-8")
    .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
    .build();

Response response = Feign.builder()
                         .encoder(new JAXBEncoder(jaxbFactory))
                         .decoder(new JAXBDecoder(jaxbFactory))
                         .target(Response.class, "https://apihost");
```

`JAXBDecoder` can also be created with a builder to allow overriding some default parser options:

```java
JAXBDecoder jaxbDecoder = new JAXBDecoder.Builder()
    .withJAXBContextFactory(jaxbFactory)
    .withNamespaceAware(false) // true by default
    .build();
```

Usage
===================

The feign library is available from [Maven Central](https://mvnrepository.com/artifact/io.github.openfeign/feign-jaxb).

```xml
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-jaxb</artifactId>
    <version>??feing-jaxb-version??</version>
</dependency>

```
