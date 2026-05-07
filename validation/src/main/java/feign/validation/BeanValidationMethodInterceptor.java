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
package feign.validation;

import feign.Experimental;
import feign.Invocation;
import feign.MethodInterceptor;
import feign.MethodMetadata;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;

/**
 * A {@link MethodInterceptor} that validates the method's body argument and any {@link Valid}
 * -annotated parameters using a {@link Validator} before the HTTP request is sent.
 *
 * <p>If any constraint violations are found a {@link ConstraintViolationException} is thrown and
 * the request is never dispatched.
 *
 * <p>Validation runs on the typed argument objects, before encoding. This is the right layer for
 * JSR-303 / Bean Validation 2.0 because the encoder may turn the body into bytes that the validator
 * cannot inspect.
 *
 * <pre>
 * Feign.builder()
 *   .methodInterceptor(BeanValidationMethodInterceptor.usingDefaultFactory())
 *   .target(...);
 * </pre>
 */
@Experimental
public final class BeanValidationMethodInterceptor implements MethodInterceptor {

  private final Validator validator;
  private final Class<?>[] groups;

  public BeanValidationMethodInterceptor(Validator validator, Class<?>... groups) {
    this.validator = validator;
    this.groups = groups == null ? new Class<?>[0] : groups;
  }

  /**
   * Builds an interceptor backed by the default {@link Validation#buildDefaultValidatorFactory}.
   */
  public static BeanValidationMethodInterceptor usingDefaultFactory() {
    return new BeanValidationMethodInterceptor(
        Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Override
  public Object intercept(Invocation invocation, Chain chain) throws Throwable {
    Object[] arguments = invocation.arguments();
    if (arguments != null && arguments.length > 0) {
      Set<ConstraintViolation<Object>> violations = new LinkedHashSet<>();
      MethodMetadata metadata = invocation.methodMetadata();
      Annotation[][] parameterAnnotations = invocation.method().getParameterAnnotations();
      Integer bodyIndex = metadata.bodyIndex();
      for (int i = 0; i < arguments.length; i++) {
        Object argument = arguments[i];
        if (argument == null) {
          continue;
        }
        boolean isBody = bodyIndex != null && bodyIndex == i;
        if (isBody || hasValidAnnotation(parameterAnnotations[i])) {
          violations.addAll(validator.validate(argument, groups));
        }
      }
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }
    return chain.next(invocation);
  }

  private static boolean hasValidAnnotation(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == Valid.class) {
        return true;
      }
    }
    return false;
  }
}
