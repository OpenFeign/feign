package feign.hystrix;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * * Adds the ability to customize hystrix's settings:
 * <blockquote>
 * <pre>
 *     @HystrixConfig(key = "MyCustomKey", timeout = 2000)
 *     interface MyService {
 *         // methods...
 *     }
 * </pre>
 * </blockquote>
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface HystrixConfig {
    String key() default "";

    int timeout() default -1;
}
