package feign.template.velocity;

import feign.translate.TemplateEngine;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

/**
 * Created by david on 14/04/16.
 */
public class VelocityTemplate implements TemplateEngine{

    private final VelocityEngine engine;

    public VelocityTemplate(){
        engine = new VelocityEngine();
        engine.setProperty(Velocity.RESOURCE_LOADER, "string");
        engine.addProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.addProperty("string.resource.loader.repository.static", "false");
        engine.init();

    }

    @Override
    public String translate(String template, Map<String, ?> variables) {
        // Initialize my template repository. You can replace the "Hello $w" with your String.
        StringResourceRepository repo = (StringResourceRepository) engine.getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT);
        repo.putStringResource("template", template);

        // Set parameters for my template.
        VelocityContext context = new VelocityContext();
        for(Map.Entry<String, ?> a : variables.entrySet()){
            context.put(a.getKey(),a.getValue());
        }

        // Get and merge the template with my parameters.
        Template t = engine.getTemplate("template");
        StringWriter writer = new StringWriter();
        t.merge(context, writer);
        return writer.toString();

    }
}
