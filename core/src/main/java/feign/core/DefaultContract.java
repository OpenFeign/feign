/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.core;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

import feign.Body;
import feign.DeclarativeContract;
import feign.HeaderMap;
import feign.Headers;
import feign.Param;
import feign.Part;
import feign.PartMetadata;
import feign.QueryMap;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Types;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultContract extends DeclarativeContract {

  static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

  public DefaultContract() {
    super.registerClassAnnotation(
        Headers.class,
        (header, data) -> {
          final String[] headersOnType = header.value();
          checkState(
              headersOnType.length > 0,
              "Headers annotation was empty on type %s.",
              data.configKey());
          final Map<String, Collection<String>> headers = toMap(headersOnType);
          headers.putAll(data.template().headers());
          data.template().headers(null); // to clear
          data.template().headers(headers);
        });
    super.registerMethodAnnotation(
        RequestLine.class,
        (ann, data) -> {
          final String requestLine = ann.value();
          checkState(
              emptyToNull(requestLine) != null,
              "RequestLine annotation was empty on method %s.",
              data.configKey());

          final Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
          if (!requestLineMatcher.find()) {
            throw new IllegalStateException(
                String.format(
                    "RequestLine annotation didn't start with an HTTP verb on method %s",
                    data.configKey()));
          } else {
            data.template().method(HttpMethod.valueOf(requestLineMatcher.group(1)));
            data.template().uri(requestLineMatcher.group(2));
          }
          data.template().decodeSlash(ann.decodeSlash());
          data.template().collectionFormat(ann.collectionFormat());
        });
    super.registerMethodAnnotation(
        Body.class,
        (ann, data) -> {
          final String body = ann.value();
          checkState(
              emptyToNull(body) != null,
              "Body annotation was empty on method %s.",
              data.configKey());
          if (body.indexOf('{') == -1) {
            data.template().body(Request.Body.of(body));
          } else {
            data.template().bodyTemplate(body);
          }
        });
    super.registerMethodAnnotation(
        Headers.class,
        (header, data) -> {
          final String[] headersOnMethod = header.value();
          checkState(
              headersOnMethod.length > 0,
              "Headers annotation was empty on method %s.",
              data.configKey());
          data.template().headers(toMap(headersOnMethod));
        });
    super.registerParameterAnnotation(
        Param.class,
        (paramAnnotation, data, paramIndex) -> {
          final String annotationName = paramAnnotation.value();
          final Parameter parameter = data.method().getParameters()[paramIndex];
          final String name;
          if (emptyToNull(annotationName) == null && parameter.isNamePresent()) {
            name = parameter.getName();
          } else {
            name = annotationName;
          }
          checkState(
              emptyToNull(name) != null,
              "Param annotation was empty on param %s.\nHint: %s",
              paramIndex,
              "Prefer using @Param(value=\"name\"), or compile your code with the -parameters flag.\n"
                  + "If the value is missing, Feign attempts to retrieve the parameter name from bytecode, "
                  + "which only works if the class was compiled with the -parameters flag.");
          nameParam(data, name, paramIndex);
          final Class<? extends Param.Expander> expander = paramAnnotation.expander();
          if (expander != Param.ToStringExpander.class) {
            data.indexToExpanderClass().put(paramIndex, expander);
          }
          if (!data.template().hasRequestVariable(name)) {
            data.formParams().add(name);
          }
        });
    super.registerParameterAnnotation(
        QueryMap.class,
        (queryMap, data, paramIndex) -> {
          checkState(
              data.queryMapIndex() == null,
              "QueryMap annotation was present on multiple parameters.");
          data.queryMapIndex(paramIndex);
          data.queryMapEncoder(queryMap.mapEncoder().instance());
        });
    super.registerParameterAnnotation(
        HeaderMap.class,
        (queryMap, data, paramIndex) -> {
          checkState(
              data.headerMapIndex() == null,
              "HeaderMap annotation was present on multiple parameters.");
          data.headerMapIndex(paramIndex);
        });
    super.registerParameterAnnotation(
        Part.class,
        (part, data, paramIndex) -> {
          final String[] rawHeaders;
          if (part.value().length > 0 && part.headers().length > 0) {
            throw new IllegalStateException(
                String.format(
                    "@Part on method %s parameter %d has both value() and headers() set; use one or the other",
                    data.configKey(), paramIndex));
          } else if (part.value().length > 0) {
            rawHeaders = part.value();
          } else if (part.headers().length > 0) {
            rawHeaders = part.headers();
          } else {
            throw new IllegalStateException(
                String.format(
                    "@Part on method %s parameter %d has neither value() nor headers() set; use one or the other",
                    data.configKey(), paramIndex));
          }

          final Map<String, Collection<String>> headers =
              rawHeaders.length == 1 && !rawHeaders[0].contains(":")
                  ? Map.of(
                      "Content-Disposition",
                      List.of("form-data; name=\"" + rawHeaders[0].trim() + '"'))
                  : toMap(rawHeaders);
          final Type type =
              Types.resolve(
                  data.targetType(),
                  data.targetType(),
                  data.method().getGenericParameterTypes()[paramIndex]);
          final PartMetadata metadata = new PartMetadata(paramIndex, type, headers, part.explode());

          data.partMetadata().put(paramIndex, metadata);
        });
  }

  private static Map<String, Collection<String>> toMap(String[] input) {
    final Map<String, Collection<String>> result =
        new LinkedHashMap<String, Collection<String>>(input.length);
    for (final String header : input) {
      final int colon = header.indexOf(':');
      final String name = header.substring(0, colon);
      if (!result.containsKey(name)) {
        result.put(name, new ArrayList<String>(1));
      }
      result.get(name).add(header.substring(colon + 1).trim());
    }
    return result;
  }
}
