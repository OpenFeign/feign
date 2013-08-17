package feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Expands headers supplied in the {@code value}.  Variables are permitted as values.
 * <br>
 * <pre>
 * &#64;RequestLine("GET /")
 * &#64;Headers("Cache-Control: max-age=640000")
 * ...
 *
 * &#64;RequestLine("POST /")
 * &#64;Headers({
 *   "X-Foo: Bar",
 *   "X-Ping: {token}"
 * }) void post(&#64;Named("token") String token);
 * ...
 * </pre>
 * <br>
 * <strong>Note:</strong> Headers do not overwrite each other. All headers with the same name will
 * be included in the request.
 * <br><br><b>Relationship to JAXRS</b><br>
 * <br>
 * The following two forms are identical.
 * <br>
 * Feign:
 * <pre>
 * &#64;RequestLine("POST /")
 * &#64;Headers({
 *   "X-Ping: {token}"
 * }) void post(&#64;Named("token") String token);
 * ...
 * </pre>
 * <br>
 * JAX-RS:
 * <pre>
 * &#64;POST &#64;Path("/")
 * void post(&#64;HeaderParam("X-Ping") String token);
 * ...
 * </pre>
 */
@Target(METHOD) @Retention(RUNTIME)
public @interface Headers {
  String[] value();
}
