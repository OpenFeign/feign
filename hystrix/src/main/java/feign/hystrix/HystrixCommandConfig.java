package feign.hystrix;

import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Adds the ability to customize hystrix command's settings:
 * <blockquote>
 * <pre>
 *     interface MyService {
 *         @HystrixCommandConfig(key = "MyCustomCommandKey", timeout = 2000)
 *         @RequestLine("GET /blah")
 *         String blah();
 *     }
 * </pre>
 * </blockquote>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface HystrixCommandConfig {
    String key() default "";

    int timeout() default -1;
}
