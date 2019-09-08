Apache Http Compoments 5
========================

This module directs Feign's http requests to Apache's [HttpClient 5](https://hc.apache.org/httpcomponents-client-5.0.x/index.html).

To use ClassicHttpClient with Feign, add the `feign-hc5` module to your classpath. Then, configure Feign to use the `ClassicHttpClient`:

```java
GitHub github = Feign.builder()
                     .client(new ClassicHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```
