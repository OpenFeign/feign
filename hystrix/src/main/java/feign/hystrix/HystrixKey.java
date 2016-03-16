package feign.hystrix;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
public @interface HystrixKey {
    String value();
}
