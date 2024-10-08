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
package feign.jaxrs2;

import static feign.assertj.FeignAssertions.assertThat;

import feign.MethodMetadata;
import feign.jaxrs.JAXRSContract;
import feign.jaxrs.JAXRSContractTest;
import feign.jaxrs2.JAXRS2ContractTest.Jaxrs2Internals.Input;
import javax.ws.rs.BeanParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

/**
 * Tests interfaces defined per {@link JAXRS2Contract} are interpreted into expected {@link feign
 * .RequestTemplate template} instances.
 */
class JAXRS2ContractTest extends JAXRSContractTest {

  @Override
  protected JAXRSContract createContract() {
    return new JAXRS2Contract();
  }

  @Test
  void injectJaxrsInternals() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(
            Jaxrs2Internals.class, "inject", AsyncResponse.class, UriInfo.class);
    assertThat(methodMetadata.template()).noRequestBody();
  }

  @Test
  void injectBeanParam() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(Jaxrs2Internals.class, "beanParameters", Input.class);
    assertThat(methodMetadata.template()).noRequestBody();
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
