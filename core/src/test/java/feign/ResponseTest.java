/*
 * Copyright 2013 Netflix, Inc.
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
package feign;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static feign.assertj.FeignAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ResponseTest {

  @Test
  public void reasonPhraseIsOptional() {
    Response response = Response.create(200, null /* reason phrase */, Collections.
        <String, Collection<String>>emptyMap(), new byte[0]);

    assertThat(response.reason()).isNull();
    assertThat(response.toString()).isEqualTo("HTTP/1.1 200\n\n");
  }

  @Test
  public void lowerCasesNamesOfHeaders() {
    Response response = Response.create(200,
                                        null,
                                        Collections.singletonMap("Content-Type",
                                                                 Collections.singletonList("application/json")),
                                        new byte[0]);
    assertThat(response.headers()).containsOnly(entry(("content-type"), Collections.singletonList("application/json")));
  }

  @Test
  public void canAccessHeadersCaseInsensitively() {
    List<String> valueList = Collections.singletonList("application/json");
    Response response = Response.create(200,
                                        null,
                                        Collections.singletonMap("Content-Type", valueList),
                                        new byte[0]);
    assertThat(response.headers().get("content-type")).isEqualTo(valueList);
    assertThat(response.headers().get("Content-Type")).isEqualTo(valueList);
  }

  @Test
  public void headerValuesWithSameNameOnlyVaryingInCaseAreMerged() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    headersMap.put("Set-Cookie", Arrays.asList("Cookie-A=Value", "Cookie-B=Value"));
    headersMap.put("set-cookie", Arrays.asList("Cookie-C=Value"));

    Response response = Response.create(200, null, headersMap, new byte[0]);

    List<String> expectedHeaderValue = Arrays.asList("Cookie-A=Value", "Cookie-B=Value", "Cookie-C=Value");
    assertThat(response.headers()).containsOnly(entry(("set-cookie"), expectedHeaderValue));
  }
}
