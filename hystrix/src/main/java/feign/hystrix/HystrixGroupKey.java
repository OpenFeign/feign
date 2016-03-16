package feign.hystrix;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Adds the ability to define a custom hystrix key name:
 * <blockquote>
 * <pre>
 *     @HystrixGroupKey("MyCustomKey")
 *     interface MyService {
 *         // methods...
 *     }
 * </pre>
 * </blockquote>
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface HystrixGroupKey {
    String value();
}
