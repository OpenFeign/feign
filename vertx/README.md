# Feign Vertx

Implementation of Feign on Vertx. Brings you the best of two worlds together : 
concise syntax of Feign to write client side API on fast, asynchronous and
non-blocking WebClient of Vertx.

## Installation

### With Maven

```xml
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-vertx</artifactId>
    </dependency>
    ...
</dependencies>
```

### With Gradle

```groovy
compile group: 'io.github.openfeign', name: 'feign-vertx'
```

## Compatibility

Feign                  | Vertx
---------------------- | ----------------------
14.x                   | 4.x - 5.x 

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
WebClient webClient = WebClient.create(vertx);  // create Vert.x WebClient

/* Create instance of your API */
IcecreamServiceApi icecreamApi = VertxFeign
    .builder()
    .webClient(webClient) // provide WebClient instance
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .target(IcecreamServiceApi.class, "https://www.icecream.com");
    
/* Execute requests asynchronously */
Future<Collection<Flavor>> flavorsFuture = icecreamApi.getAvailableFlavors();
Future<Collection<Mixin>> mixinsFuture = icecreamApi.getAvailableMixins();
```
