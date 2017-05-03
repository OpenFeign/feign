Annotation Error Decoder
=========================

This module allows to annotate Feign's interfaces with annotations to generate Exceptions based on error codes

To use AnnotationErrorDecoder with Feign, add the Annotation Error Decoder module to your classpath. Then, configure
Feign to use the AnnotationErrorDecoder:

```java
GitHub github = Feign.builder()
                     .client(new ApacheHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```
