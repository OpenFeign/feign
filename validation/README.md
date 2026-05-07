Feign Validation
================

Validates the body argument and any `@Valid`-annotated parameters of a Feign-built
interface using JSR-303 / Bean Validation 2.0 (`javax.validation`) **before** the HTTP
request is dispatched. Constraint violations surface as
`javax.validation.ConstraintViolationException`.

Marked `@Experimental` while the underlying `feign.interceptor.MethodInterceptor` API stabilises.

For the Jakarta namespace, see the sibling module `feign-validation-jakarta`.

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-validation</artifactId>
  <version>${feign.version}</version>
</dependency>
```

Usage
-----

```java
Api api = Feign.builder()
    .methodInterceptor(BeanValidationMethodInterceptor.usingDefaultFactory())
    .target(Api.class, "https://example.com");
```

You can pass an explicit `Validator` and validation groups:

```java
Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
Feign.builder()
    .methodInterceptor(new BeanValidationMethodInterceptor(validator, Create.class))
    .target(Api.class, "https://example.com");
```

What gets validated
-------------------

- The method's body argument (the parameter Feign treats as `bodyIndex`) — always.
- Any other parameter annotated with `@Valid` — useful for headers, query parameters
  carrying complex objects, etc.

Null arguments are skipped. Methods with no parameters pass through.
