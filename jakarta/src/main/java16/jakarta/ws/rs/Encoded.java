package jakarta.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disables automatic decoding of parameter values bound using {@link QueryParam},
 * {@link PathParam}, {@link FormParam} or {@link MatrixParam}. Using this annotation on a method
 * will disable decoding for all parameters. Using this annotation on a class will disable decoding
 * for all parameters of all methods.
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see QueryParam
 * @see MatrixParam
 * @see PathParam
 * @see FormParam
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR,
    ElementType.TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encoded {
}
