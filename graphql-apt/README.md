Feign GraphQL APT
===================

Annotation processor for `feign-graphql` that generates Java records from GraphQL schemas at compile time.

Given a `@GraphqlSchema`-annotated interface, this processor:

- Parses the referenced `.graphql` schema file
- Validates all `@GraphqlQuery` strings against the schema
- Generates Java records for result types, input types, and enums
- Maps custom scalars to Java types via `@Scalar` annotations

See the [feign-graphql README](../graphql/README.md) for usage examples.

## Generated Output

For a schema type like:

```graphql
type User {
  id: ID!
  name: String!
  email: String
  status: Status!
}

enum Status {
  ACTIVE
  INACTIVE
}
```

The processor generates:

```java
public record User(String id, String name, String email, Status status) {}
```

```java
public enum Status {
  ACTIVE,
  INACTIVE
}
```

## Maven Configuration

Add as a `provided` dependency so it runs during compilation but is not included at runtime:

```xml
<dependency>
  <groupId>io.github.openfeign.experimental</groupId>
  <artifactId>feign-graphql-apt</artifactId>
  <version>${feign.version}</version>
  <scope>provided</scope>
</dependency>
```
