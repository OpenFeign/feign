/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.jaxrs2;

import feign.MethodMetadata;
import feign.jaxrs.JAXRSContract;
import feign.jaxrs.JAXRSContractTest;
import feign.jaxrs2.JAXRS2ContractWithBeanParamSupportTest.Jaxrs2Internals.BeanParamInput;
import org.junit.Test;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.data.MapEntry.entry;

/**
 * Tests interfaces defined per {@link JAXRS2Contract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public class JAXRS2ContractWithBeanParamSupportTest extends JAXRSContractTest {

  @Override
  protected JAXRSContract createContract() {
    return new JAXRS2Contract();
  }

  @Test
  public void injectJaxrsInternals() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(Jaxrs2Internals.class, "inject", AsyncResponse.class,
            UriInfo.class);
    assertThat(methodMetadata.template())
        .noRequestBody();
  }

  @Test
  public void injectBeanParam() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(Jaxrs2Internals.class, "beanParameters", BeanParamInput.class);
    assertThat(methodMetadata.template())
        .noRequestBody();

    assertThat(methodMetadata.template())
        .hasHeaders(entry("X-Custom-Header", asList("{X-Custom-Header}")));
    assertThat(methodMetadata.template())
        .hasQueries(entry("query", asList("{query}")));
    assertThat(methodMetadata.formParams())
        .isNotEmpty()
        .containsExactly("form");

  }

  public interface Jaxrs2Internals {
    @GET
    @Path("/")
    void inject(@Suspended AsyncResponse ar, @Context UriInfo info);

    @Path("/{path}")
    @POST
    void beanParameters(@BeanParam BeanParamInput beanParam);

    public class BeanParamInput {

      @PathParam("path")
      String path;

      @QueryParam("query")
      String query;

      @FormParam("form")
      String form;

      @HeaderParam("X-Custom-Header")
      String header;
    }
  }

}
