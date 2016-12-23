package feign.error;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface ErrorHandling {
    StatusCodes[] codeSpecific() default {};
    Class<? extends Exception> defaultException() default NO_DEFAULT.class;

    final class NO_DEFAULT extends Exception {}
}
