/**
 * Copyright 2012-2019 The Feign Authors
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
package feign.template;


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.Charset;
import org.junit.Test;

public class UriUtilsTest {


  /**
   * Verify that values outside of the allowed characters in a path segment are pct-encoded. The
   * list of approved characters used are those listed in <a
   * href="https://tools.ietf.org/html/rfc3986#appendix-A>RFC 3986 Appendix A</a>
   */
  @Test
  public void pctEncodeReservedPathCharacters() {
    /*
     * the only not allowed characters are the general delimiters, with the exception of slash,
     * question, colon and at
     */
    String reservedPath = "/api/user@host:port#section[a-z]/data";
    String reservedPathEncoded = UriUtils.pathEncode(reservedPath, UTF_8);

    /*
     * the result should be the path, with the slash and question retained, but all other special
     * characters encoded
     */
    assertThat(reservedPathEncoded).isEqualTo("/api/user@host:port%23section%5Ba-z%5D/data");

  }

  /**
   * Verify that the list of allowed characters in a path segment are not pct-encoded, as they don't
   * have to be. The list of approved characters used are those listed in <a
   * href="https://tools.ietf.org/html/rfc3986#appendix-A>RFC 3986 Appendix A</a>
   */
  @Test
  public void ensureApprovedPathParametersAreNotEncoded() {
    /* the approved list is 'any pchar' */
    String pchar =
        "abcdefghijklmnopqrstuvwxyZABCDEFGHIJKLMNOPQRSTUVWXYZZ0123456789_-~.!$&'()*+,;=:@%2B";
    String pathEncoded = UriUtils.pathEncode(pchar, UTF_8);

    /* the result here should be nothing has been changed */
    assertThat(pathEncoded).isEqualTo(pchar);

  }

  /**
   * Verify that when a full query string is provided, only unapproved characters are pct-encoded.
   * This differs from {@link UriUtils#queryParamEncode(String, Charset)} in that this method
   * adheres to specification exactly, whereas the other is more restrictive.
   *
   * The list of approved characters used are those listed in <a
   * href="https://tools.ietf.org/html/rfc3986#appendix-A>RFC 3986 Appendix A</a>
   */
  @Test
  public void pctEncodeQueryString() {
    String query = "?name=James Bond&occupation=Spy&location=Great Britain!";
    assertThat(UriUtils.queryEncode(query, UTF_8))
        .isEqualToIgnoringCase("?name=James%20Bond&occupation=Spy&location=Great%20Britain!");
  }

  /**
   * Verify that a value meant for a query string parameter is pct-encoded using the defined query
   * set of allowed characters, including equals, ampersands and question marks as in Feign, we
   * manage the creation of the key value pair.
   *
   * The list of approved characters used are those listed in <a
   * href="https://tools.ietf.org/html/rfc3986#appendix-A>RFC 3986 Appendix A</a>
   */
  @Test
  public void pctEncodeQueryParameterValue() {
    String queryParameterValue = "firstName=James;lastName=Bond;location=England&Britain?";
    assertThat(UriUtils.queryParamEncode(queryParameterValue, UTF_8))
        .isEqualToIgnoringCase("firstName%3DJames;lastName%3DBond;location%3DEngland%26Britain%3F");
  }
}
