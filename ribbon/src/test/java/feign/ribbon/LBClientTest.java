package feign.ribbon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import feign.Request;
import feign.ribbon.LBClient.RibbonRequest;

public class LBClientTest {

  @Test
  public void testRibbonRequest() throws URISyntaxException {
    // test for RibbonRequest.toRequest()
    // the url has a query whose value is an encoded json string
    String urlWithEncodedJson = "http://test.feign.com/p?q=%7b%22a%22%3a1%7d";
    URI uri = new URI(urlWithEncodedJson);
    Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    // create a Request as baseline for recreate and assert
    Request requestOrign = Request.create("GET", uri.toASCIIString(), headers, null, Charset.forName("utf-8")); 
    RibbonRequest ribbonRequest = new RibbonRequest(null, requestOrign, uri);

    // use toRequest() recreate a Request
    Request requestGetFormToRequest = ribbonRequest.toRequest();

    // test the same attributes
    assertEquals(requestOrign.method(), requestGetFormToRequest.method());
    assertEquals(requestOrign.url(), requestGetFormToRequest.url());
    assertEquals(urlWithEncodedJson, requestGetFormToRequest.url()); // the url in Request should not been encoded or decoded
    assertEquals(requestOrign.body(), requestGetFormToRequest.body());
    assertEquals(requestOrign.charset(), requestGetFormToRequest.charset());
    assertNotNull(requestOrign.headers());
    assertEquals(0, requestOrign.headers().size()); // double check the baseline Request
    assertNotNull(requestGetFormToRequest.headers()); // double check the baseline Request, header is not null, but empty

    // test the different attributes
    // use toRequest() will add a "Content-Length" header, test it
    Map<String, Collection<String>> headersFromNewRequest = requestGetFormToRequest.headers();
    assertNotNull(headersFromNewRequest);
    assertEquals(1, headersFromNewRequest.size());
    assertTrue(headersFromNewRequest.containsKey("Content-Length"));
    Collection<String> contentLengthValues = headersFromNewRequest.get("Content-Length");
    assertNotNull(contentLengthValues);
    assertEquals(1, contentLengthValues.size()); // should has one value
    assertEquals("0", contentLengthValues.iterator().next()); // the body is null, so the Content-Length value should be 0
  }
}
