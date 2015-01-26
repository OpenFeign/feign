# Ribbon
This module includes a feign `Target` and `Client` adapter to take advantage of [Ribbon](https://github.com/Netflix/ribbon).

## Conventions
This integration relies on the Feign `Target.url()` being encoded like `https://myAppProd` where `myAppProd` is the ribbon client or loadbalancer name and `myAppProd.ribbon.listOfServers` configuration is set.

### RibbonClient
Adding `RibbonClient` overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by Ribbon.

#### Usage
instead of 
```java
MyService api = Feign.builder().target(MyService.class, "https://myAppProd-1234567890.us-east-1.elb.amazonaws.com");
```
do
```java
MyService api = Feign.builder().client(new RibbonClient()).target(MyService.class, "https://myAppProd");
```
### LoadBalancingTarget
Using or extending `LoadBalancingTarget` will enable dynamic url discovery via ribbon including incrementing server request counts.

#### Usage
instead of 
```java
MyService api = Feign.builder().target(MyService.class, "https://myAppProd-1234567890.us-east-1.elb.amazonaws.com");
```
do
```java
MyService api = Feign.builder().target(LoadBalancingTarget.create(MyService.class, "https://myAppProd"));
```
