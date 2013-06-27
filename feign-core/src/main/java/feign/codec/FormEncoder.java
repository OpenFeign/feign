package feign.codec;

import java.util.Map;

import javax.ws.rs.FormParam;

import feign.RequestTemplate;

public interface FormEncoder {

  /**
   * FormParam encoding
   * <p/>
   * If any parameters are annotated with {@link FormParam}, they will be
   * collected and passed as {code formParams}
   * <p/>
   * <pre>
   * &#064;POST
   * &#064;Path(&quot;/&quot;)
   * Session login(@FormParam(&quot;username&quot;) String username, @FormParam(&quot;password&quot;) String password);
   * </pre>
   *
   * @param formParams Object instance to convert.
   * @param base       template to encode the {@code object} into.
   */
  void encodeForm(Map<String, ?> formParams, RequestTemplate base);
}