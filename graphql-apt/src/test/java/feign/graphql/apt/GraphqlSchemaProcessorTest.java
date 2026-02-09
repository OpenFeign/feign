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
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id name email status }
                  }\""")
              CreateUserResult createUser(CreateUserInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateUserResult");
    assertThat(compilation).generatedSourceFile("test.CreateUserInput");
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
              @GraphqlQuery("{ user(id: \\"1\\") { id } }")
              Object query();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("GraphQL schema not found");
  }

  @Test
  void nestedTypesAreGenerated() {
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
                  { user(id: "1") { id name address { street city country } } }
                  \""")
              UserResult getUser();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserResult");
    assertThat(compilation).generatedSourceFile("test.Address");
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
                  mutation updateStatus($id: ID!, $status: Status!) {
                    updateStatus(id: $id, status: $status) { id status }
                  }\""")
              StatusResult updateStatus(String id, Status status);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.StatusResult");
    assertThat(compilation).generatedSourceFile("test.Status");
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
              @GraphqlQuery("{ user(id: \\"1\\") { id name tags } }")
              UserWithTagsResult getUser();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserWithTagsResult");
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
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id name }
                  }\""")
              CreateResult1 createUser1(CreateUserInput input);

              @GraphqlQuery(\"""
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id email }
                  }\""")
              CreateResult2 createUser2(CreateUserInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateUserInput");
    assertThat(compilation).generatedSourceFile("test.CreateResult1");
    assertThat(compilation).generatedSourceFile("test.CreateResult2");
  }

  @Test
  void deeplyNestedOrganizationQuery() {
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
                    organization(id: "1") {
                      id name
                      address { street city coordinates { latitude longitude } }
                      departments {
                        id name
                        lead { id name status }
                        members { id name email }
                        subDepartments { id name tags { key value } }
                        tags { key value }
                      }
                      metadata { foundedYear industry categories { name tags { key value } } }
                    }
                  }\""")
              OrgResult getOrganization();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.OrgResult");
    assertThat(compilation).generatedSourceFile("test.Address");
    assertThat(compilation).generatedSourceFile("test.Coordinates");
    assertThat(compilation).generatedSourceFile("test.Departments");
    assertThat(compilation).generatedSourceFile("test.Metadata");
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
                  mutation createOrg($input: CreateOrgInput!) {
                    createOrganization(input: $input) {
                      id name
                      departments { id name subDepartments { id name } }
                    }
                  }\""")
              CreateOrgResult createOrg(CreateOrgInput input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateOrgResult");
    assertThat(compilation).generatedSourceFile("test.CreateOrgInput");
    assertThat(compilation).generatedSourceFile("test.DepartmentInput");
    assertThat(compilation).generatedSourceFile("test.TagInput");
    assertThat(compilation).generatedSourceFile("test.AddressInput");
    assertThat(compilation).generatedSourceFile("test.OrgMetadataInput");
    assertThat(compilation).generatedSourceFile("test.CategoryInput");
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
                  query searchOrgs($criteria: OrgSearchCriteria!) {
                    searchOrganizations(criteria: $criteria) {
                      id name
                      departments { id name lead { id name } tags { key value } }
                      metadata { foundedYear categories { name parentCategory { name } } }
                    }
                  }\""")
              SearchResult searchOrganizations(OrgSearchCriteria criteria);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.SearchResult");
    assertThat(compilation).generatedSourceFile("test.OrgSearchCriteria");
    assertThat(compilation).generatedSourceFile("test.DepartmentFilterInput");
    assertThat(compilation).generatedSourceFile("test.TagInput");
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
                  query listUsers($filter: UserFilter) {
                    users(filter: $filter) { id name email status }
                  }\""")
              List<UserListResult> listUsers(UserFilter filter);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserListResult");
    assertThat(compilation).generatedSourceFile("test.UserFilter");
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
                  mutation createUser($input: CreateUserInput!) {
                    createUser(input: $input) { id name email }
                  }\""")
              CreateResult createUser(feign.graphql.GraphqlQuery input);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateResult");
  }

  @Test
  void userWithOrganizationMultipleLevelReuse() {
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
                    user(id: "1") {
                      id name status
                      address { street city country coordinates { latitude longitude } }
                      organization {
                        id name
                        address { street city coordinates { latitude longitude } }
                        departments { id name members { id name } }
                      }
                    }
                  }\""")
              FullUserResult getFullUser();

              @GraphqlQuery(\"""
                  query listUsers($filter: UserFilter) {
                    users(filter: $filter) { id name email tags }
                  }\""")
              UserListResult listUsers(UserFilter filter);
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.FullUserResult");
    assertThat(compilation).generatedSourceFile("test.Status");
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

              @GraphqlQuery("{ event(id: \\"1\\") { id name startTime endTime } }")
              EventResult getEvent();
            }
            """);

    var compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.EventResult");
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
              @GraphqlQuery("{ event(id: \\"1\\") { id name startTime } }")
              EventResult getEvent();
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
              @GraphqlQuery("{ event(id: \\"1\\") { id name startTime } }")
              EventResult getEvent();
            }
            """);

    var compilation =
        javac().withProcessors(new GraphqlSchemaProcessor()).compile(parentSource, source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.EventResult");
  }
}
