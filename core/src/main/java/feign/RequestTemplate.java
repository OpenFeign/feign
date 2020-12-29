/**
 * Copyright 2012-2020 The Feign Authors
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
package feign;

import feign.Request.HttpMethod;
import feign.template.*;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static feign.Util.*;

/**
 * Request Builder for an HTTP Target.
 * <p>
 * This class is a variation on a UriTemplate, where, in addition to the uri, Headers and Query
 * information also support template expressions.
 * </p>
 */
@SuppressWarnings("UnusedReturnValue")
public final class RequestTemplate implements Serializable {

  private static final Pattern QUERY_STRING_PATTERN = Pattern.compile("(?<!\\{)\\?");
  private final Map<String, QueryTemplate> queries = new LinkedHashMap<>();
  private final Map<String, HeaderTemplate> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private String target;
  private String fragment;
  private boolean resolved = false;
  private UriTemplate uriTemplate;
  private BodyTemplate bodyTemplate;
  private HttpMethod method;
  private transient Charset charset = Util.UTF_8;
  private Request.Body body = Request.Body.empty();
  private boolean decodeSlash = true;
  private CollectionFormat collectionFormat = CollectionFormat.EXPLODED;
  private MethodMetadata methodMetadata;
  private Target<?> feignTarget;

  /**
   * Create a new Request Template.
   */
  public RequestTemplate() {
    super();
  }

  /**
   * Create a new Request Template.
   *
   * @param fragment part of the request uri.
   * @param target for the template.
   * @param uriTemplate for the template.
   * @param bodyTemplate for the template, may be {@literal null}
   * @param method of the request.
   * @param charset for the request.
   * @param body of the request, may be {@literal null}
   * @param decodeSlash if the request uri should encode slash characters.
   * @param collectionFormat when expanding collection based variables.
   * @param feignTarget this template is targeted for.
   * @param methodMetadata containing a reference to the method this template is built from.
   */
  private RequestTemplate(String target,
      String fragment,
      UriTemplate uriTemplate,
      BodyTemplate bodyTemplate,
      HttpMethod method,
      Charset charset,
      Request.Body body,
      boolean decodeSlash,
      CollectionFormat collectionFormat,
      MethodMetadata methodMetadata,
      Target<?> feignTarget) {
    this.target = target;
    this.fragment = fragment;
    this.uriTemplate = uriTemplate;
    this.bodyTemplate = bodyTemplate;
    this.method = method;
    this.charset = charset;
    this.body = body;
    this.decodeSlash = decodeSlash;
    this.collectionFormat =
        (collectionFormat != null) ? collectionFormat : CollectionFormat.EXPLODED;
    this.methodMetadata = methodMetadata;
    this.feignTarget = feignTarget;
  }

  /**
   * Create a Request Template from an existing Request Template.
   *
   * @param requestTemplate to copy from.
   * @return a new Request Template.
   */
  public static RequestTemplate from(RequestTemplate requestTemplate) {
    RequestTemplate template =
        new RequestTemplate(
            requestTemplate.target,
            requestTemplate.fragment,
            requestTemplate.uriTemplate,
            requestTemplate.bodyTemplate,
            requestTemplate.method,
            requestTemplate.charset,
            requestTemplate.body,
            requestTemplate.decodeSlash,
            requestTemplate.collectionFormat,
            requestTemplate.methodMetadata,
            requestTemplate.feignTarget);

    if (!requestTemplate.queries().isEmpty()) {
      template.queries.putAll(requestTemplate.queries);
    }

    if (!requestTemplate.headers().isEmpty()) {
      template.headers.putAll(requestTemplate.headers);
    }
    return template;
  }

  /**
   * Create a Request Template from an existing Request Template.
   *
   * @param toCopy template.
   * @deprecated replaced by {@link RequestTemplate#from(RequestTemplate)}
   */
  @Deprecated
  public RequestTemplate(RequestTemplate toCopy) {
    checkNotNull(toCopy, "toCopy");
    this.target = toCopy.target;
    this.fragment = toCopy.fragment;
    this.method = toCopy.method;
    this.queries.putAll(toCopy.queries);
    this.headers.putAll(toCopy.headers);
    this.charset = toCopy.charset;
    this.body = toCopy.body;
    this.decodeSlash = toCopy.decodeSlash;
    this.collectionFormat =
        (toCopy.collectionFormat != null) ? toCopy.collectionFormat : CollectionFormat.EXPLODED;
    this.uriTemplate = toCopy.uriTemplate;
    this.bodyTemplate = toCopy.bodyTemplate;
    this.resolved = false;
    this.methodMetadata = toCopy.methodMetadata;
    this.target = toCopy.target;
    this.feignTarget = toCopy.feignTarget;
  }

  /**
   * Resolve all expressions using the variable value substitutions provided. Variable values will
   * be pct-encoded, if they are not already.
   *
   * @param variables containing the variable values to use when resolving expressions.
   * @return a new Request Template with all of the variables resolved.
   */
  public RequestTemplate resolve(Map<String, ?> variables) {

    StringBuilder uri = new StringBuilder();

    /* create a new template form this one, but explicitly */
    RequestTemplate resolved = RequestTemplate.from(this);

    if (this.uriTemplate == null) {
      /* create a new uri template using the default root */
      this.uriTemplate = UriTemplate.create("", !this.decodeSlash, this.charset);
    }

    String expanded = this.uriTemplate.expand(variables);
    if (expanded != null) {
      uri.append(expanded);
    }

    /*
     * for simplicity, combine the queries into the uri and use the resulting uri to seed the
     * resolved template.
     */
    if (!this.queries.isEmpty()) {
      /*
       * since we only want to keep resolved query values, reset any queries on the resolved copy
       */
      resolved.queries(Collections.emptyMap());
      StringBuilder query = new StringBuilder();
      Iterator<QueryTemplate> queryTemplates = this.queries.values().iterator();

      while (queryTemplates.hasNext()) {
        QueryTemplate queryTemplate = queryTemplates.next();
        String queryExpanded = queryTemplate.expand(variables);
        if (Util.isNotBlank(queryExpanded)) {
          query.append(queryExpanded);
          if (queryTemplates.hasNext()) {
            query.append("&");
          }
        }
      }

      String queryString = query.toString();
      if (!queryString.isEmpty()) {
        Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
        if (queryMatcher.find()) {
          /* the uri already has a query, so any additional queries should be appended */
          uri.append("&");
        } else {
          uri.append("?");
        }
        uri.append(queryString);
      }
    }

    /* add the uri to result */
    resolved.uri(uri.toString());

    /* headers */
    if (!this.headers.isEmpty()) {
      /*
       * same as the query string, we only want to keep resolved values, so clear the header map on
       * the resolved instance
       */
      resolved.headers(Collections.emptyMap());
      for (HeaderTemplate headerTemplate : this.headers.values()) {
        /* resolve the header */
        String header = headerTemplate.expand(variables);
        if (!header.isEmpty()) {
          /* append the header as a new literal as the value has already been expanded. */
          resolved.header(headerTemplate.getName(), header);
        }
      }
    }

    if (this.bodyTemplate != null) {
      resolved.body(this.bodyTemplate.expand(variables));
    }

    /* mark the new template resolved */
    resolved.resolved = true;
    return resolved;
  }

  /**
   * Resolves all expressions, using the variables provided. Values not present in the {@code
   * alreadyEncoded} map are pct-encoded.
   *
   * @param unencoded variable values to substitute.
   * @param alreadyEncoded variable names.
   * @return a resolved Request Template
   * @deprecated use {@link RequestTemplate#resolve(Map)}. Values already encoded are recognized as
   *             such and skipped.
   */
  @SuppressWarnings("unused")
  @Deprecated
  RequestTemplate resolve(Map<String, ?> unencoded, Map<String, Boolean> alreadyEncoded) {
    return this.resolve(unencoded);
  }

  /**
   * Creates a {@link Request} from this template. The template must be resolved before calling this
   * method, or an {@link IllegalStateException} will be thrown.
   *
   * @return a new Request instance.
   * @throws IllegalStateException if this template has not been resolved.
   */
  public Request request() {
    if (!this.resolved) {
      throw new IllegalStateException("template has not been resolved.");
    }
    return Request.create(this.method, this.url(), this.headers(), this.body, this);
  }

  /**
   * Set the Http Method.
   *
   * @param method to use.
   * @return a RequestTemplate for chaining.
   * @deprecated see {@link RequestTemplate#method(HttpMethod)}
   */
  @Deprecated
  public RequestTemplate method(String method) {
    checkNotNull(method, "method");
    try {
      this.method = HttpMethod.valueOf(method);
    } catch (IllegalArgumentException iae) {
      throw new IllegalArgumentException("Invalid HTTP Method: " + method);
    }
    return this;
  }

  /**
   * Set the Http Method.
   *
   * @param method to use.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate method(HttpMethod method) {
    checkNotNull(method, "method");
    this.method = method;
    return this;
  }

  /**
   * The Request Http Method.
   *
   * @return Http Method.
   */
  public String method() {
    return (method != null) ? method.name() : null;
  }

  /**
   * Set whether do encode slash {@literal /} characters when resolving this template.
   *
   * @param decodeSlash if slash literals should not be encoded.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate decodeSlash(boolean decodeSlash) {
    this.decodeSlash = decodeSlash;
    this.uriTemplate =
        UriTemplate.create(this.uriTemplate.toString(), !this.decodeSlash, this.charset);
    if (!this.queries.isEmpty()) {
      this.queries.replaceAll((key, queryTemplate) -> QueryTemplate.create(
          /* replace the current template with new ones honoring the decode value */
          queryTemplate.getName(), queryTemplate.getValues(), charset, collectionFormat,
          decodeSlash));

    }
    return this;
  }

  /**
   * If slash {@literal /} characters are not encoded when resolving.
   *
   * @return true if slash literals are not encoded, false otherwise.
   */
  public boolean decodeSlash() {
    return decodeSlash;
  }

  /**
   * The Collection Format to use when resolving variables that represent {@link Iterable}s or
   * {@link Collection}s
   *
   * @param collectionFormat to use.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate collectionFormat(CollectionFormat collectionFormat) {
    this.collectionFormat = collectionFormat;
    return this;
  }

  /**
   * The Collection Format that will be used when resolving {@link Iterable} and {@link Collection}
   * variables.
   *
   * @return the collection format set
   */
  @SuppressWarnings("unused")
  public CollectionFormat collectionFormat() {
    return collectionFormat;
  }

  /**
   * Append the value to the template.
   * <p>
   * This method is poorly named and is used primarily to store the relative uri for the request. It
   * has been replaced by {@link RequestTemplate#uri(String)} and will be removed in a future
   * release.
   * </p>
   *
   * @param value to append.
   * @return a RequestTemplate for chaining.
   * @deprecated see {@link RequestTemplate#uri(String, boolean)}
   */
  @Deprecated
  public RequestTemplate append(CharSequence value) {
    /* proxy to url */
    if (this.uriTemplate != null) {
      return this.uri(value.toString(), true);
    }
    return this.uri(value.toString());
  }

  /**
   * Insert the value at the specified point in the template uri.
   * <p>
   * This method is poorly named has undocumented behavior. When the value contains a fully
   * qualified http request url, the value is always inserted at the beginning of the uri.
   * </p>
   * <p>
   * Due to this, use of this method is not recommended and remains for backward compatibility. It
   * has been replaced by {@link RequestTemplate#target(String)} and will be removed in a future
   * release.
   * </p>
   *
   * @param pos in the uri to place the value.
   * @param value to insert.
   * @return a RequestTemplate for chaining.
   * @deprecated see {@link RequestTemplate#target(String)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public RequestTemplate insert(int pos, CharSequence value) {
    return target(value.toString());
  }

  /**
   * Set the Uri for the request, replacing the existing uri if set.
   *
   * @param uri to use, must be a relative uri.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate uri(String uri) {
    return this.uri(uri, false);
  }

  /**
   * Set the uri for the request.
   *
   * @param uri to use, must be a relative uri.
   * @param append if the uri should be appended, if the uri is already set.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate uri(String uri, boolean append) {
    /* validate and ensure that the url is always a relative one */
    if (UriUtils.isAbsolute(uri)) {
      throw new IllegalArgumentException("url values must be not be absolute.");
    }

    if (uri == null) {
      uri = "/";
    } else if ((!uri.isEmpty() && !uri.startsWith("/") && !uri.startsWith("{")
        && !uri.startsWith("?") && !uri.startsWith(";"))) {
      /* if the start of the url is a literal, it must begin with a slash. */
      uri = "/" + uri;
    }

    /*
     * templates may provide query parameters. since we want to manage those explicity, we will need
     * to extract those out, leaving the uriTemplate with only the path to deal with.
     */
    Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
    if (queryMatcher.find()) {
      String queryString = uri.substring(queryMatcher.start() + 1);

      /* parse the query string */
      this.extractQueryTemplates(queryString, append);

      /* reduce the uri to the path */
      uri = uri.substring(0, queryMatcher.start());
    }

    int fragmentIndex = uri.indexOf('#');
    if (fragmentIndex > -1) {
      fragment = uri.substring(fragmentIndex);
      uri = uri.substring(0, fragmentIndex);
    }

    /* replace the uri template */
    if (append && this.uriTemplate != null) {
      this.uriTemplate = UriTemplate.append(this.uriTemplate, uri);
    } else {
      this.uriTemplate = UriTemplate.create(uri, !this.decodeSlash, this.charset);
    }
    return this;
  }

  /**
   * Set the target host for this request.
   *
   * @param target host for this request. Must be an absolute target.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate target(String target) {
    /* target can be empty */
    if (Util.isBlank(target)) {
      return this;
    }

    /* verify that the target contains the scheme, host and port */
    if (!UriUtils.isAbsolute(target)) {
      throw new IllegalArgumentException("target values must be absolute.");
    }
    if (target.endsWith("/")) {
      target = target.substring(0, target.length() - 1);
    }
    try {
      /* parse the target */
      URI targetUri = URI.create(target);

      if (Util.isNotBlank(targetUri.getRawQuery())) {
        /*
         * target has a query string, we need to make sure that they are recorded as queries
         */
        this.extractQueryTemplates(targetUri.getRawQuery(), true);
      }

      /* strip the query string */
      this.target = targetUri.getScheme() + "://" + targetUri.getAuthority() + targetUri.getPath();
      if (targetUri.getFragment() != null) {
        this.fragment = "#" + targetUri.getFragment();
      }
    } catch (IllegalArgumentException iae) {
      /* the uri provided is not a valid one, we can't continue */
      throw new IllegalArgumentException("Target is not a valid URI.", iae);
    }
    return this;
  }

  /**
   * The URL for the request. If the template has not been resolved, the url will represent a uri
   * template.
   *
   * @return the url
   */
  public String url() {

    /* build the fully qualified url with all query parameters */
    StringBuilder url = new StringBuilder(this.path());
    if (!this.queries.isEmpty()) {
      url.append(this.queryLine());
    }
    if (fragment != null) {
      url.append(fragment);
    }

    return url.toString();
  }

  /**
   * The Uri Path.
   *
   * @return the uri path.
   */
  public String path() {
    /* build the fully qualified url with all query parameters */
    StringBuilder path = new StringBuilder();
    if (this.target != null) {
      path.append(this.target);
    }
    if (this.uriTemplate != null) {
      path.append(this.uriTemplate.toString());
    }
    if (path.length() == 0) {
      /* no path indicates the root uri */
      path.append("/");
    }
    return path.toString();

  }

  /**
   * List all of the template variable expressions for this template.
   *
   * @return a list of template variable names
   */
  public List<String> variables() {
    /* combine the variables from the uri, query, header, and body templates */
    List<String> variables = new ArrayList<>(this.uriTemplate.getVariables());

    /* queries */
    for (QueryTemplate queryTemplate : this.queries.values()) {
      variables.addAll(queryTemplate.getVariables());
    }

    /* headers */
    for (HeaderTemplate headerTemplate : this.headers.values()) {
      variables.addAll(headerTemplate.getVariables());
    }

    /* body */
    if (this.bodyTemplate != null) {
      variables.addAll(this.bodyTemplate.getVariables());
    }

    return variables;
  }

  /**
   * @see RequestTemplate#query(String, Iterable)
   */
  public RequestTemplate query(String name, String... values) {
    if (values == null) {
      return query(name, Collections.emptyList());
    }
    return query(name, Arrays.asList(values));
  }


  /**
   * Specify a Query String parameter, with the specified values. Values can be literals or template
   * expressions.
   *
   * @param name of the parameter.
   * @param values for this parameter.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate query(String name, Iterable<String> values) {
    return appendQuery(name, values, this.collectionFormat);
  }

  /**
   * Specify a Query String parameter, with the specified values. Values can be literals or template
   * expressions.
   *
   * @param name of the parameter.
   * @param values for this parameter.
   * @param collectionFormat to use when resolving collection based expressions.
   * @return a Request Template for chaining.
   */
  public RequestTemplate query(String name,
                               Iterable<String> values,
                               CollectionFormat collectionFormat) {
    return appendQuery(name, values, collectionFormat);
  }

  /**
   * Appends the query name and values.
   *
   * @param name of the parameter.
   * @param values for the parameter, may be expressions.
   * @param collectionFormat to use when resolving collection based query variables.
   * @return a RequestTemplate for chaining.
   */
  private RequestTemplate appendQuery(String name,
                                      Iterable<String> values,
                                      CollectionFormat collectionFormat) {
    if (!values.iterator().hasNext()) {
      /* empty value, clear the existing values */
      this.queries.remove(name);
      return this;
    }

    /* create a new query template out of the information here */
    this.queries.compute(name, (key, queryTemplate) -> {
      if (queryTemplate == null) {
        return QueryTemplate.create(name, values, this.charset, collectionFormat, this.decodeSlash);
      } else {
        return QueryTemplate.append(queryTemplate, values, collectionFormat, this.decodeSlash);
      }
    });
    return this;
  }

  /**
   * Sets the Query Parameters.
   *
   * @param queries to use for this request.
   * @return a RequestTemplate for chaining.
   */
  @SuppressWarnings("unused")
  public RequestTemplate queries(Map<String, Collection<String>> queries) {
    if (queries == null || queries.isEmpty()) {
      this.queries.clear();
    } else {
      queries.forEach(this::query);
    }
    return this;
  }


  /**
   * Return an immutable Map of all Query Parameters and their values.
   *
   * @return registered Query Parameters.
   */
  public Map<String, Collection<String>> queries() {
    Map<String, Collection<String>> queryMap = new LinkedHashMap<>();
    this.queries.forEach((key, queryTemplate) -> {
      List<String> values = new ArrayList<>(queryTemplate.getValues());

      /* add the expanded collection, but lock it */
      queryMap.put(key, Collections.unmodifiableList(values));
    });

    return Collections.unmodifiableMap(queryMap);
  }

  /**
   * @see RequestTemplate#header(String, Iterable)
   */
  public RequestTemplate header(String name, String... values) {
    return header(name, Arrays.asList(values));
  }

  /**
   * Specify a Header, with the specified values. Values can be literals or template expressions.
   *
   * @param name of the header.
   * @param values for this header.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate header(String name, Iterable<String> values) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }
    if (values == null) {
      values = Collections.emptyList();
    }

    return appendHeader(name, values);
  }

  /**
   * Clear on reader from {@link RequestTemplate}
   *
   * @param name of the header.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate removeHeader(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }
    this.headers.remove(name);
    return this;
  }

  /**
   * Create a Header Template.
   *
   * @param name of the header
   * @param values for the header, may be expressions.
   * @return a RequestTemplate for chaining.
   */
  private RequestTemplate appendHeader(String name, Iterable<String> values) {
    if (!values.iterator().hasNext()) {
      /* empty value, clear the existing values */
      this.headers.remove(name);
      return this;
    }
    if (name.equals("Content-Type")) {
      // a client can only produce content of one single type, so always override Content-Type and
      // only add a single type
      this.headers.remove(name);
      this.headers.put(name,
          HeaderTemplate.create(name, Collections.singletonList(values.iterator().next())));
      return this;
    }
    this.headers.compute(name, (headerName, headerTemplate) -> {
      if (headerTemplate == null) {
        return HeaderTemplate.create(headerName, values);
      } else {
        return HeaderTemplate.append(headerTemplate, values);
      }
    });
    return this;
  }

  /**
   * Headers for this Request.
   *
   * @param headers to use.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate headers(Map<String, Collection<String>> headers) {
    if (headers != null && !headers.isEmpty()) {
      headers.forEach(this::header);
    } else {
      this.headers.clear();
    }
    return this;
  }

  /**
   * Returns an immutable copy of the Headers for this request.
   *
   * @return the currently applied headers.
   */
  public Map<String, Collection<String>> headers() {
    Map<String, Collection<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.headers.forEach((key, headerTemplate) -> {
      List<String> values = new ArrayList<>(headerTemplate.getValues());

      /* add the expanded collection, but only if it has values */
      if (!values.isEmpty()) {
        headerMap.put(key, Collections.unmodifiableList(values));
      }
    });
    return Collections.unmodifiableMap(headerMap);
  }

  /**
   * Sets the Body and Charset for this request.
   *
   * @param data to send, can be null.
   * @param charset of the encoded data.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate body(byte[] data, Charset charset) {
    this.body(Request.Body.create(data, charset));
    return this;
  }

  /**
   * Set the Body for this request. Charset is assumed to be UTF_8. Data must be encoded.
   *
   * @param bodyText to send.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate body(String bodyText) {
    this.body(Request.Body.create(bodyText.getBytes(this.charset), this.charset));
    return this;
  }

  /**
   * Set the Body for this request.
   *
   * @param body to send.
   * @return a RequestTemplate for chaining.
   * @deprecated use {@link #body(byte[], Charset)} instead.
   */
  @Deprecated
  public RequestTemplate body(Request.Body body) {
    this.body = body;

    /* body template must be cleared to prevent double processing */
    this.bodyTemplate = null;

    header(CONTENT_LENGTH, Collections.emptyList());
    if (body.length() > 0) {
      header(CONTENT_LENGTH, String.valueOf(body.length()));
    }

    return this;
  }

  /**
   * Charset of the Request Body, if known.
   *
   * @return the currently applied Charset.
   */
  public Charset requestCharset() {
    if (this.body != null) {
      return this.body.getEncoding()
          .orElse(this.charset);
    }
    return this.charset;
  }

  /**
   * The Request Body.
   *
   * @return the request body.
   */
  public byte[] body() {
    return body.asBytes();
  }

  /**
   * The Request.Body internal object.
   *
   * @return the internal Request.Body.
   * @deprecated this abstraction is leaky and will be removed in later releases.
   */
  @Deprecated
  public Request.Body requestBody() {
    return this.body;
  }


  /**
   * Specify the Body Template to use. Can contain literals and expressions.
   *
   * @param bodyTemplate to use.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate bodyTemplate(String bodyTemplate) {
    this.bodyTemplate = BodyTemplate.create(bodyTemplate, this.charset);
    return this;
  }

  /**
   * Specify the Body Template to use. Can contain literals and expressions.
   *
   * @param bodyTemplate to use.
   * @return a RequestTemplate for chaining.
   */
  public RequestTemplate bodyTemplate(String bodyTemplate, Charset charset) {
    this.bodyTemplate = BodyTemplate.create(bodyTemplate, charset);
    this.charset = charset;
    return this;
  }

  /**
   * Body Template to resolve.
   *
   * @return the unresolved body template.
   */
  public String bodyTemplate() {
    if (this.bodyTemplate != null) {
      return this.bodyTemplate.toString();
    }
    return null;
  }

  @Override
  public String toString() {
    return request().toString();
  }

  /**
   * Return if the variable exists on the uri, query, or headers, in this template.
   *
   * @param variable to look for.
   * @return true if the variable exists, false otherwise.
   */
  public boolean hasRequestVariable(String variable) {
    return this.getRequestVariables().contains(variable);
  }

  /**
   * Retrieve all uri, header, and query template variables.
   *
   * @return a List of all the variable names.
   */
  public Collection<String> getRequestVariables() {
    final Collection<String> variables = new LinkedHashSet<>(this.uriTemplate.getVariables());
    this.queries.values().forEach(queryTemplate -> variables.addAll(queryTemplate.getVariables()));
    this.headers.values()
        .forEach(headerTemplate -> variables.addAll(headerTemplate.getVariables()));
    return variables;
  }

  /**
   * If this template has been resolved.
   *
   * @return true if the template has been resolved, false otherwise.
   */
  @SuppressWarnings("unused")
  public boolean resolved() {
    return this.resolved;
  }

  /**
   * The Query String for the template. Expressions are not resolved.
   *
   * @return the Query String.
   */
  public String queryLine() {
    StringBuilder queryString = new StringBuilder();

    if (!this.queries.isEmpty()) {
      Iterator<QueryTemplate> iterator = this.queries.values().iterator();
      while (iterator.hasNext()) {
        QueryTemplate queryTemplate = iterator.next();
        String query = queryTemplate.toString();
        if (query != null && !query.isEmpty()) {
          queryString.append(query);
          if (iterator.hasNext()) {
            queryString.append("&");
          }
        }
      }
    }
    /* remove any trailing ampersands */
    String result = queryString.toString();
    if (result.endsWith("&")) {
      result = result.substring(0, result.length() - 1);
    }

    if (!result.isEmpty()) {
      result = "?" + result;
    }

    return result;
  }

  private void extractQueryTemplates(String queryString, boolean append) {
    /* split the query string up into name value pairs */
    Map<String, List<String>> queryParameters =
        Arrays.stream(queryString.split("&"))
            .map(this::splitQueryParameter)
            .collect(Collectors.groupingBy(
                SimpleImmutableEntry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Entry::getValue, Collectors.toList())));

    /* add them to this template */
    if (!append) {
      /* clear the queries and use the new ones */
      this.queries.clear();
    }
    queryParameters.forEach(this::query);
  }

  private SimpleImmutableEntry<String, String> splitQueryParameter(String pair) {
    int eq = pair.indexOf("=");
    final String name = (eq > 0) ? pair.substring(0, eq) : pair;
    final String value = (eq > 0 && eq < pair.length()) ? pair.substring(eq + 1) : null;
    return new SimpleImmutableEntry<>(name, value);
  }

  @Experimental
  public RequestTemplate methodMetadata(MethodMetadata methodMetadata) {
    this.methodMetadata = methodMetadata;
    return this;
  }

  @Experimental
  public RequestTemplate feignTarget(Target<?> feignTarget) {
    this.feignTarget = feignTarget;
    return this;
  }

  @Experimental
  public MethodMetadata methodMetadata() {
    return methodMetadata;
  }

  @Experimental
  public Target<?> feignTarget() {
    return feignTarget;
  }

  /**
   * Factory for creating RequestTemplate.
   */
  interface Factory {

    /**
     * create a request template using args passed to a method invocation.
     */
    RequestTemplate create(Object[] argv);
  }

}
