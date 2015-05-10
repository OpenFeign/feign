Apache HttpClient
===================

This module directs Feign's http requests to Apache's [HttpClient](https://hc.apache.org/httpcomponents-client-ga/).

To use HttpClient with Feign, add the HttpClient module to your classpath. Then, configure Feign to use the HttpClient:

```java
GitHub github = Feign.builder()
                     .client(new ApacheHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```
