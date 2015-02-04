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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static feign.Util.CONTENT_LENGTH;
import static feign.Util.UTF_8;
import static feign.Util.checkNotNull;
import static feign.Util.emptyToNull;
import static feign.Util.toArray;
import static feign.Util.valuesOrEmpty;

/**
 * Builds a request to an http target. Not thread safe. <br> <br><br><b>relationship to JAXRS
 * 2.0</b><br> <br> A combination of {@code javax.ws.rs.client.WebTarget} and {@code
 * javax.ws.rs.client.Invocation.Builder}, ensuring you can modify any part of the request. However,
 * this object is mutable, so needs to be guarded with the copy constructor.
 */
public final class RequestTemplate implements Serializable {

  private static final long serialVersionUID = 1L;
  private final Map<String, Collection<String>>
      queries =
      new LinkedHashMap<String, Collection<String>>();
  private final Map<String, Collection<String>>
      headers =
      new LinkedHashMap<String, Collection<String>>();
  private String method;
  /* final to encourage mutable use vs replacing the object. */
  private StringBuilder url = new StringBuilder();
  private transient Charset charset;
  private byte[] body;
  private String bodyTemplate;

  public RequestTemplate() {

  }

  /* Copy constructor. Use this when making templates. */
  public RequestTemplate(RequestTemplate toCopy) {
    checkNotNull(toCopy, "toCopy");
    this.method = toCopy.method;
    this.url.append(toCopy.url);
    this.queries.putAll(toCopy.queries);
    this.headers.putAll(toCopy.headers);
    this.charset = toCopy.charset;
    this.body = toCopy.body;
    this.bodyTemplate = toCopy.bodyTemplate;
  }

  private static String urlDecode(String arg) {
    try {
      return URLDecoder.decode(arg, UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String urlEncode(Object arg) {
    try {
      return URLEncoder.encode(String.valueOf(arg), UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Expands a {@code template}, such as {@code username}, using the {@code variables} supplied. Any
   * unresolved parameters will remain. <br> Note that if you'd like curly braces literally in the
   * {@code template}, urlencode them first.
   *
   * @param template  URI template that can be in level 1 <a href="http://tools.ietf.org/html/rfc6570">RFC6570</a>
   *                  form.
   * @param variables to the URI template
   * @return expanded template, leaving any unresolved parameters literal
   */
  public static String expand(String template, Map<String, ?> variables) {
    // skip expansion if there's no valid variables set. ex. {a} is the
    // first valid
    if (checkNotNull(template, "template").length() < 3) {
      return template.toString();
    }
    checkNotNull(variables, "variables for %s", template);

    boolean inVar = false;
    StringBuilder var = new StringBuilder();
    StringBuilder builder = new StringBuilder();
    for (char c : template.toCharArray()) {
      switch (c) {
        case '{':
          inVar = true;
          break;
        case '}':
          inVar = false;
          String key = var.toString();
          Object value = variables.get(var.toString());
          if (value != null) {
            builder.append(value);
          } else {
            builder.append('{').append(key).append('}');
          }
          var = new StringBuilder();
          break;
        default:
          if (inVar) {
            var.append(c);
          } else {
            builder.append(c);
          }
      }
    }
    return builder.toString();
  }

  private static Map<String, Collection<String>> parseAndDecodeQueries(String queryLine) {
    Map<String, Collection<String>> map = new LinkedHashMap<String, Collection<String>>();
    if (emptyToNull(queryLine) == null) {
      return map;
    }
    if (queryLine.indexOf('&') == -1) {
      if (queryLine.indexOf('=') != -1) {
        putKV(queryLine, map);
      } else {
        map.put(queryLine, null);
      }
    } else {
      char[] chars = queryLine.toCharArray();
      int start = 0;
      int i = 0;
      for (; i < chars.length; i++) {
        if (chars[i] == '&') {
          putKV(queryLine.substring(start, i), map);
          start = i + 1;
        }
      }
      putKV(queryLine.substring(start, i), map);
    }
    return map;
  }

  private static void putKV(String stringToParse, Map<String, Collection<String>> map) {
    String key;
    String value;
    // note that '=' can be a valid part of the value
    int firstEq = stringToParse.indexOf('=');
    if (firstEq == -1) {
      key = urlDecode(stringToParse);
      value = null;
    } else {
      key = urlDecode(stringToParse.substring(0, firstEq));
      value = urlDecode(stringToParse.substring(firstEq + 1));
    }
    Collection<String> values = map.containsKey(key) ? map.get(key) : new ArrayList<String>();
    values.add(value);
    map.put(key, values);
  }

  /**
   * Resolves any template parameters in the requests path, query, or headers against the supplied
   * unencoded arguments. <br> <br><br><b>relationship to JAXRS 2.0</b><br> <br> This call is
   * similar to {@code javax.ws.rs.client.WebTarget.resolveTemplates(templateValues, true)} , except
   * that the template values apply to any part of the request, not just the URL
   */
  public RequestTemplate resolve(Map<String, ?> unencoded) {
    replaceQueryValues(unencoded);
    Map<String, String> encoded = new LinkedHashMap<String, String>();
    for (Entry<String, ?> entry : unencoded.entrySet()) {
      encoded.put(entry.getKey(), urlEncode(String.valueOf(entry.getValue())));
    }
    String resolvedUrl = expand(url.toString(), encoded).replace("%2F", "/");
    url = new StringBuilder(resolvedUrl);

    Map<String, Collection<String>>
        resolvedHeaders =
        new LinkedHashMap<String, Collection<String>>();
    for (String field : headers.keySet()) {
      Collection<String> resolvedValues = new ArrayList<String>();
      for (String value : valuesOrEmpty(headers, field)) {
        String resolved;
        if (value.indexOf('{') == 0) {
          resolved = expand(value, unencoded);
        } else {
          resolved = value;
        }
        if (resolved != null) {
          resolvedValues.add(resolved);
        }
      }
      resolvedHeaders.put(field, resolvedValues);
    }
    headers.clear();
    headers.putAll(resolvedHeaders);
    if (bodyTemplate != null) {
      body(urlDecode(expand(bodyTemplate, unencoded)));
    }
    return this;
  }

  /* roughly analogous to {@code javax.ws.rs.client.Target.request()}. */
  public Request request() {
    return new Request(method, new StringBuilder(url).append(queryLine()).toString(),
                       headers, body, charset);
  }

  /* @see Request#method() */
  public RequestTemplate method(String method) {
    this.method = checkNotNull(method, "method");
    return this;
  }

  /* @see Request#method() */
  public String method() {
    return method;
  }

  /* @see #url() */
  public RequestTemplate append(CharSequence value) {
    url.append(value);
    url = pullAnyQueriesOutOfUrl(url);
    return this;
  }

  /* @see #url() */
  public RequestTemplate insert(int pos, CharSequence value) {
    url.insert(pos, pullAnyQueriesOutOfUrl(new StringBuilder(value)));
    return this;
  }

  public String url() {
    return url.toString();
  }

  /**
   * Replaces queries with the specified {@code configKey} with url decoded {@code values} supplied.
   * <br> When the {@code value} is {@code null}, all queries with the {@code configKey} are
   * removed. <br> <br><br><b>relationship to JAXRS 2.0</b><br> <br> Like {@code WebTarget.query},
   * except the values can be templatized. <br> ex. <br>
   * <pre>
   * template.query(&quot;Signature&quot;, &quot;{signature}&quot;);
   * </pre>
   *
   * @param configKey the configKey of the query
   * @param values    can be a single null to imply removing all values. Else no values are expected
   *                  to be null.
   * @see #queries()
   */
  public RequestTemplate query(String configKey, String... values) {
    queries.remove(checkNotNull(configKey, "configKey"));
    if (values != null && values.length > 0 && values[0] != null) {
      ArrayList<String> encoded = new ArrayList<String>();
      for (String value : values) {
        encoded.add(encodeIfNotVariable(value));
      }
      this.queries.put(encodeIfNotVariable(configKey), encoded);
    }
    return this;
  }

  /* @see #query(String, String...) */
  public RequestTemplate query(String configKey, Iterable<String> values) {
    if (values != null) {
      return query(configKey, toArray(values, String.class));
    }
    return query(configKey, (String[]) null);
  }

  private String encodeIfNotVariable(String in) {
    if (in == null || in.indexOf('{') == 0) {
      return in;
    }
    return urlEncode(in);
  }

  /**
   * Replaces all existing queries with the newly supplied url decoded queries. <br>
   * <br><br><b>relationship to JAXRS 2.0</b><br> <br> Like {@code WebTarget.queries}, except the
   * values can be templatized. <br> ex. <br>
   * <pre>
   * template.queries(ImmutableMultimap.of(&quot;Signature&quot;, &quot;{signature}&quot;));
   * </pre>
   *
   * @param queries if null, remove all queries. else value to replace all queries with.
   * @see #queries()
   */
  public RequestTemplate queries(Map<String, Collection<String>> queries) {
    if (queries == null || queries.isEmpty()) {
      this.queries.clear();
    } else {
      for (Entry<String, Collection<String>> entry : queries.entrySet()) {
        query(entry.getKey(), toArray(entry.getValue(), String.class));
      }
    }
    return this;
  }

  /**
   * Returns an immutable copy of the url decoded queries.
   *
   * @see Request#url()
   */
  public Map<String, Collection<String>> queries() {
    Map<String, Collection<String>> decoded = new LinkedHashMap<String, Collection<String>>();
    for (String field : queries.keySet()) {
      Collection<String> decodedValues = new ArrayList<String>();
      for (String value : valuesOrEmpty(queries, field)) {
        if (value != null) {
          decodedValues.add(urlDecode(value));
        } else {
          decodedValues.add(null);
        }
      }
      decoded.put(urlDecode(field), decodedValues);
    }
    return Collections.unmodifiableMap(decoded);
  }

  /**
   * Replaces headers with the specified {@code configKey} with the {@code values} supplied. <br>
   * When the {@code value} is {@code null}, all headers with the {@code configKey} are removed.
   * <br> <br><br><b>relationship to JAXRS 2.0</b><br> <br> Like {@code WebTarget.queries} and
   * {@code javax.ws.rs.client.Invocation.Builder.header}, except the values can be templatized.
   * <br> ex. <br>
   * <pre>
   * template.query(&quot;X-Application-Version&quot;, &quot;{version}&quot;);
   * </pre>
   *
   * @param name   the name of the header
   * @param values can be a single null to imply removing all values. Else no values are expected to
   *               be null.
   * @see #headers()
   */
  public RequestTemplate header(String name, String... values) {
    checkNotNull(name, "header name");
    if (values == null || (values.length == 1 && values[0] == null)) {
      headers.remove(name);
    } else {
      List<String> headers = new ArrayList<String>();
      headers.addAll(Arrays.asList(values));
      this.headers.put(name, headers);
    }
    return this;
  }

  /* @see #header(String, String...) */
  public RequestTemplate header(String name, Iterable<String> values) {
    if (values != null) {
      return header(name, toArray(values, String.class));
    }
    return header(name, (String[]) null);
  }

  /**
   * Replaces all existing headers with the newly supplied headers. <br> <br><br><b>relationship to
   * JAXRS 2.0</b><br> <br> Like {@code Invocation.Builder.headers(MultivaluedMap)}, except the
   * values can be templatized. <br> ex. <br>
   * <pre>
   * template.headers(ImmutableMultimap.of(&quot;X-Application-Version&quot;,
   * &quot;{version}&quot;));
   * </pre>
   *
   * @param headers if null, remove all headers. else value to replace all headers with.
   * @see #headers()
   */
  public RequestTemplate headers(Map<String, Collection<String>> headers) {
    if (headers == null || headers.isEmpty()) {
      this.headers.clear();
    } else {
      this.headers.putAll(headers);
    }
    return this;
  }

  /**
   * Returns an immutable copy of the current headers.
   *
   * @see Request#headers()
   */
  public Map<String, Collection<String>> headers() {
    return Collections.unmodifiableMap(headers);
  }

  /**
   * replaces the {@link feign.Util#CONTENT_LENGTH} header. <br> Usually populated by an {@link
   * feign.codec.Encoder}.
   *
   * @see Request#body()
   */
  public RequestTemplate body(byte[] bodyData, Charset charset) {
    this.bodyTemplate = null;
    this.charset = charset;
    this.body = bodyData;
    int bodyLength = bodyData != null ? bodyData.length : 0;
    header(CONTENT_LENGTH, String.valueOf(bodyLength));
    return this;
  }

  /**
   * replaces the {@link feign.Util#CONTENT_LENGTH} header. <br> Usually populated by an {@link
   * feign.codec.Encoder}.
   *
   * @see Request#body()
   */
  public RequestTemplate body(String bodyText) {
    byte[] bodyData = bodyText != null ? bodyText.getBytes(UTF_8) : null;
    return body(bodyData, UTF_8);
  }

  /**
   * The character set with which the body is encoded, or null if unknown or not applicable.  When
   * this is present, you can use {@code new String(req.body(), req.charset())} to access the body
   * as a String.
   */
  public Charset charset() {
    return charset;
  }

  /**
   * @see Request#body()
   */
  public byte[] body() {
    return body;
  }

  /**
   * populated by {@link Body}
   *
   * @see Request#body()
   */
  public RequestTemplate bodyTemplate(String bodyTemplate) {
    this.bodyTemplate = bodyTemplate;
    this.charset = null;
    this.body = null;
    return this;
  }

  /**
   * @see Request#body()
   * @see #expand(String, Map)
   */
  public String bodyTemplate() {
    return bodyTemplate;
  }

  /**
   * if there are any query params in the URL, this will extract them out.
   */
  private StringBuilder pullAnyQueriesOutOfUrl(StringBuilder url) {
    // parse out queries
    int queryIndex = url.indexOf("?");
    if (queryIndex != -1) {
      String queryLine = url.substring(queryIndex + 1);
      Map<String, Collection<String>> firstQueries = parseAndDecodeQueries(queryLine);
      if (!queries.isEmpty()) {
        firstQueries.putAll(queries);
        queries.clear();
      }
      //Since we decode all queries, we want to use the
      //query()-method to re-add them to ensure that all
      //logic (such as url-encoding) are executed, giving
      //a valid queryLine()
      for (String key : firstQueries.keySet()) {
        Collection<String> values = firstQueries.get(key);
        if (allValuesAreNull(values)) {
          //Queryies where all values are null will
          //be ignored by the query(key, value)-method
          //So we manually avoid this case here, to ensure that
          //we still fulfill the contract (ex. parameters without values)
          queries.put(urlEncode(key), values);
        } else {
          query(key, values);
        }

      }
      return new StringBuilder(url.substring(0, queryIndex));
    }
    return url;
  }

  private boolean allValuesAreNull(Collection<String> values) {
    if (values.isEmpty()) {
      return true;
    }
    for (String val : values) {
      if (val != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return request().toString();
  }

  /**
   * Replaces query values which are templated with corresponding values from the {@code unencoded}
   * map. Any unresolved queries are removed.
   */
  public void replaceQueryValues(Map<String, ?> unencoded) {
    Iterator<Entry<String, Collection<String>>> iterator = queries.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, Collection<String>> entry = iterator.next();
      if (entry.getValue() == null) {
        continue;
      }
      Collection<String> values = new ArrayList<String>();
      for (String value : entry.getValue()) {
        if (value.indexOf('{') == 0 && value.indexOf('}') == value.length() - 1) {
          Object variableValue = unencoded.get(value.substring(1, value.length() - 1));
          // only add non-null expressions
          if (variableValue == null) {
            continue;
          }
          if (variableValue instanceof Iterable) {
            for (Object val : Iterable.class.cast(variableValue)) {
              values.add(urlEncode(String.valueOf(val)));
            }
          } else {
            values.add(urlEncode(String.valueOf(variableValue)));
          }
        } else {
          values.add(value);
        }
      }
      if (values.isEmpty()) {
        iterator.remove();
      } else {
        entry.setValue(values);
      }
    }
  }

  public String queryLine() {
    if (queries.isEmpty()) {
      return "";
    }
    StringBuilder queryBuilder = new StringBuilder();
    for (String field : queries.keySet()) {
      for (String value : valuesOrEmpty(queries, field)) {
        queryBuilder.append('&');
        queryBuilder.append(field);
        if (value != null) {
          queryBuilder.append('=');
          if (!value.isEmpty()) {
            queryBuilder.append(value);
          }
        }
      }
    }
    queryBuilder.deleteCharAt(0);
    return queryBuilder.insert(0, '?').toString();
  }

  interface Factory {

    /**
     * create a request template using args passed to a method invocation.
     */
    RequestTemplate create(Object[] argv);
  }
}
