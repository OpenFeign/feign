/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.graphql.apt;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

class GraphqlSchemaProcessorTest {

  @Test
  void validMutationGeneratesTypes() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MyApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface MyApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id name email appearsIn }
                  }\""")
              CreateCharacterResult createCharacter(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateCharacterResult");
    assertThat(compilation).generatedSourceFile("test.CreateCharacterInput");
  }

  @Test
  void invalidQueryReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.BadApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface BadApi {
              @GraphqlQuery("{ nonExistentField }")
              BadResult query();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("GraphQL validation error");
  }

  @Test
  void missingSchemaReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NoSchemaApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("nonexistent-schema.graphql")
            interface NoSchemaApi {
              @GraphqlQuery("{ character(id: \\"1\\") { id } }")
              Object query();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("GraphQL schema not found");
  }

  @Test
  void nestedTypesAreGeneratedAsInnerRecords() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NestedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface NestedApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name location { planet sector region } } }
                  \""")
              CharacterResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CharacterResult");
    assertThat(compilation)
        .generatedSourceFile("test.CharacterResult")
        .contentsAsUtf8String()
        .contains(
            "public record Location(Optional<String> planet, Optional<String> sector, Optional<String> region) {}");
  }

  @Test
  void enumsAreGeneratedAsJavaEnums() {
    var source =
        JavaFileObjects.forSourceString(
            "test.EnumApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface EnumApi {
              @GraphqlQuery(\"""
                  mutation updateEpisode($id: ID!, $episode: Episode!) {
                    updateEpisode(id: $id, episode: $episode) { id appearsIn }
                  }\""")
              EpisodeResult updateEpisode(String id, Episode episode);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.EpisodeResult");
    assertThat(compilation).generatedSourceFile("test.Episode");
  }

  @Test
  void listTypesMapToJavaList() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ListApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ListApi {
              @GraphqlQuery("{ character(id: \\"1\\") { id name tags } }")
              CharacterWithTagsResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CharacterWithTagsResult");
  }

  @Test
  void multipleMethodsSharingInputType() {
    var source =
        JavaFileObjects.forSourceString(
            "test.SharedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface SharedApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id name }
                  }\""")
              CreateResult1 createCharacter1(CreateCharacterInput input);

              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id email }
                  }\""")
              CreateResult2 createCharacter2(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateCharacterInput");
    assertThat(compilation).generatedSourceFile("test.CreateResult1");
    assertThat(compilation).generatedSourceFile("test.CreateResult2");
  }

  @Test
  void deeplyNestedStarshipQuery() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DeepApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface DeepApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      location { planet sector coordinates { latitude longitude } }
                      squadrons {
                        id name
                        leader { id name appearsIn }
                        members { id name email }
                        subSquadrons { id name traits { key value } }
                        traits { key value }
                      }
                      specs { lengthMeters classification weapons { name traits { key value } } }
                    }
                  }\""")
              StarshipResult getStarship();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.StarshipResult");
    assertThat(compilation)
        .generatedSourceFile("test.StarshipResult")
        .contentsAsUtf8String()
        .contains("public record Location(");
    assertThat(compilation)
        .generatedSourceFile("test.StarshipResult")
        .contentsAsUtf8String()
        .contains("public record Squadrons(");
    assertThat(compilation)
        .generatedSourceFile("test.StarshipResult")
        .contentsAsUtf8String()
        .contains("public record Specs(");
  }

  @Test
  void complexMutationWithNestedInputs() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ComplexMutationApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ComplexMutationApi {
              @GraphqlQuery(\"""
                  mutation createStarship($input: CreateStarshipInput!) {
                    createStarship(input: $input) {
                      id name
                      squadrons { id name subSquadrons { id name } }
                    }
                  }\""")
              CreateStarshipResult createStarship(CreateStarshipInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateStarshipResult");
    assertThat(compilation).generatedSourceFile("test.CreateStarshipInput");
    assertThat(compilation).generatedSourceFile("test.SquadronInput");
    assertThat(compilation).generatedSourceFile("test.TraitInput");
    assertThat(compilation).generatedSourceFile("test.LocationInput");
    assertThat(compilation).generatedSourceFile("test.ShipSpecsInput");
    assertThat(compilation).generatedSourceFile("test.WeaponInput");
  }

  @Test
  void searchWithComplexFilterInput() {
    var source =
        JavaFileObjects.forSourceString(
            "test.SearchApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface SearchApi {
              @GraphqlQuery(\"""
                  query searchStarships($criteria: StarshipSearchCriteria!) {
                    searchStarships(criteria: $criteria) {
                      id name
                      squadrons { id name leader { id name } traits { key value } }
                      specs { lengthMeters weapons { name parentWeapon { name } } }
                    }
                  }\""")
              SearchResult searchStarships(StarshipSearchCriteria criteria);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.SearchResult");
    assertThat(compilation).generatedSourceFile("test.StarshipSearchCriteria");
    assertThat(compilation).generatedSourceFile("test.SquadronFilterInput");
    assertThat(compilation).generatedSourceFile("test.TraitInput");
  }

  @Test
  void listReturnTypeGeneratesElementType() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ListReturnApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import java.util.List;

            @GraphqlSchema("test-schema.graphql")
            interface ListReturnApi {
              @GraphqlQuery(\"""
                  query listCharacters($filter: CharacterFilter) {
                    characters(filter: $filter) { id name email appearsIn }
                  }\""")
              List<CharacterListResult> listCharacters(CharacterFilter filter);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CharacterListResult");
    assertThat(compilation).generatedSourceFile("test.CharacterFilter");
  }

  @Test
  void existingExternalTypeSkipsGeneration() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ExternalTypeApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ExternalTypeApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id name email }
                  }\""")
              CreateResult createCharacter(feign.graphql.GraphqlQuery input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateResult");
  }

  @Test
  void characterWithStarshipMultipleLevelReuse() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ReuseApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ReuseApi {
              @GraphqlQuery(\"""
                  {
                    character(id: "1") {
                      id name appearsIn
                      location { planet sector region coordinates { latitude longitude } }
                      starship {
                        id name
                        location { planet sector coordinates { latitude longitude } }
                        squadrons { id name members { id name } }
                      }
                    }
                  }\""")
              FullCharacterResult getFullCharacter();

              @GraphqlQuery(\"""
                  query listCharacters($filter: CharacterFilter) {
                    characters(filter: $filter) { id name email tags }
                  }\""")
              CharacterListResult listCharacters(CharacterFilter filter);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.FullCharacterResult");
    assertThat(compilation).generatedSourceFile("test.Episode");
  }

  @Test
  void scalarAnnotationMapsCustomScalar() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ScalarApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Scalar;

            @GraphqlSchema("scalar-test-schema.graphql")
            interface ScalarApi {
              @Scalar("DateTime")
              default String dateTime(String raw) { return raw; }

              @GraphqlQuery("{ battle(id: \\"1\\") { id name startTime endTime } }")
              BattleResult getBattle();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.BattleResult");
  }

  @Test
  void missingScalarAnnotationReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MissingScalarApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("scalar-test-schema.graphql")
            interface MissingScalarApi {
              @GraphqlQuery("{ battle(id: \\"1\\") { id name startTime } }")
              BattleResult getBattle();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Custom scalar 'DateTime'");
    assertThat(compilation).hadErrorContaining("@Scalar(\"DateTime\")");
  }

  @Test
  void scalarFromParentInterfaceIsInherited() {
    var parentSource =
        JavaFileObjects.forSourceString(
            "test.ScalarDefinitions",
            """
            package test;

            import feign.graphql.Scalar;

            interface ScalarDefinitions {
              @Scalar("DateTime")
              default String dateTime(String raw) { return raw; }
            }
            """);

    var source =
        JavaFileObjects.forSourceString(
            "test.ChildApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("scalar-test-schema.graphql")
            interface ChildApi extends ScalarDefinitions {
              @GraphqlQuery("{ battle(id: \\"1\\") { id name startTime } }")
              BattleResult getBattle();
            }
            """);

    var compilation =
        javac().withProcessors(new GraphqlSchemaProcessor()).compile(parentSource, source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.BattleResult");
  }

  @Test
  void conflictingReturnTypesReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ConflictApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ConflictApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              CharResult query1();

              @GraphqlQuery(\"""
                  { character(id: "2") { id email } }
                  \""")
              CharResult query2();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Conflicting return type 'CharResult'");
    assertThat(compilation).hadErrorContaining("'query1()'");
    assertThat(compilation).hadErrorContaining("'query2()'");
    assertThat(compilation).hadErrorContaining("id, name");
    assertThat(compilation).hadErrorContaining("id, email");
  }

  @Test
  void sameReturnTypeSameFieldsSucceeds() {
    var source =
        JavaFileObjects.forSourceString(
            "test.SameFieldsApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface SameFieldsApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              CharResult query1();

              @GraphqlQuery(\"""
                  { character(id: "2") { id name email } }
                  \""")
              CharResult query2();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CharResult");
  }

  @Test
  void innerClassContentIsCorrect() {
    var source =
        JavaFileObjects.forSourceString(
            "test.InnerApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface InnerApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      location { planet coordinates { latitude longitude } }
                      specs { lengthMeters classification }
                    }
                  }\""")
              ShipResult getShip();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.ShipResult").contentsAsUtf8String();

    contents.contains(
        "public record ShipResult(String id, String name, Optional<Location> location, Optional<Specs> specs)");
    contents.contains(
        "public record Location(Optional<String> planet, Optional<Coordinates> coordinates)");
    contents.contains(
        "public record Coordinates(Optional<Double> latitude, Optional<Double> longitude) {}");
    contents.contains(
        "public record Specs(Optional<Integer> lengthMeters, Optional<String> classification) {}");
  }

  @Test
  void differentQueriesDifferentNestedFields() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DiffNestedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface DiffNestedApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id location { planet } } }
                  \""")
              CharByPlanet queryByPlanet();

              @GraphqlQuery(\"""
                  { character(id: "2") { id location { sector region } } }
                  \""")
              CharByRegion queryByRegion();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CharByPlanet");
    assertThat(compilation).generatedSourceFile("test.CharByRegion");

    assertThat(compilation)
        .generatedSourceFile("test.CharByPlanet")
        .contentsAsUtf8String()
        .contains("public record Location(Optional<String> planet) {}");

    assertThat(compilation)
        .generatedSourceFile("test.CharByRegion")
        .contentsAsUtf8String()
        .contains("public record Location(Optional<String> sector, Optional<String> region) {}");
  }

  @Test
  void missingRequiredVariableReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MissingVarApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface MissingVarApi {
              @GraphqlQuery("query getChar($id: ID!) { character(id: $id) { id name } }")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Required GraphQL variable '$id'");
  }

  @Test
  void requiredVariableWithDefaultValueIsOptional() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DefaultVarApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface DefaultVarApi {
              @GraphqlQuery("query getChar($id: ID! = \\"1\\") { character(id: $id) { id name } }")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
  }

  @Test
  void nullableVariableIsOptional() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NullableVarApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface NullableVarApi {
              @GraphqlQuery(\"""
                  query listChars($filter: CharacterFilter) {
                    characters(filter: $filter) { id name }
                  }\""")
              CharResult listCharacters();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
  }

  @Test
  void paramAnnotationNotCountedAsVariable() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ParamApi",
            """
            package test;

            import feign.Param;
            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface ParamApi {
              @GraphqlQuery("query getChar($id: ID!) { character(id: $id) { id name } }")
              CharResult getCharacter(@Param("auth") String token);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Required GraphQL variable '$id'");
  }

  @Test
  void requiredVariableWithMatchingParameterSucceeds() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MatchApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface MatchApi {
              @GraphqlQuery("query getChar($id: ID!) { character(id: $id) { id name } }")
              CharResult getCharacter(String id);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
  }

  @Test
  void multipleRequiredVariablesMissing() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MultiMissingApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface MultiMissingApi {
              @GraphqlQuery(\"""
                  mutation updateEpisode($id: ID!, $episode: Episode!) {
                    updateEpisode(id: $id, episode: $episode) { id appearsIn }
                  }\""")
              EpisodeResult updateEpisode();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Required GraphQL variable '$id'");
    assertThat(compilation).hadErrorContaining("Required GraphQL variable '$episode'");
  }

  @Test
  void inlineInputMissingRequiredFieldReportsError() {
    var source =
        JavaFileObjects.forSourceString(
            "test.InlineApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface InlineApi {
              @GraphqlQuery(\"""
                  mutation create($name: String!) {
                    createCharacter(input: { name: $name }) { id }
                  }\""")
              Object create(String name);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("email");
  }

  @Test
  void useOptionalDisabledGeneratesPlainTypes() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NoOptionalApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface NoOptionalApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email location { planet } } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains(
        "public record CharResult(String id, String name, String email, Location location)");
    contents.contains("public record Location(String planet) {}");
  }

  @Test
  void useOptionalDefaultWrapsNullableFields() {
    var source =
        JavaFileObjects.forSourceString(
            "test.OptionalApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface OptionalApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email location { planet } } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("import java.util.Optional;");
    contents.contains(
        "String id, String name, Optional<String> email, Optional<Location> location");
    contents.contains("public record Location(Optional<String> planet) {}");
  }

  @Test
  void useOptionalMethodOverridesClassLevel() {
    var source =
        JavaFileObjects.forSourceString(
            "test.OverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Toggle;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface OverrideApi {
              @GraphqlQuery(value = \"""
                  { character(id: "1") { id name email } }
                  \""", useOptional = Toggle.TRUE)
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("import java.util.Optional;");
    contents.contains("String id, String name, Optional<String> email");
  }

  @Test
  void typeAnnotationsAddedToGeneratedRecords() {
    var source =
        JavaFileObjects.forSourceString(
            "test.AnnotatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class})
            interface AnnotatedApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id name }
                  }\""")
              CreateResult createCharacter(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CreateResult")
        .contentsAsUtf8String()
        .contains("@Deprecated");
    assertThat(compilation)
        .generatedSourceFile("test.CreateCharacterInput")
        .contentsAsUtf8String()
        .contains("@Deprecated");
  }

  @Test
  void rawTypeAnnotationsAppendedToGeneratedRecords() {
    var source =
        JavaFileObjects.forSourceString(
            "test.RawAnnotatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                rawTypeAnnotations = {"@Deprecated"})
            interface RawAnnotatedApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@Deprecated");
  }

  @Test
  void collisionBetweenTypeAndRawAnnotationUsesClassAsImportOnly() {
    var source =
        JavaFileObjects.forSourceString(
            "test.CollisionApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class},
                rawTypeAnnotations = {"@Deprecated(since = \\"1.0\\")"})
            interface CollisionApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated(since = \"1.0\")");
  }

  @Test
  void methodLevelAnnotationsOverrideClassLevel() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MethodOverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class})
            interface MethodOverrideApi {
              @GraphqlQuery(value = \"""
                  { character(id: "1") { id name } }
                  \""", rawTypeAnnotations = {"@SuppressWarnings(\\"unchecked\\")"})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@SuppressWarnings(\"unchecked\")");
  }

  @Test
  void optionalOnInputTypeWrapsNullableFields() {
    var source =
        JavaFileObjects.forSourceString(
            "test.OptionalInputApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("test-schema.graphql")
            interface OptionalInputApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id }
                  }\""")
              Object createCharacter(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation)
            .generatedSourceFile("test.CreateCharacterInput")
            .contentsAsUtf8String();
    contents.contains("String name, String email");
    contents.contains("Optional<Episode> appearsIn");
    contents.contains("Optional<LocationInput> location");
    contents.contains("Optional<List<String>> tags");
    contents.contains("Optional<String> starshipId");
  }

  @Test
  void mixedTypeAndRawAnnotationsWithoutCollision() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MixedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class},
                rawTypeAnnotations = {"@SuppressWarnings(\\"unchecked\\")"})
            interface MixedApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated");
    contents.contains("@SuppressWarnings(\"unchecked\")");
  }

  @Test
  void annotationsAppliedToNestedResultRecords() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NestedAnnotatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class})
            interface NestedAnnotatedApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      location { planet coordinates { latitude longitude } }
                    }
                  }\""")
              ShipResult getShip();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.ShipResult").contentsAsUtf8String();
    contents.contains("@Deprecated\npublic record ShipResult(");
    contents.contains("@Deprecated\n  public record Location(");
    contents.contains("@Deprecated\n    public record Coordinates(");
  }

  @Test
  void fieldAnnotationOnSimpleField() {
    var source =
        JavaFileObjects.forSourceString(
            "test.FieldAnnotApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface FieldAnnotApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id name email }
                  }\""")
              @GraphqlField(name = "email", typeAnnotations = {Deprecated.class})
              CreateResult createCharacter(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CreateResult")
        .contentsAsUtf8String()
        .contains("String id, String name, @Deprecated String email");
  }

  @Test
  void fieldAnnotationWithRawString() {
    var source =
        JavaFileObjects.forSourceString(
            "test.FieldRawApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface FieldRawApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "name", rawTypeAnnotations = {"@SuppressWarnings(\\"unchecked\\")"})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@SuppressWarnings(\"unchecked\") String name");
  }

  @Test
  void fieldAnnotationWithDotNotationForNestedField() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DotNotationApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface DotNotationApi {
              @GraphqlQuery(\"""
                  {
                    character(id: "1") {
                      id name
                      location { planet sector }
                    }
                  }\""")
              @GraphqlField(name = "location.planet", typeAnnotations = {Deprecated.class})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@Deprecated String planet, String sector");
  }

  @Test
  void fieldAnnotationCollisionUsesClassAsImportOnly() {
    var source =
        JavaFileObjects.forSourceString(
            "test.FieldCollisionApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface FieldCollisionApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              @GraphqlField(name = "name",
                  typeAnnotations = {Deprecated.class},
                  rawTypeAnnotations = {"@Deprecated(since = \\"2.0\\")"})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@Deprecated(since = \"2.0\") String name");
  }

  @Test
  void multipleFieldAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MultiFieldApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface MultiFieldApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "name", typeAnnotations = {Deprecated.class})
              @GraphqlField(name = "email", typeAnnotations = {Deprecated.class})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated String name");
    contents.contains("@Deprecated String email");
  }

  @Test
  void classLevelTypeAnnotationsWithMethodLevelFieldAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ClassAndFieldApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                typeAnnotations = {Deprecated.class})
            interface ClassAndFieldApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "email", rawTypeAnnotations = {"@SuppressWarnings(\\"unchecked\\")"})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated\npublic record CharResult(");
    contents.contains("@SuppressWarnings(\"unchecked\") String email");
  }

  @Test
  void deepNestedDotNotation() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DeepDotApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface DeepDotApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      location { planet coordinates { latitude longitude } }
                    }
                  }\""")
              @GraphqlField(name = "location.coordinates.latitude", typeAnnotations = {Deprecated.class})
              ShipResult getShip();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.ShipResult")
        .contentsAsUtf8String()
        .contains("@Deprecated Double latitude, Double longitude");
  }

  @Test
  void usesAddsImportsForRawTypeAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.UsesApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                uses = {Deprecated.class},
                rawTypeAnnotations = {"@Deprecated(since = \\"1.0\\")"})
            interface UsesApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@Deprecated(since = \"1.0\")");
  }

  @Test
  void usesAddsImportsForRawFieldAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.UsesFieldApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                uses = {Deprecated.class})
            interface UsesFieldApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "email", rawTypeAnnotations = {"@Deprecated(since = \\"2.0\\")"})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated(since = \"2.0\") String email");
  }

  @Test
  void fieldTypeOverride() {
    var source =
        JavaFileObjects.forSourceString(
            "test.TypeOverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.time.ZonedDateTime;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface TypeOverrideApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "email", type = ZonedDateTime.class)
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("import java.time.ZonedDateTime;");
    contents.contains("String id, String name, ZonedDateTime email");
  }

  @Test
  void fieldTypeOverrideWithAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.TypeOverrideAnnotApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.time.ZonedDateTime;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface TypeOverrideAnnotApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "email", type = ZonedDateTime.class, typeAnnotations = {Deprecated.class})
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("@Deprecated ZonedDateTime email");
  }

  @Test
  void fieldTypeOverrideOnNestedField() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NestedTypeOverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.math.BigDecimal;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            interface NestedTypeOverrideApi {
              @GraphqlQuery(\"""
                  {
                    character(id: "1") {
                      id name
                      location { planet coordinates { latitude longitude } }
                    }
                  }\""")
              @GraphqlField(name = "location.coordinates.latitude", type = BigDecimal.class)
              @GraphqlField(name = "location.coordinates.longitude", type = BigDecimal.class)
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("import java.math.BigDecimal;");
    contents.contains("BigDecimal latitude, BigDecimal longitude");
  }

  @Test
  void classLevelFieldTypeOverrideAppliesToAllMethods() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ClassFieldOverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.time.ZonedDateTime;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            @GraphqlField(name = "email", type = ZonedDateTime.class)
            interface ClassFieldOverrideApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              CharResult1 getCharacter1();

              @GraphqlQuery(\"""
                  { character(id: "2") { id email } }
                  \""")
              CharResult2 getCharacter2();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult1")
        .contentsAsUtf8String()
        .contains("ZonedDateTime email");
    assertThat(compilation)
        .generatedSourceFile("test.CharResult2")
        .contentsAsUtf8String()
        .contains("ZonedDateTime email");
  }

  @Test
  void methodLevelFieldOverridesClassLevel() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MethodOverridesClassFieldApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.time.ZonedDateTime;
            import java.time.Instant;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false)
            @GraphqlField(name = "email", type = ZonedDateTime.class)
            interface MethodOverridesClassFieldApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "email", type = Instant.class)
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("test.CharResult")
        .contentsAsUtf8String()
        .contains("Instant email");
  }

  @Test
  void nonNullAnnotationsAppliedToRequiredFields() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NonNullApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                nonNullTypeAnnotations = {Deprecated.class})
            interface NonNullApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated String id, @Deprecated String name, String email");
  }

  @Test
  void nonNullAnnotationsOnInputType() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NonNullInputApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                nonNullTypeAnnotations = {Deprecated.class})
            interface NonNullInputApi {
              @GraphqlQuery(\"""
                  mutation createCharacter($input: CreateCharacterInput!) {
                    createCharacter(input: $input) { id }
                  }\""")
              Object createCharacter(CreateCharacterInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation)
            .generatedSourceFile("test.CreateCharacterInput")
            .contentsAsUtf8String();
    contents.contains("@Deprecated String name, @Deprecated String email");
    contents.contains("Episode appearsIn");
  }

  @Test
  void nonNullRawAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NonNullRawApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                nonNullRawTypeAnnotations = {"@SuppressWarnings(\\"required\\")"})
            interface NonNullRawApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@SuppressWarnings(\"required\") String id");
    contents.contains("String email");
  }

  @Test
  void nonNullAnnotationsCombinedWithFieldAnnotations() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NonNullFieldComboApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.GraphqlField;
            import java.time.ZonedDateTime;

            @GraphqlSchema(value = "test-schema.graphql", useOptional = false,
                nonNullTypeAnnotations = {Deprecated.class})
            interface NonNullFieldComboApi {
              @GraphqlQuery(\"""
                  { character(id: "1") { id name email } }
                  \""")
              @GraphqlField(name = "name", type = ZonedDateTime.class)
              CharResult getCharacter();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.CharResult").contentsAsUtf8String();
    contents.contains("@Deprecated ZonedDateTime name");
    contents.contains("@Deprecated String id");
    contents.contains("String email");
  }

  @Test
  void useAliasForFieldNamesGeneratesAliasedInnerRecords() {
    var source =
        JavaFileObjects.forSourceString(
            "test.AliasApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useAliasForFieldNames = true)
            interface AliasApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      specification: specs { lengthMeters classification }
                      currentLocation: location { planet sector }
                    }
                  }\""")
              StarshipResult getStarship();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.StarshipResult").contentsAsUtf8String();
    contents.contains("Optional<Specification> specification");
    contents.contains("Optional<CurrentLocation> currentLocation");
    contents.contains("public record Specification(");
    contents.contains("public record CurrentLocation(");
  }

  @Test
  void useAliasForFieldNamesDisabledUsesFieldName() {
    var source =
        JavaFileObjects.forSourceString(
            "test.NoAliasApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "test-schema.graphql", useAliasForFieldNames = false)
            interface NoAliasApi {
              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      specification: specs { lengthMeters classification }
                      currentLocation: location { planet sector }
                    }
                  }\""")
              StarshipResult getStarship();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var contents =
        assertThat(compilation).generatedSourceFile("test.StarshipResult").contentsAsUtf8String();
    contents.contains("Optional<Specs> specs");
    contents.contains("Optional<Location> location");
    contents.contains("public record Specs(");
    contents.contains("public record Location(");
  }

  @Test
  void useAliasForFieldNamesMethodOverridesClassLevel() {
    var source =
        JavaFileObjects.forSourceString(
            "test.AliasOverrideApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Toggle;

            @GraphqlSchema(value = "test-schema.graphql", useAliasForFieldNames = false)
            interface AliasOverrideApi {
              @GraphqlQuery(value = \"""
                  {
                    starship(id: "1") {
                      id name
                      specification: specs { lengthMeters classification }
                    }
                  }\""", useAliasForFieldNames = Toggle.TRUE)
              AliasedResult getAliased();

              @GraphqlQuery(\"""
                  {
                    starship(id: "1") {
                      id name
                      specification: specs { lengthMeters classification }
                    }
                  }\""")
              PlainResult getPlain();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();

    var aliased =
        assertThat(compilation).generatedSourceFile("test.AliasedResult").contentsAsUtf8String();
    aliased.contains("Optional<Specification> specification");
    aliased.contains("public record Specification(");

    var plain =
        assertThat(compilation).generatedSourceFile("test.PlainResult").contentsAsUtf8String();
    plain.contains("Optional<Specs> specs");
    plain.contains("public record Specs(");
  }

  @Test
  void deprecatedFieldsIncludedByDefault() {
    var source =
        JavaFileObjects.forSourceString(
            "test.DefaultDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("deprecated-test-schema.graphql")
            interface DefaultDeprecatedApi {
              @GraphqlQuery(\"""
                  { user(id: "1") { id name email emails status } }
                  \""")
              UserResult getUser();

              @GraphqlQuery(\"""
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id name }
                  }\""")
              CreateUserResult createUser(CreateUserInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    var userResult =
        assertThat(compilation).generatedSourceFile("test.UserResult").contentsAsUtf8String();
    userResult.contains("@Deprecated Optional<String> email,");

    var createInput =
        assertThat(compilation).generatedSourceFile("test.CreateUserInput").contentsAsUtf8String();
    createInput.contains("@Deprecated Optional<String> email");

    var status =
        assertThat(compilation).generatedSourceFile("test.UserStatus").contentsAsUtf8String();
    status.contains("@Deprecated\n  BANNED");
  }

  @Test
  void deprecatedFieldsSkippedWhenDisabledAtClassLevel() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ClassDisabledDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "deprecated-test-schema.graphql", generateDeprecated = false)
            interface ClassDisabledDeprecatedApi {
              @GraphqlQuery(\"""
                  { user(id: "1") { id name email emails status } }
                  \""")
              UserResult getUser();

              @GraphqlQuery(\"""
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id name }
                  }\""")
              CreateUserResult createUser(CreateUserInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.UserResult")
        .contentsAsUtf8String()
        .doesNotContain(" email,");
    assertThat(compilation)
        .generatedSourceFile("test.CreateUserInput")
        .contentsAsUtf8String()
        .doesNotContain(" email,");
    assertThat(compilation)
        .generatedSourceFile("test.UserStatus")
        .contentsAsUtf8String()
        .doesNotContain("BANNED");
  }

  @Test
  void methodLevelToggleCanReEnableDeprecated() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MethodOverrideDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Toggle;

            @GraphqlSchema(value = "deprecated-test-schema.graphql", generateDeprecated = false)
            interface MethodOverrideDeprecatedApi {
              @GraphqlQuery(value = \"""
                  { user(id: "1") { id name email emails status } }
                  \""", generateDeprecated = Toggle.TRUE)
              WithDeprecatedResult getUserWithDeprecated();

              @GraphqlQuery(\"""
                  { user(id: "1") { id name email emails status } }
                  \""")
              WithoutDeprecatedResult getUserFiltered();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.WithDeprecatedResult")
        .contentsAsUtf8String()
        .contains(" email,");
    assertThat(compilation)
        .generatedSourceFile("test.WithoutDeprecatedResult")
        .contentsAsUtf8String()
        .doesNotContain(" email,");
  }

  @Test
  void deprecatedEnumValuesSkippedWhenDisabled() {
    var source =
        JavaFileObjects.forSourceString(
            "test.EnumDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema(value = "deprecated-test-schema.graphql", generateDeprecated = false)
            interface EnumDeprecatedApi {
              @GraphqlQuery(\"""
                  { user(id: "1") { id status } }
                  \""")
              UserResult getUser();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    var status =
        assertThat(compilation).generatedSourceFile("test.UserStatus").contentsAsUtf8String();
    status.contains("ACTIVE");
    status.contains("INACTIVE");
    status.doesNotContain("BANNED");
  }

  @Test
  void methodToggleReEnabledDeprecatedStillCarriesAnnotation() {
    var source =
        JavaFileObjects.forSourceString(
            "test.ReEnabledDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Toggle;

            @GraphqlSchema(value = "deprecated-test-schema.graphql", generateDeprecated = false)
            interface ReEnabledDeprecatedApi {
              @GraphqlQuery(value = \"""
                  { user(id: "1") { id name email status } }
                  \""", generateDeprecated = Toggle.TRUE)
              ReEnabledResult getUser();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.ReEnabledResult")
        .contentsAsUtf8String()
        .contains("@Deprecated Optional<String> email,");
    assertThat(compilation)
        .generatedSourceFile("test.UserStatus")
        .contentsAsUtf8String()
        .contains("@Deprecated\n  BANNED");
  }

  @Test
  void deprecatedEnumValuesKeptByDefault() {
    var source =
        JavaFileObjects.forSourceString(
            "test.EnumDefaultApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;

            @GraphqlSchema("deprecated-test-schema.graphql")
            interface EnumDefaultApi {
              @GraphqlQuery(\"""
                  { user(id: "1") { id status } }
                  \""")
              UserResult getUser();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    var status =
        assertThat(compilation).generatedSourceFile("test.UserStatus").contentsAsUtf8String();
    status.contains("ACTIVE");
    status.contains("INACTIVE");
    status.contains("BANNED");
  }

  @Test
  void methodLevelToggleCanDisableDeprecated() {
    var source =
        JavaFileObjects.forSourceString(
            "test.MethodDisableDeprecatedApi",
            """
            package test;

            import feign.graphql.GraphqlSchema;
            import feign.graphql.GraphqlQuery;
            import feign.graphql.Toggle;

            @GraphqlSchema("deprecated-test-schema.graphql")
            interface MethodDisableDeprecatedApi {
              @GraphqlQuery(value = \"""
                  { user(id: "1") { id name email emails status } }
                  \""", generateDeprecated = Toggle.FALSE)
              FilteredUserResult getUserFiltered();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.FilteredUserResult")
        .contentsAsUtf8String()
        .doesNotContain(" email,");
    assertThat(compilation)
        .generatedSourceFile("test.UserStatus")
        .contentsAsUtf8String()
        .doesNotContain("BANNED");
  }
}
