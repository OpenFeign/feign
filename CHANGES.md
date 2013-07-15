### Version 3.0
* Wire is now Logger, with configurable Logger.Level.
* decoupled ErrorDecoder from fallback handling
* Decoders can throw checked exceptions, but needn't declare Throwable
* Decoders no longer read methodKey

### Version 2.0.0
* removes guava and jax-rs dependencies
* adds JAX-RS integration

### Version 1.1.0
* adds Ribbon integration
* adds cli example
* exponential backoff customizable via Retryer.Default ctor

### Version 1.0.0

* Initial open source release
