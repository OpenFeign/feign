### Version 7.0
* Expose reflective dispatch hook: InvocationHandlerFactory
* Add JAXB integration

### Version 6.1.1
* Fix for #85

### Version 6.1.0
* Add [SLF4J](http://www.slf4j.org/) integration

### Version 6.0.1
* Fix for BasicAuthRequestInterceptor when username and/or password are long.

### Version 6.0
* Support binary request and response bodies.
* Don't throw http status code exceptions when return type is `Response`.

### Version 5.4.0
* Add `BasicAuthRequestInterceptor`
* Add Jackson integration

### Version 5.3.0
* Split `GsonCodec` into `GsonEncoder` and `GsonDecoder`, which are easy to use with `Feign.Builder`
* Deprecate `GsonCodec`
* Update to Ribbon 0.2.3

### Version 5.2.0
* Support usage of `GsonCodec` via `Feign.Builder`

### Version 5.1.0
* Correctly handle IOExceptions wrapped by Ribbon.
* Miscellaneous findbugs fixes.

### Version 5.0.1
* `Decoder.decode()` is no longer called for `Response` or `void` types.

### Version 5.0
* Remove support for Observable methods.
* Use single non-generic Decoder/Encoder instead of sets of type-specific Decoders/Encoders.
* Decoders/Encoders are now more flexible, having access to the Response/RequestTemplate respectively.
* Moved SaxDecoder into `feign-sax` dependency.
  * SaxDecoder now decodes multiple types.
  * Remove pattern decoders in favor of SaxDecoder.
* Added Feign.Builder to simplify client customizations without using Dagger.
* Gson type adapters can be registered as Dagger set bindings.
* `Feign.create(...)` now requires specifying an encoder and decoder.

### Version 4.4.1
* Fix NullPointerException on calling equals and hashCode.

### Version 4.4
* Support overriding default HostnameVerifier.
* Support GZIP content encoding for request bodies.
* Support Iterable args for query parameters.
* Support urls which have query parameters.

### Version 4.3
* Add ability to configure zero or more RequestInterceptors.
* Remove `overrides = true` on codec modules.

### Version 4.2/3.3
* Document and enforce JAX-RS annotation processing from server POV
* Skip query template parameters when corresponding java arg is null

### Version 4.1/3.2
* update to dagger 1.1
* Add wikipedia search example
* Allow `@Path` on types in feign-jaxrs

### Version 4.0
* Support RxJava-style Observers.
  * Return type can be `Observable<T>` for an async equiv of `Iterable<T>`.
  * `Observer<T>` replaces `IncrementalCallback<T>` and is passed to `Observable.subscribe()`.
  * On `Subscription.unsubscribe()`, `Observer.onNext()` will stop being called.

### Version 3.1
* Log when an http request is retried or a response fails due to an IOException.

### Version 3.0
* Added support for asynchronous callbacks via `IncrementalCallback<T>` and `IncrementalDecoder.TextStream<T>`.
* Wire is now Logger, with configurable Logger.Level.
* Added `feign-gson` codec, used via `new GsonModule()`
* changed codec to be similar to [WebSocket JSR 356](http://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html)
  * Decoder is now `Decoder.TextStream<T>`
  * BodyEncoder is now `Encoder.Text<T>`
  * FormEncoder is now `Encoder.Text<Map<String, ?>>`
* Encoder and Decoders are specified via `Provides.Type.SET` binding.
* Default Encoder and Form Encoder is `Encoder.Text<Object>`
* Default Decoder is `Decoder.TextStream<Object>`
* ErrorDecoder now returns Exception, not fallback.
* There can only be one `ErrorDecoder` and `Request.Options` binding now. 

### Version 2.0.0
* removes guava and jax-rs dependencies
* adds JAX-RS integration

### Version 1.1.0
* adds Ribbon integration
* adds cli example
* exponential backoff customizable via Retryer.Default ctor

### Version 1.0.0

* Initial open source release
