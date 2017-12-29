package feign.codec;


import feign.QueryObject;
import feign.RequestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class QueryObjectEncoder implements Encoder {

    private Encoder fallbackEncoder;

    public QueryObjectEncoder(Encoder fallbackEncoder) {
        this.fallbackEncoder = fallbackEncoder;
    }

    @Override
    public void encode(Object parametersObject, Type bodyType, RequestTemplate template) throws EncodeException {
        if (parametersObject.getClass().getAnnotation(QueryObject.class) != null) {
            Map<String, Object> params = new HashMap<String, Object>();
            try {
                for (Method method : parametersObject.getClass().getMethods()) {
                    QueryObject.Param param = method.getAnnotation(QueryObject.Param.class);
                    if (param != null && method.getName().startsWith("get")) {
                        String key = param.value();
                        Object value = method.invoke(parametersObject);
                        if (value != null) {
                            value = String.valueOf(value);
                        }
                        params.put(key, value);
                        template.query(key, keyToTemplate(key));
                    }
                }
                template.resolve(params);
            } catch (IllegalAccessException e) {
                throw new EncodeException("Could not encode object query correctly", e);
            } catch (InvocationTargetException e) {
                throw new EncodeException("Could not encode object query correctly", e);
            }
        } else {
            fallbackEncoder.encode(parametersObject, bodyType, template);
        }
    }

    private String keyToTemplate(String key) {
        return "{" + key + "}";
    }

}

