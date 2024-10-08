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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.Feign;
import feign.Feign.Builder;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.jaxrs.JAXRSContract;
import java.util.Collections;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class JAXRSClientTest extends AbstractJAXRSClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new JAXRSClient());
  }

  @Test
  void consumesMultipleWithContentTypeHeaderAndBody() throws Exception {
    server.enqueue(new MockResponse().setBody("AAAAAAAA"));
    final JaxRSClientTestInterfaceWithJaxRsContract api =
        newBuilder()
            .contract(new JAXRSContract()) // use JAXRSContract
            .target(
                JaxRSClientTestInterfaceWithJaxRsContract.class,
                "http://localhost:" + server.getPort());

    final Response response =
        api.consumesMultipleWithContentTypeHeaderAndBody("application/json;charset=utf-8", "body");
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(
            MapEntry.entry(
                "Content-Type", Collections.singletonList("application/json;charset=utf-8")))
        .hasMethod("POST");
  }

  public interface JaxRSClientTestInterfaceWithJaxRsContract {
    @Path("/")
    @POST
    @Consumes({"application/xml", "application/json"})
    Response consumesMultipleWithContentTypeHeaderAndBody(
        @HeaderParam("Content-Type") String contentType, String body);
  }
}
