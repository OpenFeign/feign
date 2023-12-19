# Feign Jakarta
This module overrides annotation processing to instead use standard ones supplied by the Jakarta specification.  This is currently targeted at the 3.1 spec.

## Limitations
While it may appear possible to reuse the same interface across client and server, bear in mind that Jakarta resource
 annotations were not designed to be processed by clients. Finally, Jakarta is a large spec and attempts to implement
it completely would be a project larger than feign itself.  In other words, this implementation is *best efforts* and 
concedes far from 100% compatibility with server interface behavior.

## Currently Supported Annotation Processing
Feign only supports processing java interfaces (not abstract or concrete classes).

Here are a list of behaviors currently supported.
### Type Annotations
#### `@Path`
Appends the value to `Target.url()`.  Can have tokens corresponding to `@PathParam` annotations.
### Method Annotations
#### `@HttpMethod` meta-annotation (present on `@GET`, `@POST`, etc.)
Sets the request method.
#### `@Path`
Appends the value to `Target.url()`.  Can have tokens corresponding to `@PathParam` annotations.
#### `@Produces`
Adds all values into the `Accept` header.
#### `@Consumes`
Adds the first value as the `Content-Type` header.
### Parameter Annotations
#### `@PathParam`
Links the value of the corresponding parameter to a template variable declared in the path.
#### `@QueryParam`
Links the value of the corresponding parameter to a query parameter.  When invoked, null will skip the query param.
#### `@HeaderParam`
Links the value of the corresponding parameter to a header.
#### `@FormParam`
Links the value of the corresponding parameter to a key passed to `Encoder.Text<Map<String, Object>>.encode()`.
#### `@BeanParm`
Aggregates the above supported parameter annotations under a single value object.
