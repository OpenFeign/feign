package feign.translate;

import java.util.Map;

/**
 * Created by david on 14/04/16.
 */
public interface TemplateEngine {

    String translate(String template, Map<String, ?> variables);
}
