Gson Codec
===================

This module adds support for encoding and decoding json via the Gson library.

Add this to your object graph like so:

```java
GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());
```
