/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.ribbon;

import feign.Request.HttpMethod;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import feign.Request;
import feign.ribbon.LBClient.RibbonRequest;
import static org.assertj.core.api.Assertions.assertThat;

public class LBClientTest {

  @Test
  public void testParseCodes() {
    assertThat(LBClient.parseStatusCodes("")).isEmpty();
    assertThat(LBClient.parseStatusCodes(null)).isEmpty();
    assertThat(LBClient.parseStatusCodes("504")).contains(504);
    assertThat(LBClient.parseStatusCodes("503,504")).contains(503, 504);
  }

  @Test
  public void testRibbonRequest() throws URISyntaxException {
    // test for RibbonRequest.toRequest()
    // the url has a query whose value is an encoded json string
    String urlWithEncodedJson = "http://test.feign.com/p?q=%7b%22a%22%3a1%7d";
    HttpMethod method = HttpMethod.GET;
    URI uri = new URI(urlWithEncodedJson);
    Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
    // create a Request for recreating another Request by toRequest()
    Request requestOrigin =
        Request.create(method, uri.toASCIIString(), headers, null, Charset.forName("utf-8"));
    RibbonRequest ribbonRequest = new RibbonRequest(null, requestOrigin, uri);

    // use toRequest() recreate a Request
    Request requestRecreate = ribbonRequest.toRequest();

    // test that requestOrigin and requestRecreate are same except the header 'Content-Length'
    // ps, requestOrigin and requestRecreate won't be null
    assertThat(requestOrigin.toString())
        .contains(String.format("%s %s HTTP/1.1\n", method, urlWithEncodedJson));
    assertThat(requestRecreate.toString())
        .contains(String.format("%s %s HTTP/1.1\nContent-Length: 0\n", method, urlWithEncodedJson));
  }
}
