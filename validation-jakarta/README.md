Feign Validation (Jakarta)
==========================

Validates the body argument and any `@Valid`-annotated parameters of a Feign-built
interface using Jakarta Bean Validation (`jakarta.validation`) **before** the HTTP
request is dispatched. Constraint violations surface as
`jakarta.validation.ConstraintViolationException`.

Marked `@Experimental` while the underlying `feign.interceptor.MethodInterceptor` API stabilises.

For the legacy `javax.validation` namespace, use the sibling `feign-validation` module.

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-validation-jakarta</artifactId>
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
