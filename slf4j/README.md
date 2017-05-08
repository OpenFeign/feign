SLF4J
===================

This module allows directing Feign's logging to [SLF4J](http://www.slf4j.org/), allowing you to easily use a logging backend of your choice (Logback, Log4J, etc.)

To use SLF4J with Feign, add both the SLF4J module and an SLF4J binding of your choice to your classpath.  Then, configure Feign to use the Slf4jLogger:

```java
GitHub github = Feign.builder()
                     .logger(new Slf4jLogger())
                     .target(GitHub.class, "https://api.github.com");
```


## Tab-separated key-value SLF4J logger

To use more compact version of slf4j logger implementation, you can add:

```
// ...
.logger(new Slf4jTskvLogger())
// ...
```

It very close to `Slf4jLogger`, but writes logs in format:

```
DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]   call=[someMethod()]	method=[GET]	uri=[http://api.example.com]
DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]	status=[200]	reason=[OK] elapsed-ms=[273]	length=[0]

```

Then you can grep such logs with `| cut -f 3` (with right column number)

Also this logger adds unique `req-id` string to merge request and response.
All retries and error log lines have such string too.
This string is changed each request.
