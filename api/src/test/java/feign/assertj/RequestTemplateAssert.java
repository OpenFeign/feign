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
package feign.assertj;

import static feign.Util.UTF_8;

import feign.Request;
import feign.RequestTemplate;
import java.io.IOException;
import java.util.Optional;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.data.MapEntry;
import org.assertj.core.internal.ByteArrays;
import org.assertj.core.internal.Maps;
import org.assertj.core.internal.Objects;

public final class RequestTemplateAssert
    extends AbstractAssert<RequestTemplateAssert, RequestTemplate> {

  ByteArrays arrays = ByteArrays.instance();
  Objects objects = Objects.instance();
  Maps maps = Maps.instance();

  public RequestTemplateAssert(RequestTemplate actual) {
    super(actual, RequestTemplateAssert.class);
  }

  public RequestTemplateAssert hasMethod(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.method(), expected);
    return this;
  }

  public RequestTemplateAssert hasUrl(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.url(), expected);
    return this;
  }

  public RequestTemplateAssert hasPath(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.path(), expected);
    return this;
  }

  public RequestTemplateAssert hasBody(String utf8Expected) {
    isNotNull();
    if (actual.bodyTemplate() != null) {
      failWithMessage("\nExpecting bodyTemplate to be null, but was:<%s>", actual.bodyTemplate());
    }
    Optional<Request.Body> requestBody = actual.requestBody();
    if (!requestBody.isPresent()) {
      failWithMessage("\nExpecting body to be <%s>, but was empty", utf8Expected);
      return this;
    }
    try {
      objects.assertEqual(info, requestBody.get().writeToString(UTF_8), utf8Expected);
    } catch (IOException e) {
      failWithMessage("\nFailed to read body: %s", e.getMessage());
    }
    return this;
  }

  public RequestTemplateAssert hasBody(byte[] expected) {
    isNotNull();
    if (actual.bodyTemplate() != null) {
      failWithMessage("\nExpecting bodyTemplate to be null, but was:<%s>", actual.bodyTemplate());
    }
    Optional<Request.Body> requestBody = actual.requestBody();
    if (!requestBody.isPresent()) {
      failWithMessage("\nExpecting body to be present, but was empty");
      return this;
    }
    try {
      arrays.assertContains(info, requestBody.get().writeToByteArray(), expected);
    } catch (IOException e) {
      failWithMessage("\nFailed to read body: %s", e.getMessage());
    }
    return this;
  }

  public RequestTemplateAssert hasBodyTemplate(String expected) {
    isNotNull();
    if (actual.requestBody().isPresent()) {
      failWithMessage("\nExpecting body to be null, but was present");
    }
    objects.assertEqual(info, actual.bodyTemplate(), expected);
    return this;
  }

  public RequestTemplateAssert hasQueries(MapEntry... entries) {
    isNotNull();
    maps.assertContainsExactly(info, actual.queries(), entries);
    return this;
  }

  public RequestTemplateAssert hasHeaders(MapEntry... entries) {
    isNotNull();
    maps.assertContainsOnly(info, actual.headers(), entries);
    return this;
  }

  public RequestTemplateAssert hasNoHeader(final String encoded) {
    objects.assertNull(info, actual.headers().get(encoded));
    return this;
  }

  public RequestTemplateAssert noRequestBody() {
    isNotNull();
    if (actual.bodyTemplate() != null) {
      failWithMessage(
          "\nExpecting requestBody.bodyTemplate to be null, but was:<%s>", actual.bodyTemplate());
    }
    if (actual.requestBody().isPresent()) {
      try {
        failWithMessage(
            "\nExpecting requestBody.data to be null, but was:<%s>",
            actual.requestBody().get().writeToString(UTF_8));
      } catch (IOException e) {
        failWithMessage("\nExpecting requestBody to be null, but was present");
      }
    }
    return this;
  }
}
