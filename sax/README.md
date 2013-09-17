Sax Decoder
===================

This module adds support for decoding xml via SAX.

Add this to your object graph like so:

```java
api = Feign.builder()
           .decoder(SAXDecoder.builder()
                              .registerContentHandler(UserIdHandler.class)
                              .build())
           .target(Api.class, "https://apihost");
```
