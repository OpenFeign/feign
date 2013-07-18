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
