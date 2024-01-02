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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.ClassLevelDefaultException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.ClassLevelNotFoundException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.Method1DefaultException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.Method1NotFoundException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.Method2NotFoundException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.Method3DefaultException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.ServeErrorException;
import feign.error.AnnotationErrorDecoderInheritanceTest.TopLevelInterface.UnauthenticatedOrUnauthorizedException;

public class AnnotationErrorDecoderInheritanceTest extends
    AbstractAnnotationErrorDecoderTest<AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority> {

  @Override
  public Class<TestClientInterfaceWithExceptionPriority> interfaceAtTest() {
    return TestClientInterfaceWithExceptionPriority.class;
  }

  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"Test Code Specific At Method", 404, "method1Test", Method1NotFoundException.class},
        {"Test Code Specific At Method", 401, "method1Test",
            UnauthenticatedOrUnauthorizedException.class},
        {"Test Code Specific At Method", 404, "method2Test", Method2NotFoundException.class},
        {"Test Code Specific At Method", 500, "method2Test", ServeErrorException.class},
        {"Test Code Specific At Method", 503, "method2Test", ServeErrorException.class},
        {"Test Code Specific At Class", 403, "method1Test",
            UnauthenticatedOrUnauthorizedException.class},
        {"Test Code Specific At Class", 403, "method2Test",
            UnauthenticatedOrUnauthorizedException.class},
        {"Test Code Specific At Class", 404, "method3Test", ClassLevelNotFoundException.class},
        {"Test Code Specific At Class", 403, "method3Test",
            UnauthenticatedOrUnauthorizedException.class},
        {"Test Default At Method", 504, "method1Test", Method1DefaultException.class},
        {"Test Default At Method", 504, "method3Test", Method3DefaultException.class},
        {"Test Default At Class", 504, "method2Test", ClassLevelDefaultException.class},
    });
  } // first data value (0) is default

  public String testType;
  public int errorCode;
  public String method;
  public Class<? extends Exception> expectedExceptionClass;

  @MethodSource("data")
  @ParameterizedTest(
      name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
  void test(String testType,
            int errorCode,
            String method,
            Class<? extends Exception> expectedExceptionClass)
      throws Exception {
    initAnnotationErrorDecoderInheritanceTest(testType, errorCode, method, expectedExceptionClass);
    AnnotationErrorDecoder decoder =
        AnnotationErrorDecoder.builderFor(TestClientInterfaceWithExceptionPriority.class).build();

    assertThat(decoder.decode(feignConfigKey(method), testResponse(errorCode)).getClass())
        .isEqualTo(expectedExceptionClass);
  }


  @ErrorHandling(codeSpecific = {
      @ErrorCodes(codes = {404}, generate = ClassLevelNotFoundException.class),
      @ErrorCodes(codes = {403}, generate = UnauthenticatedOrUnauthorizedException.class)
  },
      defaultException = ClassLevelDefaultException.class)
  interface TopLevelInterface {
    @ErrorHandling(codeSpecific = {
        @ErrorCodes(codes = {404}, generate = Method1NotFoundException.class),
        @ErrorCodes(codes = {401}, generate = UnauthenticatedOrUnauthorizedException.class)
    },
        defaultException = Method1DefaultException.class)
    void method1Test();

    class ClassLevelDefaultException extends Exception {
    }
    class Method1DefaultException extends Exception {
    }
    class Method3DefaultException extends Exception {
    }
    class Method1NotFoundException extends Exception {
    }
    class Method2NotFoundException extends Exception {
    }
    class ClassLevelNotFoundException extends Exception {
    }
    class UnauthenticatedOrUnauthorizedException extends Exception {
    }
    class ServeErrorException extends Exception {
    }

  }

  interface SecondTopLevelInterface {
  }
  interface SecondLevelInterface extends SecondTopLevelInterface, TopLevelInterface {
  }

  interface TestClientInterfaceWithExceptionPriority extends SecondLevelInterface {

    @ErrorHandling(codeSpecific = {
        @ErrorCodes(codes = {404}, generate = Method2NotFoundException.class),
        @ErrorCodes(codes = {500, 503}, generate = ServeErrorException.class)
    })
    void method2Test();

    @ErrorHandling(
        defaultException = Method3DefaultException.class)
    void method3Test();
  }

  public void initAnnotationErrorDecoderInheritanceTest(String testType,
                                                        int errorCode,
                                                        String method,
                                                        Class<? extends Exception> expectedExceptionClass) {
    this.testType = testType;
    this.errorCode = errorCode;
    this.method = method;
    this.expectedExceptionClass = expectedExceptionClass;
  }
}
