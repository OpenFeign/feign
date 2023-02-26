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
package feign.sax;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Util;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.helpers.DefaultHandler;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import feign.Response;
import feign.codec.Decoder;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("deprecation")
public class SAXDecoderTest {

  static String statusFailed = ""//
      + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
      //
      + "  <soap:Body>\n"//
      + "    <ns1:getNeustarNetworkStatusResponse xmlns:ns1=\"http://webservice.api.ultra.neustar.com/v01/\">\n"
      //
      + "      <NeustarNetworkStatus xmlns:ns2=\"http://schema.ultraservice.neustar.com/v01/\">Failed</NeustarNetworkStatus>\n"
      //
      + "    </ns1:getNeustarNetworkStatusResponse>\n"//
      + "  </soap:Body>\n"//
      + "</soap:Envelope>";
  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  Decoder decoder = SAXDecoder.builder() //
      .registerContentHandler(NetworkStatus.class,
          new SAXDecoder.ContentHandlerWithResult.Factory<NetworkStatus>() {
            @Override
            public SAXDecoder.ContentHandlerWithResult<NetworkStatus> create() {
              return new NetworkStatusHandler();
            }
          }) //
      .registerContentHandler(NetworkStatusStringHandler.class) //
      .build();

  @Test
  public void parsesConfiguredTypes() throws ParseException, IOException {
    assertEquals(NetworkStatus.FAILED, decoder.decode(statusFailedResponse(), NetworkStatus.class));
    assertEquals("Failed", decoder.decode(statusFailedResponse(), String.class));
  }

  @Test
  public void niceErrorOnUnconfiguredType() throws ParseException, IOException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("type int not in configured handlers");

    decoder.decode(statusFailedResponse(), int.class);
  }

  private Response statusFailedResponse() {
    return Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .body(statusFailed, UTF_8)
        .build();
  }

  @Test
  public void nullBodyDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .build();
    assertThat((byte[]) decoder.decode(response, byte[].class)).isEmpty();
  }

  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .build();
    assertThat((byte[]) decoder.decode(response, byte[].class)).isEmpty();
  }

  static enum NetworkStatus {
    GOOD, FAILED;
  }

  static class NetworkStatusStringHandler extends DefaultHandler implements
      SAXDecoder.ContentHandlerWithResult<String> {

    private StringBuilder currentText = new StringBuilder();

    private String status;

    @Override
    public String result() {
      return status;
    }

    @Override
    public void endElement(String uri, String name, String qName) {
      if (qName.equals("NeustarNetworkStatus")) {
        this.status = currentText.toString().trim();
      }
      currentText = new StringBuilder();
    }

    @Override
    public void characters(char ch[], int start, int length) {
      currentText.append(ch, start, length);
    }
  }

  static class NetworkStatusHandler extends DefaultHandler implements
      SAXDecoder.ContentHandlerWithResult<NetworkStatus> {

    private StringBuilder currentText = new StringBuilder();

    private NetworkStatus status;

    @Override
    public NetworkStatus result() {
      return status;
    }

    @Override
    public void endElement(String uri, String name, String qName) {
      if (qName.equals("NeustarNetworkStatus")) {
        this.status = NetworkStatus.valueOf(currentText.toString().trim().toUpperCase());
      }
      currentText = new StringBuilder();
    }

    @Override
    public void characters(char ch[], int start, int length) {
      currentText.append(ch, start, length);
    }
  }
}
