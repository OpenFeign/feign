# Google Http Client - Feign Client

This library is a feign [Client](https://github.com/OpenFeign/feign/blob/master/core/src/main/java/feign/Client.java) to use the java [Google Http Client](https://github.com/googleapis/google-http-java-client).

To use this, add to your classpath (via maven, or otherwise). Then cofigure Feign to use the GoogleHttpClient:

```java
GitHub github = Feign.builder()
                     .client(new GoogleHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```
