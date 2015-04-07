package feign.benchmark;

import feign.Headers;
import feign.Response;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Consumes("application/json")
interface JAXRSTestInterface {

  @GET
  @Path("/?Action=GetUser&Version=2010-05-08&limit=1")
  Response query();

  @GET
  @Path("/domains/{domainId}/records")
  Response mixedParams(
      @PathParam("domainId") int id,
      @QueryParam("name") String nameFilter,
      @QueryParam("type") String typeFilter);

  @PATCH
  Response customMethod();

  @Target({ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @HttpMethod("PATCH")
  @interface PATCH {}

  @PUT
  @Produces("application/json")
  void bodyParam(List<String> body);

  @POST
  void form(
      @FormParam("customer_name") String customer,
      @FormParam("user_name") String user,
      @FormParam("password") String password);

  @POST
  @Headers("Happy: sad")
  void headers(@HeaderParam("Auth-Token") String token);
}
