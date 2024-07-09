/*
 * Copyright 2012-2024 The Feign Authors
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
package feign.soap;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import feign.jaxb.JAXBContextFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.ws.soap.SOAPFaultException;

@SuppressWarnings("deprecation")
class SOAPFaultDecoderTest {

  private static byte[] getResourceBytes(String resourcePath) throws IOException {
    InputStream resourceAsStream = SOAPFaultDecoderTest.class.getResourceAsStream(resourcePath);
    byte[] bytes = new byte[resourceAsStream.available()];
    new DataInputStream(resourceAsStream).readFully(bytes);
    return bytes;
  }

  @Test
  void soapDecoderThrowsSOAPFaultException() throws IOException {

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(getResourceBytes("/samples/SOAP_1_2_FAULT.xml"))
        .build();

    SOAPDecoder decoder =
        new SOAPDecoder.Builder().withSOAPProtocol(SOAPConstants.SOAP_1_2_PROTOCOL)
            .withJAXBContextFactory(new JAXBContextFactory.Builder().build()).build();

    Throwable exception = assertThrows(SOAPFaultException.class, () -> decoder
        .decode(response, Object.class));
    assertThat(exception.getMessage()).contains("Processing error");
  }

  @Test
  void errorDecoderReturnsSOAPFaultException() throws IOException {
    Response response = Response.builder()
        .status(400)
        .reason("BAD REQUEST")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(getResourceBytes("/samples/SOAP_1_1_FAULT.xml"))
        .build();

    Exception error =
        new SOAPErrorDecoder().decode("Service#foo()", response);
    assertThat(error).isInstanceOf(SOAPFaultException.class)
        .hasMessage("Message was not SOAP 1.1 compliant");
  }

  @Test
  void errorDecoderReturnsFeignExceptionOn503Status() throws IOException {
    Response response = Response.builder()
        .status(503)
        .reason("Service Unavailable")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body("Service Unavailable", UTF_8)
        .build();

    Exception error =
        new SOAPErrorDecoder().decode("Service#foo()", response);

    assertThat(error).isInstanceOf(FeignException.class)
        .hasMessage(
            "[503 Service Unavailable] during [GET] to [/api] [Service#foo()]: [Service Unavailable]");
  }

  @Test
  void errorDecoderReturnsFeignExceptionOnEmptyFault() throws IOException {
    String responseBody = """
        <?xml version = '1.0' encoding = 'UTF-8'?>
        <SOAP-ENV:Envelope
           xmlns:SOAP-ENV = "http://schemas.xmlsoap.org/soap/envelope/"
           xmlns:xsi = "http://www.w3.org/1999/XMLSchema-instance"
           xmlns:xsd = "http://www.w3.org/1999/XMLSchema">
           <SOAP-ENV:Body>
           </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>\
        """;
    Response response = Response.builder()
        .status(500)
        .reason("Internal Server Error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(responseBody, UTF_8)
        .build();

    Exception error =
        new SOAPErrorDecoder().decode("Service#foo()", response);

    assertThat(error).isInstanceOf(FeignException.class)
        .hasMessage("[500 Internal Server Error] during [GET] to [/api] [Service#foo()]: ["
            + responseBody + "]");
  }

}
