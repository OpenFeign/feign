package feign;

import java.lang.reflect.Type;

/**
 * Map function to apply to the response before decoding it.
 *
 * <pre>{@code
 * new ResponseMapper() {
 *      @Override
 *      public Response map(Response response, Type type) {
 *          try {
 *            return response
 *              .toBuilder()
 *              .body(Util.toString(response.body().asReader()).toUpperCase().getBytes())
 *              .build();
 *          } catch (IOException e) {
 *              throw new RuntimeException(e);
 *          }
 *      }
 *  };
 * }</pre>
 */
public interface ResponseMapper {

  Response map(Response response, Type type);
}
