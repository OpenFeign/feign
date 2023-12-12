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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AnnotationErrorDecoderInheritanceMethodLevelAnnotationTest extends
    AbstractAnnotationErrorDecoderTest<AnnotationErrorDecoderInheritanceMethodLevelAnnotationTest.SecondLevelInterface> {
  @Override
  public Class<SecondLevelInterface> interfaceAtTest() {
    return SecondLevelInterface.class;
  }

  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"Test Code Specific At Method", 403, "topLevelMethod1",
            MethodTopLevelDefaultException.class},
        {"Test Code Specific At Method", 404, "topLevelMethod1",
            MethodTopLevelAnnotationException.class},
        {"Test Code Specific At Method", 403, "topLevelMethod2",
            MethodSecondLevelDefaultException.class},
        {"Test Code Specific At Method", 404, "topLevelMethod2",
            MethodSecondLevelAnnotationException.class},
        {"Test Code Specific At Method", 403, "topLevelMethod3",
            MethodSecondLevelDefaultException.class},
        {"Test Code Specific At Method", 404, "topLevelMethod3",
            MethodSecondLevelErrorHandlingException.class},
        {"Test Code Specific At Method", 403, "topLevelMethod4",
            MethodSecondLevelDefaultException.class},
        {"Test Code Specific At Method", 404, "topLevelMethod4",
            MethodSecondLevelAnnotationException.class},
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
    initAnnotationErrorDecoderInheritanceMethodLevelAnnotationTest(testType, errorCode, method,
        expectedExceptionClass);
    AnnotationErrorDecoder decoder =
        AnnotationErrorDecoder.builderFor(SecondLevelInterface.class).build();

    assertThat(decoder.decode(feignConfigKey(method), testResponse(errorCode)).getClass())
        .isEqualTo(expectedExceptionClass);
  }

  interface TopLevelInterface {
    @TopLevelMethodErrorHandling
    void topLevelMethod1();

    @TopLevelMethodErrorHandling
    void topLevelMethod2();

    @TopLevelMethodErrorHandling
    void topLevelMethod3();

    @ErrorHandling(codeSpecific = @ErrorCodes(codes = {404},
        generate = TopLevelMethodErrorHandlingException.class))
    void topLevelMethod4();
  }

  interface SecondLevelInterface extends TopLevelInterface {
    @Override
    @SecondLevelMethodErrorHandling
    void topLevelMethod2();

    @Override
    @ErrorHandling(
        codeSpecific = {
            @ErrorCodes(codes = {404}, generate = MethodSecondLevelErrorHandlingException.class)},
        defaultException = MethodSecondLevelDefaultException.class)
    void topLevelMethod3();

    @Override
    @SecondLevelMethodErrorHandling
    void topLevelMethod4();
  }

  @ErrorHandling(
      codeSpecific = {
          @ErrorCodes(codes = {404}, generate = MethodTopLevelAnnotationException.class),},
      defaultException = MethodTopLevelDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface TopLevelMethodErrorHandling {
  }

  @ErrorHandling(
      codeSpecific = {
          @ErrorCodes(codes = {404}, generate = MethodSecondLevelAnnotationException.class),},
      defaultException = MethodSecondLevelDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface SecondLevelMethodErrorHandling {
  }

  static class MethodTopLevelDefaultException extends Exception {
    public MethodTopLevelDefaultException() {}
  }
  static class TopLevelMethodErrorHandlingException extends Exception {
    public TopLevelMethodErrorHandlingException() {}
  }
  static class MethodTopLevelAnnotationException extends Exception {
    public MethodTopLevelAnnotationException() {}
  }
  static class MethodSecondLevelDefaultException extends Exception {
    public MethodSecondLevelDefaultException() {}
  }
  static class MethodSecondLevelErrorHandlingException extends Exception {
    public MethodSecondLevelErrorHandlingException() {}
  }
  static class MethodSecondLevelAnnotationException extends Exception {
    public MethodSecondLevelAnnotationException() {}
  }

  public void initAnnotationErrorDecoderInheritanceMethodLevelAnnotationTest(String testType,
                                                                             int errorCode,
                                                                             String method,
                                                                             Class<? extends Exception> expectedExceptionClass) {
    this.testType = testType;
    this.errorCode = errorCode;
    this.method = method;
    this.expectedExceptionClass = expectedExceptionClass;
  }
}
