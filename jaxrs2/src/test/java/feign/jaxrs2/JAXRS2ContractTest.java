/**
 * Copyright 2012-2020 The Feign Authors
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

import static feign.assertj.FeignAssertions.assertThat;
import org.assertj.core.data.MapEntry;
import org.junit.Test;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import feign.MethodMetadata;
import feign.jaxrs.JAXRSContract;
import feign.jaxrs.JAXRSContractTest;
import feign.jaxrs2.JAXRS2ContractTest.Jaxrs2Internals.Input;

/**
 * Tests interfaces defined per {@link JAXRS2Contract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public class JAXRS2ContractTest extends JAXRSContractTest {

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
        parseAndValidateMetadata(Jaxrs2Internals.class, "beanParameters", Input.class);
    assertThat(methodMetadata.template())
        .noRequestBody();
  }


  @Path("/")
  public interface Jaxrs2Internals {
    @GET
    void inject(@Suspended AsyncResponse ar, @Context UriInfo info);

    @POST
    void beanParameters(@BeanParam Input input);

    public class Input {

      @QueryParam("query")
      String query;

      @FormParam("form")
      String form;

      @HeaderParam("header")
      String header;

    }
  }

}
