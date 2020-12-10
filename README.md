# feign-vertx

[![Build Status](https://travis-ci.org/OpenFeign/feign-vertx.svg?branch=master)](https://travis-ci.org/OpenFeign/feign-vertx)
[![Download](https://api.bintray.com/packages/hosuaby/OpenFeign/feign-vertx/images/download.svg)](https://bintray.com/hosuaby/OpenFeign/feign-vertx/_latestVersion)

Implementation of Feign on Vertx. Brings you the best of two worlds together : 
concise syntax of Feign to write client side API on fast, asynchronous and
non-blocking HTTP client of Vertx.

## Installation

### With Maven

```xml
<repositories>
    <repository>
        <id>jcenter</id>
        <url>https://jcenter.bintray.com/</url>
    </repository>
</repositories>
...
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-vertx</artifactId>
        <version>2.0.0</version>
    </dependency>
    ...
</dependencies>
```

### With Gradle

```groovy
repositories {
    jcenter()
}

compile group: 'io.github.openfeign', name: 'feign-vertx', version: '2.0.0'
```

## Compatibility

Feign                  | feign-vertx            | Vertx
---------------------- | ---------------------- | ----------------------
8.x                    | 1.x+                   | 3.5.x - 3.9.x (except 3.5.2)
9.x                    | 2.x+                   | 3.5.x - 3.9.x (except 3.5.2)
10.x (except 10.5.0)   | 3.x+                   | 3.5.x - 3.9.x (except 3.5.2)

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
