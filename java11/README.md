# feign-java11

This module directs Feign's http requests to Java11 [New HTTP/2 Client](https://openjdk.java.net/jeps/321) that implements HTTP/2.

To use New HTTP/2 Client with Feign, use Java SDK 11. Then, configure Feign to use the Http2Client:

```java
GitHub github = Feign.builder()
                     .client(new Http2Client())
                     .target(GitHub.class, "https://api.github.com");
```

`Request.Options.readTimeout` applies to reading the response body as well as waiting for the
headers. It is an idle timeout: a body read that blocks for longer than `readTimeout` fails with
`HttpTimeoutException`. A `readTimeout` of 0 disables it.
