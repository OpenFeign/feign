package feign.codec;

import feign.RequestTemplate;

public interface BodyEncoder {
  /**
   * Converts objects to an appropriate representation. Can affect any part of {@link
   * RequestTemplate}.
   *
   * <p>Ex.
   *
   * <p>
   *
   * <pre>
   * public class GsonEncoder implements BodyEncoder {
   *     private final Gson gson;
   *
   *     public GsonEncoder(Gson gson) {
   *         this.gson = gson;
   *     }
   *
   *     &#064;Override
   *     public void encodeBody(Object bodyParam, RequestTemplate base) {
   *         base.body(gson.toJson(bodyParam));
   *     }
   *
   * }
   * </pre>
   *
   * <p>If a parameter has no {@code *Param} annotation, it is passed to this method.
   *
   * <p>
   *
   * <pre>
   * &#064;POST
   * &#064;Path(&quot;/&quot;)
   * void create(User user);
   * </pre>
   *
   * @param bodyParam a body parameter
   * @param base template to encode the {@code object} into.
   */
  void encodeBody(Object bodyParam, RequestTemplate base);
}
