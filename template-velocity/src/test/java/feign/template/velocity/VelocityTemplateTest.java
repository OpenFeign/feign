package feign.template.velocity;

import feign.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by david on 14/04/16.
 */
public class VelocityTemplateTest {



    @Test
    public void testTemplate(){
        VelocityTemplate velocityTemplate = new VelocityTemplate();
        Map<String,String> variables = new HashMap<String, String>();
        variables.put("name","World");
        String result = velocityTemplate.translate("test.vm",variables);
        Assert.assertEquals("Hello World!  Welcome to Velocity!",result);
    }

}
