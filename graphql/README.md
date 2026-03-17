Feign GraphQL
===================

This module adds support for declarative GraphQL clients using Feign. It provides a `GraphqlContract`, `GraphqlEncoder`, and `GraphqlDecoder` that transform annotated interfaces into fully functional GraphQL clients.

The companion module `feign-graphql-apt` provides compile-time type generation from GraphQL schemas, producing Java records for query results and input types.

## Dependencies

Add both modules to use schema-driven type generation:

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-graphql</artifactId>
  <version>${feign.version}</version>
</dependency>

<dependency>
  <groupId>io.github.openfeign.experimental</groupId>
  <artifactId>feign-graphql-apt</artifactId>
  <version>${feign.version}</version>
  <scope>provided</scope>
</dependency>
```

## Basic Usage

Define a GraphQL schema in `src/main/resources`:

```graphql
type Query {
  user(id: ID!): User
}

type User {
  id: ID!
  name: String!
  email: String
}
```

Annotate your Feign interface with `@GraphqlSchema` pointing to the schema file and `@GraphqlQuery` on each method with the GraphQL query string:

```java
@GraphqlSchema("my-schema.graphql")
interface UserApi {

  @GraphqlQuery("query { user(id: $id) { id name email } }")
  User getUser(@Param("id") String id);
}
```

The annotation processor generates a Java record for `User` at compile time:

```java
public record User(String id, String name, String email) {}
```

Build the client using `GraphqlCapability`, which wires the contract, encoder, decoder, and request interceptor automatically. It takes any Feign `Encoder` and any `JsonDecoder` — all existing Feign decoders implement it:

```java
// Using Jackson
UserApi api = Feign.builder()
    .addCapability(new GraphqlCapability(new JacksonEncoder(), new JacksonDecoder()))
    .target(UserApi.class, "https://api.example.com/graphql");

User user = api.getUser("123");
```

Any JSON encoder/decoder pair works the same way:

```java
// Gson
new GraphqlCapability(new GsonEncoder(), new GsonDecoder())

// Jackson 3
new GraphqlCapability(new Jackson3Encoder(), new Jackson3Decoder())

// Fastjson2
new GraphqlCapability(new Fastjson2Encoder(), new Fastjson2Decoder())
```

## Mutations with Variables

Methods with parameters are sent as GraphQL variables:

```java
@GraphqlSchema("my-schema.graphql")
interface UserApi {

  @GraphqlQuery("mutation($input: CreateUserInput!) { createUser(input: $input) { id name } }")
  User createUser(@Param("input") CreateUserInput input);
}
```

The processor generates a record for the input type as well:

```java
public record CreateUserInput(String name, String email) {}
```

## Custom Scalars

When your schema defines custom scalars, map them to Java types using `@Scalar` on default methods:

```graphql
scalar DateTime

type Event {
  id: ID!
  name: String!
  startTime: DateTime!
}
```

```java
@GraphqlSchema("event-schema.graphql")
interface EventApi {

  @Scalar("DateTime")
  default Instant dateTime() { return null; }

  @GraphqlQuery("query { events { id name startTime } }")
  List<Event> getEvents();
}
```

The processor maps `DateTime` fields to `java.time.Instant` in the generated record:

```java
public record Event(String id, String name, Instant startTime) {}
```

## Single Result from Array Queries

When a GraphQL query returns an array type (e.g. `[User!]`) but the Java method declares a single return type, the decoder automatically unwraps the first element:

```java
@GraphqlQuery("query topUser($limit: Int!) { topUsers(limit: $limit) { id name email } }")
User topUser(int limit);
```

This is useful when using `limit: 1` to fetch a single result from a list query. If the array is empty, `null` is returned.

## Optional Return Types

Methods can return `Optional<T>` to safely handle nullable results:

```java
@GraphqlQuery("query getUser($id: String!) { getUser(id: $id) { id name email } }")
Optional<User> findUser(String id);
```

Returns `Optional.empty()` when the data is null or missing, and `Optional.of(value)` otherwise. This also works with array unwrapping:

```java
@GraphqlQuery("query topUser($limit: Int!) { topUsers(limit: $limit) { id name email } }")
Optional<User> findTopUser(int limit);
```

## Optional Fields

By default, nullable GraphQL fields (without `!`) are wrapped in `Optional<>` in generated records:

```graphql
type User {
  id: ID!          # non-null
  name: String!    # non-null
  email: String    # nullable
}
```

```java
public record User(String id, String name, Optional<String> email) {}
```

This is controlled by `useOptional` on `@GraphqlSchema` (defaults to `true`):

```java
@GraphqlSchema(value = "schema.graphql", useOptional = false)
```

Override per method with `Toggle`:

```java
@GraphqlQuery(value = "...", useOptional = Toggle.FALSE)
```

## Type Annotations on Generated Records

Add annotations to all generated records using `typeAnnotations` (no-arg) and `rawTypeAnnotations` (with args):

```java
@GraphqlSchema(
    value = "schema.graphql",
    typeAnnotations = {Builder.class, Jacksonized.class},
    rawTypeAnnotations = {"@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)"}
)
```

Generates:

```java
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record User(String id, String name) {}
```

**Collision rule:** when the same annotation simple name appears in both `typeAnnotations` and `rawTypeAnnotations`, the class provides only the import and the raw string is used:

```java
typeAnnotations = {Builder.class},
rawTypeAnnotations = {"@Builder(toBuilder = true)"}
// Result: import lombok.Builder; + @Builder(toBuilder = true)
```

Override per method on `@GraphqlQuery` — non-empty arrays replace class-level values.

## Import-Only Classes with `uses`

When raw annotations reference classes not in `typeAnnotations`, use `uses` to add their imports:

```java
@GraphqlSchema(
    value = "schema.graphql",
    uses = {Min.class, Max.class, Pattern.class}
)
```

These classes are added as imports to all generated files but no annotations are generated from them.

## Non-Null Field Annotations

Automatically annotate all non-null (`!`) fields with `nonNullTypeAnnotations`:

```java
@GraphqlSchema(
    value = "schema.graphql",
    nonNullTypeAnnotations = {NotNull.class}
)
```

For `name: String!` and `email: String`, generates:

```java
public record User(@NotNull String name, Optional<String> email) {}
```

Same collision rule applies with `nonNullRawTypeAnnotations`. Overridable per method on `@GraphqlQuery`.

## Field-Level Annotations with `@GraphqlField`

Apply annotations or override types on specific fields. Repeatable, works on both the interface (class-level default) and individual methods:

```java
@GraphqlSchema(value = "schema.graphql", useOptional = false)
@GraphqlField(name = "email", typeAnnotations = {Email.class})
interface UserApi {

  @GraphqlQuery("{ user(id: \"1\") { id name email } }")
  @GraphqlField(name = "name", typeAnnotations = {NotBlank.class})
  UserResult getUser();
}
```

Generates:

```java
public record UserResult(String id, @NotBlank String name, @Email String email) {}
```

### Dot Notation for Nested Fields

Use dot notation to target fields in nested records:

```java
@GraphqlField(name = "location.coordinates.latitude", typeAnnotations = {NotNull.class})
@GraphqlField(name = "location.planet", typeAnnotations = {NotBlank.class})
```

### Field Type Override

Override the Java type for a field — useful when APIs return strings for dates without declaring custom scalars:

```java
@GraphqlField(name = "createdAt", type = ZonedDateTime.class)
@GraphqlField(name = "amount", type = BigDecimal.class)
```

Combines with annotations:

```java
@GraphqlField(name = "createdAt", type = ZonedDateTime.class, typeAnnotations = {NotNull.class})
```

Class-level `@GraphqlField` applies to all methods; method-level overrides for the same field name.

## Disabling Type Generation

If you provide your own model classes, disable automatic generation:

```java
@GraphqlSchema(value = "my-schema.graphql", generateTypes = false)
interface UserApi {
  // ...
}
```

Queries are still validated against the schema at compile time.

## Error Handling

GraphQL errors in the response throw `GraphqlErrorException`:

```java
try {
  User user = api.getUser("invalid-id");
} catch (GraphqlErrorException e) {
  String operation = e.operation();
  String errors = e.errors();
}
```
