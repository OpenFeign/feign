# feign-vertx

[![CI](https://github.com/OpenFeign/feign-vertx/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/OpenFeign/feign-vertx/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/hosuaby/vertx-feign/badge.svg?branch=master)](https://coveralls.io/github/hosuaby/vertx-feign?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-vertx/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-vertx)
[![javadoc](https://javadoc.io/badge2/io.github.openfeign/feign-vertx/javadoc.svg)](https://javadoc.io/doc/io.github.openfeign/feign-vertx)

Implementation of Feign on Vertx. Brings you the best of two worlds together : 
concise syntax of Feign to write client side API on fast, asynchronous and
non-blocking HTTP client of Vertx.

## Installation

### With Maven

```xml
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-vertx</artifactId>
        <version>5.1.0</version>
    </dependency>
    ...
</dependencies>
```

### With Gradle

```groovy
compile group: 'io.github.openfeign', name: 'feign-vertx', version: '5.1.0'
```

## Compatibility

Feign                  | feign-vertx | Vertx
---------------------- |-------------| ----------------------
8.x                    | 1.x+        | 3.5.x - 3.9.x (except 3.5.2)
9.x                    | 2.x+        | 3.5.x - 3.9.x (except 3.5.2)
10.x (except 10.5.0)   | 3.x+        | 3.5.x - 3.9.x (except 3.5.2)
11.x                   | 4.x+        | 3.5.x - 3.9.x (except 3.5.2)
11.x                   | 5.x+        | 4.x
12.x - 13.x            | 6.x+        | 4.x

## Usage

Write Feign API as usual, but every method of interface must return
`io.vertx.core.Future`.

```java
@Headers({ "Accept: application/json" })
interface IcecreamServiceApi {

  @RequestLine("GET /icecream/flavors")
  Future<Collection<Flavor>> getAvailableFlavors();

  @RequestLine("GET /icecream/mixins")
  Future<Collection<Mixin>> getAvailableMixins();

  @RequestLine("POST /icecream/orders")
  @Headers("Content-Type: application/json")
  Future<Bill> makeOrder(IceCreamOrder order);

  @RequestLine("GET /icecream/orders/{orderId}")
  Future<IceCreamOrder> findOrder(@Param("orderId") int orderId);
  
  @RequestLine("POST /icecream/bills/pay")
  @Headers("Content-Type: application/json")
  Future<Void> payBill(Bill bill);
}
```
Build the client :

```java
Vertx vertx = Vertx.vertx();  // get Vertx instance

/* Create instance of your API */
IcecreamServiceApi icecreamApi = VertxFeign
    .builder()
    .vertx(vertx) // provide vertx instance
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .target(IcecreamServiceApi.class, "http://www.icecreame.com");
    
/* Execute requests asynchronously */
Future<Collection<Flavor>> flavorsFuture = icecreamApi.getAvailableFlavors();
Future<Collection<Mixin>> mixinsFuture = icecreamApi.getAvailableMixins();
```

## License

Library distributed under Apache License Version 2.0.
