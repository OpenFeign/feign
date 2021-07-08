/**
 * Copyright 2017-2019 The Feign Authors
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

import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.ClassLevelDefaultException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.ClassLevelNotFoundException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.Method1DefaultException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.Method1NotFoundException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.Method2NotFoundException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.Method3DefaultException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.ServeErrorException;
import static feign.error.AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority.UnauthenticatedOrUnauthorizedException;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationErrorDecoderInheritanceTest extends
    AbstractAnnotationErrorDecoderTest<AnnotationErrorDecoderInheritanceTest.TestClientInterfaceWithExceptionPriority> {

  @Override
  public Class<TestClientInterfaceWithExceptionPriority> interfaceAtTest() {
    return TestClientInterfaceWithExceptionPriority.class;
  }

  @Parameters(
      name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
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
  }

  @Parameter // first data value (0) is default
  public String testType;

  @Parameter(1)
  public int errorCode;

  @Parameter(2)
  public String method;

  @Parameter(3)
  public Class<? extends Exception> expectedExceptionClass;

  @Test
  public void test() throws Exception {
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
}
