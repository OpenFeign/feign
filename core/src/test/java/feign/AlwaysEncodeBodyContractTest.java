/**
 * Copyright 2012-2021 The Feign Authors
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

import feign.codec.EncodeException;
import feign.codec.Encoder;
import org.junit.Assert;
import org.junit.Test;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class AlwaysEncodeBodyContractTest {

  @Retention(RUNTIME)
  @Target(ElementType.METHOD)
  private @interface SampleMethodAnnotation {
  }

  private static class SampleContract extends AlwaysEncodeBodyContract {
    SampleContract() {
      AnnotationProcessor<SampleMethodAnnotation> annotationProcessor =
          (annotation, metadata) -> metadata.template().method(Request.HttpMethod.POST);
      super.registerMethodAnnotation(SampleMethodAnnotation.class, annotationProcessor);
    }
  }

  private interface SampleTargetMultipleNonAnnotatedParameters {
    @SampleMethodAnnotation
    String concatenate(String word1, String word2, String word3);
  }

  private interface SampleTargetNoParameters {
    @SampleMethodAnnotation
    String concatenate();
  }

  private interface SampleTargetOneParameter {
    @SampleMethodAnnotation
    String concatenate(String word1);
  }

  private static class AllParametersSampleEncoder implements Encoder {
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template)
        throws EncodeException {
      Object[] methodParameters = (Object[]) object;
      String body =
          Arrays.stream(methodParameters).map(String::valueOf).collect(Collectors.joining());
      template.body(body);
    }
  }

  private static class BodyParameterSampleEncoder implements Encoder {
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template)
        throws EncodeException {
      template.body(String.valueOf(object));
    }
  }

  private static class SampleClient implements Client {
    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
      return Response.builder()
          .status(200)
          .request(request)
          .body(request.body())
          .build();
    }
  }

  /**
   * This test makes sure Feign calls the client provided encoder regardless of how many
   * non-annotated parameters the client method has, as alwaysEncodeBody is set to true.
   */
  @Test
  public void alwaysEncodeBodyTrueTest() {
    SampleTargetMultipleNonAnnotatedParameters sampleClient1 = Feign.builder()
        .contract(new SampleContract())
        .encoder(new AllParametersSampleEncoder())
        .client(new SampleClient())
        .target(SampleTargetMultipleNonAnnotatedParameters.class, "http://localhost");
    Assert.assertEquals("foobarchar", sampleClient1.concatenate("foo", "bar", "char"));

    SampleTargetNoParameters sampleClient2 = Feign.builder()
        .contract(new SampleContract())
        .encoder(new AllParametersSampleEncoder())
        .client(new SampleClient())
        .target(SampleTargetNoParameters.class, "http://localhost");
    Assert.assertEquals("", sampleClient2.concatenate());

    SampleTargetOneParameter sampleClient3 = Feign.builder()
        .contract(new SampleContract())
        .encoder(new AllParametersSampleEncoder())
        .client(new SampleClient())
        .target(SampleTargetOneParameter.class, "http://localhost");
    Assert.assertEquals("moo", sampleClient3.concatenate("moo"));
  }

}
