SOAP Codec
===================

This module adds support for encoding and decoding SOAP Body objects via JAXB and SOAPMessage. It also provides SOAPFault decoding capabilities by wrapping them into the original `javax.xml.ws.soap.SOAPFaultException`, so that you'll only need to catch `SOAPFaultException` in order to handle SOAPFault.

Add `SOAPEncoder` and/or `SOAPDecoder` to your `Feign.Builder` like so:

```java
public interface MyApi {
 
    @RequestLine("POST /getObject")
    @Headers({
      "SOAPAction: getObject",
      "Content-Type: text/xml"
    })
    MyJaxbObjectResponse getObject(MyJaxbObjectRequest request);
    
 }

 ...

 JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
     .withMarshallerJAXBEncoding("UTF-8")
     .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
     .build();

 api = Feign.builder()
     .encoder(new SOAPEncoder(jaxbFactory))
     .decoder(new SOAPDecoder(jaxbFactory))
     .target(MyApi.class, "http://api");

 ...

 try {
    api.getObject(new MyJaxbObjectRequest());
 } catch (SOAPFaultException faultException) {
    log.info(faultException.getFault().getFaultString());
 }
 
```

Because a SOAP Fault can be returned as well with a 200 http code than a 4xx or 5xx HTTP error code (depending on the used API), you may also use `SOAPErrorDecoder` in your API configuration, in order to be able to catch `SOAPFaultException` in case of SOAP Fault. Add it, like below:

```java
api = Feign.builder()
     .encoder(new SOAPEncoder(jaxbFactory))
     .decoder(new SOAPDecoder(jaxbFactory))
     .errorDecoder(new SOAPErrorDecoder())
     .target(MyApi.class, "http://api");
```

In certain situations the declarations on the SOAP envelope are not inherited by JAXB when reading the documents.  This is particularly
troublesome when it is not possible to correct the XML at the source.

To account for this situation, use the `useFirstChild` option on the `SOAPDecoder` builder.  This will instruct JAX be to use `SOAPBody#getFirstChild()`
instead of `SOAPBody#extractContentAsDocument()`.  This will allow users to supply a `package-info.java` to manage the element namespaces
explicitly and define what should occur if the namespace declarations are missing.