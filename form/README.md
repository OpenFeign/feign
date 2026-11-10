Feign Form
==========

`FormEncoder` encodes `application/x-www-form-urlencoded` and `multipart/form-data`
request bodies.

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-form</artifactId>
  <version>${feign.version}</version>
</dependency>
```

Usage
-----

```java
Api api = Feign.builder()
    .encoder(new FormEncoder())
    .target(Api.class, "https://example.com");
```

Charset
-------

The encoding charset comes from the `charset` parameter in the first non-null
`Content-Type` header value. It is used for the encoded request body and emitted
`Content-Type`. Missing, illegal or unsupported charset names fall back to `UTF-8`.

Parameter names are case-insensitive and values may be quoted. A `charset` string inside
another quoted parameter value, including one containing escaped quotes, is not a charset
parameter. Longer names such as `xcharset` do not select the encoding either.
