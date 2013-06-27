package feign;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static feign.Contract.parseAndValidatateMetadata;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import feign.RequestTemplate.Body;
import java.net.URI;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.testng.annotations.Test;

@Test
public class ContractTest {

  static interface Methods {
    @POST
    void post();

    @PUT
    void put();

    @GET
    void get();

    @DELETE
    void delete();
  }

  @Test
  public void httpMethods() throws Exception {
    assertEquals(
        parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post")).template().method(),
        POST);
    assertEquals(
        parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put")).template().method(),
        PUT);
    assertEquals(
        parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get")).template().method(),
        GET);
    assertEquals(
        parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete")).template().method(),
        DELETE);
  }

  static interface WithQueryParamsInPath {
    @GET
    @Path("/?Action=GetUser&Version=2010-05-08")
    Response get();
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("get"));
    assertEquals(md.template().url(), "/");
    assertEquals(md.template().queries().get("Action"), ImmutableSet.of("GetUser"));
    assertEquals(md.template().queries().get("Version"), ImmutableSet.of("2010-05-08"));
  }

  static interface BodyWithoutParameters {
    @POST
    @Produces(APPLICATION_XML)
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Test
  public void bodyWithoutParameters() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals(md.template().body().get(), "<v01:getAccountsListOfUser/>");
    assertFalse(md.template().bodyTemplate().isPresent());
    assertTrue(md.formParams().isEmpty());
    assertTrue(md.indexToName().isEmpty());
  }

  @Test
  public void producesAddsContentTypeHeader() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals(md.template().headers().get(CONTENT_TYPE), ImmutableSet.of(APPLICATION_XML));
  }

  static interface WithURIParam {
    @GET
    @Path("/{1}/{2}")
    Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
  }

  @Test
  public void methodCanHaveUriParam() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(
            WithURIParam.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class));
    assertEquals(md.urlIndex(), Integer.valueOf(1));
  }

  @Test
  public void pathParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(
            WithURIParam.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class));
    assertEquals(md.template().url(), "/{1}/{2}");
    assertEquals(md.indexToName().get(0), ImmutableSet.of("1"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("2"));
  }

  static interface FormParams {
    @POST
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    void login(@FormParam("customer_name") String customer, @FormParam("user_name") String user);
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(
            FormParams.class.getDeclaredMethod("login", String.class, String.class, String.class));

    assertFalse(md.template().body().isPresent());
    assertEquals(
        md.template().bodyTemplate().get(),
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D");
    assertEquals(md.formParams(), ImmutableList.of("customer_name", "user_name", "password"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("customer_name"));
    assertEquals(md.indexToName().get(1), ImmutableSet.of("user_name"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("password"));
  }

  static interface HeaderParams {
    @POST
    void logout(@HeaderParam("Auth-Token") String token);
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        parseAndValidatateMetadata(HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertEquals(md.template().headers().get("Auth-Token"), ImmutableSet.of("{Auth-Token}"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("Auth-Token"));
  }
}
