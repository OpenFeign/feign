package feign;

import java.lang.annotation.Retention;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Expands the request-line supplied in the {@code value}, permitting path and query variables, or
 * just the http method. <br>
 * <pre>
 * ...
 * &#64;RequestLine("POST /servers")
 * ...
 *
 * &#64;RequestLine("GET /servers/{serverId}?count={count}")
 * void get(&#64;Param("serverId") String serverId, &#64;Param("count") int count);
 * ...
 *
 * &#64;RequestLine("GET")
 * Response getNext(URI nextLink);
 * ...
 * </pre>
 * HTTP version suffix is optional, but permitted.  There are no guarantees this version will impact
 * that sent by the client. <br>
 * <pre>
 * &#64;RequestLine("POST /servers HTTP/1.1")
 * ...
 * </pre>
 * <br> <strong>Note:</strong> Query params do not overwrite each other. All queries with the same
 * name will be included in the request. <br><br><b>Relationship to JAXRS</b><br> <br> The following
 * two forms are identical. <br> Feign:
 * <pre>
 * &#64;RequestLine("GET /servers/{serverId}?count={count}")
 * void get(&#64;Param("serverId") String serverId, &#64;Param("count") int count);
 * ...
 * </pre>
 * <br> JAX-RS:
 * <pre>
 * &#64;GET &#64;Path("/servers/{serverId}")
 * void get(&#64;PathParam("serverId") String serverId, &#64;QueryParam("count") int count);
 * ...
 * </pre>
 */
@java.lang.annotation.Target(METHOD)
@Retention(RUNTIME)
public @interface RequestLine {

  String value();
  boolean decodeSlash() default true;
}
