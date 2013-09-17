Sax Decoder
===================

This module adds support for decoding xml via SAX.

Add this to your object graph like so:

```java
IAM iam = Feign.create(IAM.class, "https://iam.amazonaws.com", new DecodeWithSax());

--snip--
@Module(addsTo = Feign.Defaults.class)
static class DecodeWithSax {
  @Provides Decoder saxDecoder(Provider<UserIdHandler> userIdHandler) {
    return SAXDecoder.builder() //
        .addContentHandler(userIdHandler) //
        .build();
  }

  @Provides Encoder defaultEncoder() {
    return new Encoder.Default();
  }
}
```
