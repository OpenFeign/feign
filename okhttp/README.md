OkHttp
===================

This module directs Feign's http requests to [OkHttp](http://square.github.io/okhttp/), which enables SPDY and better network control.

To use OkHttp with Feign, add the OkHttp module to your classpath. Then, configure Feign to use the OkHttpClient:

```java
GitHub github = Feign.builder()
                     .client(new OkHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```
