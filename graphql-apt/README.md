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

### Result types with inner records

Nested result types are generated as inner records scoped to each query result. This ensures each query gets exactly the fields it selects, even when different queries target the same GraphQL type.

For a query like:

```graphql
{
  starship(id: "1") {
    id name
    location { planet sector }
    specs { lengthMeters classification }
  }
}
```

The processor generates a single file with inner records:

```java
public record StarshipResult(String id, String name, Location location, Specs specs) {

  public record Location(String planet, String sector) {}

  public record Specs(Integer lengthMeters, String classification) {}

}
```

Two different queries can select different fields from the same GraphQL type without conflict:

```java
// Query 1: selects location { planet }
public record CharByPlanet(String id, Location location) {
  public record Location(String planet) {}
}

// Query 2: selects location { sector region }
public record CharByRegion(String id, Location location) {
  public record Location(String sector, String region) {}
}
```

### Conflicting return type error

If two queries use the same return type name but select different fields, the processor reports compilation errors on both methods showing which fields each selects:

```
error: Conflicting return type 'CharResult': method selects [id, email] but method 'query1()' already selects [id, name]
  CharResult query2();
             ^
error: Conflicting return type 'CharResult': method selects [id, name] but method 'query2()' selects [id, email]
  CharResult query1();
             ^
```

### Input types and enums

Input types and enums are generated as top-level files since they represent the full schema type:

```java
public record CreateCharacterInput(String name, String email, Episode appearsIn) {}
```

```java
public enum Episode {
  NEWHOPE,
  EMPIRE,
  JEDI
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
