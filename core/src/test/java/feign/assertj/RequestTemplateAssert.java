/*
 * Copyright 2015 Netflix, Inc.
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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.data.MapEntry;
import org.assertj.core.internal.ByteArrays;
import org.assertj.core.internal.Maps;
import org.assertj.core.internal.Objects;

import feign.RequestTemplate;

import static feign.Util.UTF_8;

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

  public RequestTemplateAssert hasBody(String utf8Expected) {
    isNotNull();
    if (actual.bodyTemplate() != null) {
      failWithMessage("\nExpecting bodyTemplate to be null, but was:<%s>", actual.bodyTemplate());
    }
    objects.assertEqual(info, new String(actual.body(), UTF_8), utf8Expected);
    return this;
  }

  public RequestTemplateAssert hasBody(byte[] expected) {
    isNotNull();
    if (actual.bodyTemplate() != null) {
      failWithMessage("\nExpecting bodyTemplate to be null, but was:<%s>", actual.bodyTemplate());
    }
    arrays.assertContains(info, actual.body(), expected);
    return this;
  }

  public RequestTemplateAssert hasBodyTemplate(String expected) {
    isNotNull();
    if (actual.body() != null) {
      failWithMessage("\nExpecting body to be null, but was:<%s>", actual.bodyTemplate());
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
    maps.assertContainsExactly(info, actual.headers(), entries);
    return this;
  }
}
