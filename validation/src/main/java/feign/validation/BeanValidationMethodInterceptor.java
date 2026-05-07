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
import feign.interceptor.Invocation;
import feign.interceptor.MethodInterceptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
  private final ConcurrentMap<Method, Annotation[][]> parameterAnnotationsCache =
      new ConcurrentHashMap<>();

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
    Set<ConstraintViolation<Object>> violations = collectViolations(invocation);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    return chain.next(invocation);
  }

  private Set<ConstraintViolation<Object>> collectViolations(Invocation invocation) {
    Object[] arguments = invocation.arguments();
    if (arguments == null || arguments.length == 0) {
      return Collections.emptySet();
    }
    Set<ConstraintViolation<Object>> violations = new LinkedHashSet<>();
    Object body = invocation.body();
    if (body != null) {
      violations.addAll(validator.validate(body, groups));
    }
    Integer bodyIndex = invocation.methodMetadata().bodyIndex();
    Annotation[][] parameterAnnotations =
        parameterAnnotationsCache.computeIfAbsent(
            invocation.method(), Method::getParameterAnnotations);
    for (int i = 0; i < arguments.length; i++) {
      if (arguments[i] == null) {
        continue;
      }
      if (bodyIndex != null && bodyIndex == i) {
        continue;
      }
      if (hasValidAnnotation(parameterAnnotations[i])) {
        violations.addAll(validator.validate(arguments[i], groups));
      }
    }
    return violations;
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
