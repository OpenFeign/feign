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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Arrays;

import static feign.RequestTemplate.expand;
import static org.testng.Assert.assertEquals;

public class RequestTemplateTest {
  @Test public void expandNotUrlEncoded() {
    for (String val : ImmutableList.of("apples", "sp ace", "unic???de", "qu?stion"))
      assertEquals(expand("/users/{user}", ImmutableMap.of("user", val)), "/users/" + val);
  }

  @Test public void expandMultipleParams() {
    assertEquals(expand("/users/{user}/{repo}", ImmutableMap.of("user", "unic???de", "repo", "foo")),
        "/users/unic???de/foo");
  }

  @Test public void expandParamKeyHyphen() {
    assertEquals(expand("/{user-dir}", ImmutableMap.of("user-dir", "foo")), "/foo");
  }

  @Test public void expandMissingParamProceeds() {
    assertEquals(expand("/{user-dir}", ImmutableMap.of("user_dir", "foo")), "/{user-dir}");
  }

  @Test public void resolveTemplateWithParameterizedPathSkipsEncodingSlash() {

    RequestTemplate template = new RequestTemplate().method("GET")
        .append("{zoneId}");

    assertEquals(template.toString(), ""//
        + "GET {zoneId} HTTP/1.1\n");

    template.resolve(ImmutableMap.of("zoneId", "/hostedzone/Z1PA6795UKMFR9"));

    assertEquals(template.toString(), ""//
        + "GET /hostedzone/Z1PA6795UKMFR9 HTTP/1.1\n");

    template.insert(0, "https://route53.amazonaws.com/2012-12-12");

    assertEquals(template.request().toString(), ""//
        + "GET https://route53.amazonaws.com/2012-12-12/hostedzone/Z1PA6795UKMFR9 HTTP/1.1\n");
  }

  @Test public void resolveTemplateWithBaseAndParameterizedQuery() {
    RequestTemplate template = new RequestTemplate().method("GET")
        .append("/?Action=DescribeRegions").query("RegionName.1", "{region}");

    assertEquals(template.queries(),
        ImmutableListMultimap.of("Action", "DescribeRegions", "RegionName.1", "{region}").asMap());
    assertEquals(template.toString(), ""//
        + "GET /?Action=DescribeRegions&RegionName.1={region} HTTP/1.1\n");

    template.resolve(ImmutableMap.of("region", "eu-west-1"));
    assertEquals(template.queries(),
        ImmutableListMultimap.of("Action", "DescribeRegions", "RegionName.1", "eu-west-1").asMap());

    assertEquals(template.toString(), ""//
        + "GET /?Action=DescribeRegions&RegionName.1=eu-west-1 HTTP/1.1\n");

    template.insert(0, "https://iam.amazonaws.com");

    assertEquals(template.request().toString(), ""//
        + "GET https://iam.amazonaws.com/?Action=DescribeRegions&RegionName.1=eu-west-1 HTTP/1.1\n");
  }

  @Test public void resolveTemplateWithBaseAndParameterizedIterableQuery() {
    RequestTemplate template = new RequestTemplate().method("GET")
        .append("/?Query=one").query("Queries", "{queries}");

    template.resolve(ImmutableMap.of("queries", Arrays.asList("us-east-1", "eu-west-1")));
    assertEquals(template.queries(),
        ImmutableListMultimap.<String, String> builder()
                             .put("Query", "one")
                             .putAll("Queries", "us-east-1", "eu-west-1")
                             .build().asMap());

    assertEquals(template.toString(), "GET /?Query=one&Queries=us-east-1&Queries=eu-west-1 HTTP/1.1\n");
  }

  @Test public void resolveTemplateWithMixedRequestLineParams() throws Exception {
    RequestTemplate template = new RequestTemplate().method("GET")//
        .append("/domains/{domainId}/records")//
        .query("name", "{name}")//
        .query("type", "{type}");

    template = template.resolve(ImmutableMap.<String, Object>builder()//
        .put("domainId", 1001)//
        .put("name", "denominator.io")//
        .put("type", "CNAME")//
        .build()
    );

    assertEquals(template.toString(), ""//
        + "GET /domains/1001/records?name=denominator.io&type=CNAME HTTP/1.1\n");

    template.insert(0, "https://dns.api.rackspacecloud.com/v1.0/1234");

    assertEquals(template.request().toString(), ""//
        + "GET https://dns.api.rackspacecloud.com/v1.0/1234"//
        + "/domains/1001/records?name=denominator.io&type=CNAME HTTP/1.1\n");
  }

  @Test public void insertHasQueryParams() throws Exception {
    RequestTemplate template = new RequestTemplate().method("GET")//
        .append("/domains/{domainId}/records")//
        .query("name", "{name}")//
        .query("type", "{type}");

    template = template.resolve(ImmutableMap.<String, Object>builder()//
        .put("domainId", 1001)//
        .put("name", "denominator.io")//
        .put("type", "CNAME")//
        .build()
    );

    assertEquals(template.toString(), ""//
        + "GET /domains/1001/records?name=denominator.io&type=CNAME HTTP/1.1\n");

    template.insert(0, "https://host/v1.0/1234?provider=foo");

    assertEquals(template.request().toString(), ""//
        + "GET https://host/v1.0/1234/domains/1001/records?provider=foo&name=denominator.io&type=CNAME HTTP/1.1\n");
  }

  @Test public void resolveTemplateWithBodyTemplateSetsBodyAndContentLength() {
    RequestTemplate template = new RequestTemplate().method("POST")
        .bodyTemplate("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", " +
            "\"password\": \"{password}\"%7D");

    template = template.resolve(ImmutableMap.<String, Object>builder()//
        .put("customer_name", "netflix")//
        .put("user_name", "denominator")//
        .put("password", "password")//
        .build()
    );

    assertEquals(template.toString(), ""//
        + "POST  HTTP/1.1\n"//
        + "Content-Length: 80\n"//
        + "\n"//
        + "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");

    template.insert(0, "https://api2.dynect.net/REST");

    assertEquals(template.request().toString(), ""//
        + "POST https://api2.dynect.net/REST HTTP/1.1\n" //
        + "Content-Length: 80\n" //
        + "\n" //
        + "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
  }

  @Test public void skipUnresolvedQueries() throws Exception {
    RequestTemplate template = new RequestTemplate().method("GET")//
        .append("/domains/{domainId}/records")//
        .query("optional", "{optional}")//
        .query("name", "{nameVariable}");

    template = template.resolve(ImmutableMap.<String, Object>builder()//
        .put("domainId", 1001)//
        .put("nameVariable", "denominator.io")//
        .build()
    );

    assertEquals(template.toString(), ""//
        + "GET /domains/1001/records?name=denominator.io HTTP/1.1\n");
  }

  @Test public void allQueriesUnresolvable() throws Exception {
    RequestTemplate template = new RequestTemplate().method("GET")//
        .append("/domains/{domainId}/records")//
        .query("optional", "{optional}")//
        .query("optional2", "{optional2}");

    template = template.resolve(ImmutableMap.<String, Object>builder()//
        .put("domainId", 1001)//
        .build()
    );

    assertEquals(template.toString(), ""//
        + "GET /domains/1001/records HTTP/1.1\n");
  }
}
