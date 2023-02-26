/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.error;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.junit.Test;
import static feign.error.AnnotationErrorDecoderNoAnnotationTest.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationErrorDecoderNoAnnotationTest
    extends AbstractAnnotationErrorDecoderTest<TestClientInterfaceWithNoAnnotations> {

  @Override
  public Class<TestClientInterfaceWithNoAnnotations> interfaceAtTest() {
    return TestClientInterfaceWithNoAnnotations.class;
  }

  @Test
  public void delegatesToDefaultErrorDecoder() throws Exception {

    ErrorDecoder defaultErrorDecoder = new ErrorDecoder() {
      @Override
      public Exception decode(String methodKey, Response response) {
        return new DefaultErrorDecoderException();
      }
    };

    AnnotationErrorDecoder decoder =
        AnnotationErrorDecoder.builderFor(TestClientInterfaceWithNoAnnotations.class)
            .withDefaultDecoder(defaultErrorDecoder)
            .build();

    assertThat(decoder.decode(feignConfigKey("method1Test"), testResponse(502)).getClass())
        .isEqualTo(DefaultErrorDecoderException.class);
  }

  interface TestClientInterfaceWithNoAnnotations {
    void method1Test();
  }

  static class DefaultErrorDecoderException extends Exception {
  }

}
