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
package feign.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import feign.Request;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AnnotationErrorDecoderIllegalInterfacesTest {

  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            IllegalTestClientInterfaceWithTooManyMappingsToSingleCode.class,
            IllegalStateException.class,
            "Status Code [404] has already been declared"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithTooManyConstructors.class,
            IllegalStateException.class,
            "Too many constructors"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithTooManyBodyParams.class,
            IllegalStateException.class,
            "Cannot have two parameters either without"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithTooManyHeaderParams.class,
            IllegalStateException.class,
            "Cannot have two parameters tagged"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithBadHeaderParams.class,
            IllegalStateException.class,
            "Cannot generate exception - check constructor"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithBadHeaderObjectParam.class,
            IllegalStateException.class,
            "Response Header map must be of type Map"
          },
          {
            IllegalTestClientInterfaceWithExceptionWithTooManyRequestParams.class,
            IllegalStateException.class,
            "Cannot have two parameters either without"
          }
        });
  } // first data value (0) is default

  public Class testInterface;
  public Class<? extends Exception> expectedExceptionClass;
  public String messageStart;

  @MethodSource("data")
  @ParameterizedTest(
      name =
          "{index}: When building interface ({0}) should return exception type ({1}) with message"
              + " ({2})")
  void test(
      Class testInterface, Class<? extends Exception> expectedExceptionClass, String messageStart)
      throws Exception {
    initAnnotationErrorDecoderIllegalInterfacesTest(
        testInterface, expectedExceptionClass, messageStart);
    try {
      AnnotationErrorDecoder.builderFor(testInterface).build();
      fail("Should have thrown exception");
    } catch (Exception e) {
      assertThat(e.getClass()).isEqualTo(expectedExceptionClass);
      assertThat(e.getMessage()).startsWith(messageStart);
    }
  }

  interface IllegalTestClientInterfaceWithTooManyMappingsToSingleCode {
    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = Exception.class),
          @ErrorCodes(
              codes = {404},
              generate = Exception.class)
        })
    void method1Test();
  }

  interface IllegalTestClientInterfaceWithExceptionWithTooManyConstructors {
    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(String body) {}

      @FeignExceptionConstructor
      public BadException(String body, @ResponseHeaders Map<String, Collection<String>> headers) {}
    }
  }

  interface IllegalTestClientInterfaceWithExceptionWithTooManyBodyParams {
    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(String body, @ResponseBody String otherBody) {}
    }
  }

  interface IllegalTestClientInterfaceWithExceptionWithTooManyRequestParams {
    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(Request request1, Request request2) {}
    }
  }

  interface IllegalTestClientInterfaceWithExceptionWithTooManyHeaderParams {

    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(
          @ResponseHeaders Map<String, Collection<String>> headers1,
          @ResponseHeaders Map<String, Collection<String>> headers2) {}
    }
  }

  interface IllegalTestClientInterfaceWithExceptionWithBadHeaderParams {

    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(@ResponseHeaders Map<Integer, String> headers1) {
        headers1.get(3);
      }
    }
  }

  interface IllegalTestClientInterfaceWithExceptionWithBadHeaderObjectParam {

    @ErrorHandling(
        codeSpecific = {
          @ErrorCodes(
              codes = {404},
              generate = BadException.class)
        })
    void method1Test();

    class BadException extends Exception {

      @FeignExceptionConstructor
      public BadException(@ResponseHeaders TestPojo headers1) {}
    }
  }

  public void initAnnotationErrorDecoderIllegalInterfacesTest(
      Class testInterface, Class<? extends Exception> expectedExceptionClass, String messageStart) {
    this.testInterface = testInterface;
    this.expectedExceptionClass = expectedExceptionClass;
    this.messageStart = messageStart;
  }
}
