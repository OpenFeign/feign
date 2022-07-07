Apache Http Components 5
========================

This module directs Feign's http requests to Apache's [HttpClient 5](https://hc.apache.org/httpcomponents-client-5.0.x/index.html).

To use HttpClient with Feign, add the `feign-hc5` module to your classpath. Then, configure Feign to use the `ApacheHttp5Client`:

```java
GitHub github = Feign.builder()
                     .client(new ApacheHttp5Client())
                     .target(GitHub.class, "https://api.github.com");
```
