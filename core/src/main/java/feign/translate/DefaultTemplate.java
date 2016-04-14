package feign.translate;

import java.util.Map;

import static feign.Util.checkNotNull;

/**
 * Created by david on 14/04/16.
 */
public class DefaultTemplate implements TemplateEngine{

    @Override
    public String translate(String template, Map<String, ?> variables) {
        if (checkNotNull(template, "template").length() < 3) {
            return template.toString();
        }
        checkNotNull(variables, "variables for %s", template);

        boolean inVar = false;
        StringBuilder var = new StringBuilder();
        StringBuilder builder = new StringBuilder();
        for (char c : template.toCharArray()) {
            switch (c) {
                case '{':
                    inVar = true;
                    break;
                case '}':
                    inVar = false;
                    String key = var.toString();
                    Object value = variables.get(var.toString());
                    if (value != null) {
                        builder.append(value);
                    } else {
                        builder.append('{').append(key).append('}');
                    }
                    var = new StringBuilder();
                    break;
                default:
                    if (inVar) {
                        var.append(c);
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}
