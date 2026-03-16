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
        .contains("public record Location(String planet, String sector, String region) {}");
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
        "public record ShipResult(String id, String name, Location location, Specs specs)");
    contents.contains("public record Location(String planet, Coordinates coordinates)");
    contents.contains("public record Coordinates(Double latitude, Double longitude) {}");
    contents.contains("public record Specs(Integer lengthMeters, String classification) {}");
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
        .contains("public record Location(String planet) {}");

    assertThat(compilation)
        .generatedSourceFile("test.CharByRegion")
        .contentsAsUtf8String()
        .contains("public record Location(String sector, String region) {}");
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
}
