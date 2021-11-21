# feign-java11

This module directs Feign's http requests to Java11 [New HTTP/2 Client](https://openjdk.java.net/jeps/321) that implements HTTP/2.

To use New HTTP/2 Client with Feign, use Java SDK 11. Then, configure Feign to use the Http2Client:

```java
GitHub github = Feign.builder()
                     .client(new Http2Client())
                     .target(GitHub.class, "https://api.github.com");
```
