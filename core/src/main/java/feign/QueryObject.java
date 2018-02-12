package feign;

import java.lang.annotation.Retention;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This is a tag annotation to support Object Query. By default, Feign does not support object query with a GET request,
 * You can use use QueryObjectEncoder to support this.
 */
@Retention(RUNTIME)
@java.lang.annotation.Target(TYPE)
public @interface QueryObject {

    @Retention(RUNTIME)
    @java.lang.annotation.Target(METHOD)
    @interface Param {
        String value();
    }

}

