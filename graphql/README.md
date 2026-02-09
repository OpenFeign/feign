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

Build the client with `GraphqlContract`, `GraphqlEncoder`, and `GraphqlDecoder`:

```java
UserApi api = Feign.builder()
    .contract(new GraphqlContract())
    .encoder(new GraphqlEncoder(new JacksonEncoder()))
    .decoder(new GraphqlDecoder())
    .target(UserApi.class, "https://api.example.com/graphql");

User user = api.getUser("123");
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
