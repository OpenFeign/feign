package feign.ribbon;

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
    String method = "GET";
    URI uri = new URI(urlWithEncodedJson);
    Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
    // create a Request for recreating another Request by toRequest()
    Request requestOrigin = Request.create(method, uri.toASCIIString(), headers, null, Charset.forName("utf-8")); 
    RibbonRequest ribbonRequest = new RibbonRequest(null, requestOrigin, uri);

    // use toRequest() recreate a Request
    Request requestRecreate = ribbonRequest.toRequest();

    // test that requestOrigin and requestRecreate are same except the header 'Content-Length'
    // ps, requestOrigin and requestRecreate won't be null
    assertThat(requestOrigin.toString()).isEqualTo(String.format("%s %s HTTP/1.1\n", method, urlWithEncodedJson));
    assertThat(requestRecreate.toString()).isEqualTo(String.format("%s %s HTTP/1.1\nContent-Length: 0\n", method, urlWithEncodedJson));
  }
}
