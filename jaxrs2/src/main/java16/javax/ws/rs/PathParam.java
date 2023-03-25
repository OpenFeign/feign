package javax.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the value of a URI template parameter or a path segment
 * containing the template parameter to a resource method parameter, resource
 * class field, or resource class
 * bean property. The value is URL decoded unless this
 * is disabled using the {@link Encoded &#64;Encoded} annotation.
 * A default value can be specified using the {@link DefaultValue &#64;DefaultValue}
 * annotation.
 *
 * The type of the annotated parameter, field or property must either:
 * <ul>
 * <li>Be {@link javax.ws.rs.core.PathSegment}, the value will be the final
 * segment of the matching part of the path.
 * See {@link javax.ws.rs.core.UriInfo} for a means of retrieving all request
 * path segments.</li>
 * <li>Be {@code List<javax.ws.rs.core.PathSegment>}, the
 * value will be a list of {@code PathSegment} corresponding to the path
 * segment(s) that matched the named template parameter.
 * See {@link javax.ws.rs.core.UriInfo} for a means of retrieving all request
 * path segments.</li>
 * <li>Be a primitive type.</li>
 * <li>Have a constructor that accepts a single String argument.</li>
 * <li>Have a static method named {@code valueOf} or {@code fromString}
 * that accepts a single
 * String argument (see, for example, {@link Integer#valueOf(String)}).</li>
 * <li>Have a registered implementation of {@link javax.ws.rs.ext.ParamConverterProvider}
 * JAX-RS extension SPI that returns a {@link javax.ws.rs.ext.ParamConverter}
 * instance capable of a "from string" conversion for the type.</li>
 * </ul>
 *
 * <p>The injected value corresponds to the latest use (in terms of scope) of
 * the path parameter. E.g. if a class and a sub-resource method are both
 * annotated with a {@link Path &#64;Path} containing the same URI template
 * parameter, use of {@code @PathParam} on a sub-resource method parameter
 * will bind the value matching URI template parameter in the method's
 * {@code @Path} annotation.</p>
 *
 * <p>Because injection occurs at object creation time, use of this annotation
 * on resource class fields and bean properties is only supported for the
 * default per-request resource class lifecycle. Resource classes using
 * other lifecycles should only use this annotation on resource method
 * parameters.</p>
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see Encoded &#64;Encoded
 * @see DefaultValue &#64;DefaultValue
 * @see javax.ws.rs.core.PathSegment
 * @see javax.ws.rs.core.UriInfo
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD,
  ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PathParam {

    /**
     * Defines the name of the URI template parameter whose value will be used
     * to initialize the value of the annotated method parameter, class field or
     * property. See {@link Path#value()} for a description of the syntax of
     * template parameters.
     *
     * <p>E.g. a class annotated with: {@code @Path("widgets/{id}")}
     * can have methods annotated whose arguments are annotated
     * with {@code @PathParam("id")}.
     */
    String value();
}
