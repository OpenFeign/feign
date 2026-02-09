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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class GraphqlSchemaProcessorTest {

  @Test
  void validMutationGeneratesTypes() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.MyApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface MyApi {\n"
                + "  @GraphqlQuery(\"mutation createUser($input: CreateUserInput!) {"
                + " createUser(input: $input) { id name email status } }\")\n"
                + "  CreateUserResult createUser(CreateUserInput input);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateUserResult");
    assertThat(compilation).generatedSourceFile("test.CreateUserInput");
  }

  @Test
  void invalidQueryReportsError() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.BadApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface BadApi {\n"
                + "  @GraphqlQuery(\"{ nonExistentField }\")\n"
                + "  BadResult query();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("GraphQL validation error");
  }

  @Test
  void missingSchemaReportsError() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.NoSchemaApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"nonexistent-schema.graphql\")\n"
                + "interface NoSchemaApi {\n"
                + "  @GraphqlQuery(\"{ user(id: \\\"1\\\") { id } }\")\n"
                + "  Object query();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("GraphQL schema not found");
  }

  @Test
  void nestedTypesAreGenerated() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.NestedApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface NestedApi {\n"
                + "  @GraphqlQuery(\"{ user(id: \\\"1\\\") { id name address { street city"
                + " country } } }\")\n"
                + "  UserResult getUser();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserResult");
    assertThat(compilation).generatedSourceFile("test.Address");
  }

  @Test
  void enumsAreGeneratedAsJavaEnums() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.EnumApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface EnumApi {\n"
                + "  @GraphqlQuery(\"mutation updateStatus($id: ID!, $status: Status!) {"
                + " updateStatus(id: $id, status: $status) { id status } }\")\n"
                + "  StatusResult updateStatus(String id, Status status);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.StatusResult");
    assertThat(compilation).generatedSourceFile("test.Status");
  }

  @Test
  void listTypesMapToJavaList() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ListApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface ListApi {\n"
                + "  @GraphqlQuery(\"{ user(id: \\\"1\\\") { id name tags } }\")\n"
                + "  UserWithTagsResult getUser();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserWithTagsResult");
  }

  @Test
  void multipleMethodsSharingInputType() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.SharedApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface SharedApi {\n"
                + "  @GraphqlQuery(\"mutation createUser($input: CreateUserInput!) {"
                + " createUser(input: $input) { id name } }\")\n"
                + "  CreateResult1 createUser1(CreateUserInput input);\n"
                + "\n"
                + "  @GraphqlQuery(\"mutation createUser($input: CreateUserInput!) {"
                + " createUser(input: $input) { id email } }\")\n"
                + "  CreateResult2 createUser2(CreateUserInput input);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateUserInput");
    assertThat(compilation).generatedSourceFile("test.CreateResult1");
    assertThat(compilation).generatedSourceFile("test.CreateResult2");
  }

  @Test
  void deeplyNestedOrganizationQuery() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.DeepApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface DeepApi {\n"
                + "  @GraphqlQuery(\"{ organization(id: \\\"1\\\") {"
                + " id name"
                + " address { street city coordinates { latitude longitude } }"
                + " departments {"
                + "   id name"
                + "   lead { id name status }"
                + "   members { id name email }"
                + "   subDepartments { id name tags { key value } }"
                + "   tags { key value }"
                + " }"
                + " metadata { foundedYear industry categories { name tags { key value } } }"
                + " } }\")\n"
                + "  OrgResult getOrganization();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.OrgResult");
    assertThat(compilation).generatedSourceFile("test.Address");
    assertThat(compilation).generatedSourceFile("test.Coordinates");
    assertThat(compilation).generatedSourceFile("test.Departments");
    assertThat(compilation).generatedSourceFile("test.Metadata");
  }

  @Test
  void complexMutationWithNestedInputs() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ComplexMutationApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface ComplexMutationApi {\n"
                + "  @GraphqlQuery(\"mutation createOrg($input: CreateOrgInput!) {"
                + " createOrganization(input: $input) {"
                + " id name"
                + " departments { id name subDepartments { id name } }"
                + " } }\")\n"
                + "  CreateOrgResult createOrg(CreateOrgInput input);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

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
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.SearchApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface SearchApi {\n"
                + "  @GraphqlQuery(\"query searchOrgs($criteria: OrgSearchCriteria!) {"
                + " searchOrganizations(criteria: $criteria) {"
                + " id name"
                + " departments { id name lead { id name } tags { key value } }"
                + " metadata { foundedYear categories { name parentCategory { name } } }"
                + " } }\")\n"
                + "  SearchResult searchOrganizations(OrgSearchCriteria criteria);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.SearchResult");
    assertThat(compilation).generatedSourceFile("test.OrgSearchCriteria");
    assertThat(compilation).generatedSourceFile("test.DepartmentFilterInput");
    assertThat(compilation).generatedSourceFile("test.TagInput");
  }

  @Test
  void listReturnTypeGeneratesElementType() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ListReturnApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "import java.util.List;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface ListReturnApi {\n"
                + "  @GraphqlQuery(\"query listUsers($filter: UserFilter) { users(filter: $filter) {"
                + " id name email status } }\")\n"
                + "  List<UserListResult> listUsers(UserFilter filter);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.UserListResult");
    assertThat(compilation).generatedSourceFile("test.UserFilter");
  }

  @Test
  void existingExternalTypeSkipsGeneration() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ExternalTypeApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface ExternalTypeApi {\n"
                + "  @GraphqlQuery(\"mutation createUser($input: CreateUserInput!) {"
                + " createUser(input: $input) { id name email } }\")\n"
                + "  CreateResult createUser(feign.graphql.GraphqlQuery input);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.CreateResult");
  }

  @Test
  void userWithOrganizationMultipleLevelReuse() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ReuseApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"test-schema.graphql\")\n"
                + "interface ReuseApi {\n"
                + "  @GraphqlQuery(\"{ user(id: \\\"1\\\") {"
                + " id name status"
                + " address { street city country coordinates { latitude longitude } }"
                + " organization {"
                + "   id name"
                + "   address { street city coordinates { latitude longitude } }"
                + "   departments { id name members { id name } }"
                + " }"
                + " } }\")\n"
                + "  FullUserResult getFullUser();\n"
                + "\n"
                + "  @GraphqlQuery(\"query listUsers($filter: UserFilter) { users(filter: $filter) {"
                + " id name email tags"
                + " } }\")\n"
                + "  UserListResult listUsers(UserFilter filter);\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.FullUserResult");
    assertThat(compilation).generatedSourceFile("test.Status");
  }

  @Test
  void scalarAnnotationMapsCustomScalar() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ScalarApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "import feign.graphql.Scalar;\n"
                + "\n"
                + "@GraphqlSchema(\"scalar-test-schema.graphql\")\n"
                + "interface ScalarApi {\n"
                + "  @Scalar(\"DateTime\")\n"
                + "  default String dateTime(String raw) { return raw; }\n"
                + "\n"
                + "  @GraphqlQuery(\"{ event(id: \\\"1\\\") { id name startTime endTime } }\")\n"
                + "  EventResult getEvent();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.EventResult");
  }

  @Test
  void missingScalarAnnotationReportsError() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.MissingScalarApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"scalar-test-schema.graphql\")\n"
                + "interface MissingScalarApi {\n"
                + "  @GraphqlQuery(\"{ event(id: \\\"1\\\") { id name startTime } }\")\n"
                + "  EventResult getEvent();\n"
                + "}\n");

    Compilation compilation = javac().withProcessors(new GraphqlSchemaProcessor()).compile(source);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Custom scalar 'DateTime'");
    assertThat(compilation).hadErrorContaining("@Scalar(\"DateTime\")");
  }

  @Test
  void scalarFromParentInterfaceIsInherited() {
    JavaFileObject parentSource =
        JavaFileObjects.forSourceString(
            "test.ScalarDefinitions",
            "package test;\n"
                + "\n"
                + "import feign.graphql.Scalar;\n"
                + "\n"
                + "interface ScalarDefinitions {\n"
                + "  @Scalar(\"DateTime\")\n"
                + "  default String dateTime(String raw) { return raw; }\n"
                + "}\n");

    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "test.ChildApi",
            "package test;\n"
                + "\n"
                + "import feign.graphql.GraphqlSchema;\n"
                + "import feign.graphql.GraphqlQuery;\n"
                + "\n"
                + "@GraphqlSchema(\"scalar-test-schema.graphql\")\n"
                + "interface ChildApi extends ScalarDefinitions {\n"
                + "  @GraphqlQuery(\"{ event(id: \\\"1\\\") { id name startTime } }\")\n"
                + "  EventResult getEvent();\n"
                + "}\n");

    Compilation compilation =
        javac().withProcessors(new GraphqlSchemaProcessor()).compile(parentSource, source);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.EventResult");
  }
}
