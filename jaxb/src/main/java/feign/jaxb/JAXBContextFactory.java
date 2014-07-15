package feign.jaxb;

import javax.xml.bind.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JAXBContextFactory {
    private final ConcurrentHashMap<Class, JAXBContext> jaxbContexts = new ConcurrentHashMap<Class, JAXBContext>(64);
    private Map<String, Object> properties = new HashMap<String, Object>(5);

    public JAXBContextFactory(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Unmarshaller createUnmarshaller(Class<?> clazz) throws JAXBException {
        JAXBContext ctx = getContext(clazz);
        return ctx.createUnmarshaller();
    }

    public Marshaller createMarshaller(Class<?> clazz) throws JAXBException {
        JAXBContext ctx = getContext(clazz);
        Marshaller marshaller = ctx.createMarshaller();
        setMarshallerProperties(marshaller);
        return marshaller;
    }

    private void setMarshallerProperties(Marshaller marshaller) throws PropertyException {
        Iterator<String> keys = properties.keySet().iterator();

        while(keys.hasNext()) {
            String key = keys.next();
            marshaller.setProperty(key, properties.get(key));
        }
    }

    private JAXBContext getContext(Class<?> clazz) throws JAXBException {
        JAXBContext jaxbContext = this.jaxbContexts.get(clazz);
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(clazz);
            this.jaxbContexts.putIfAbsent(clazz, jaxbContext);
        }
        return jaxbContext;
    }
}
