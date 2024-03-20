package feign.jaxrs3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import jakarta.ws.rs.core.*;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper class around an existing response. Using jersey with
 *   jakarta.ws.rs.core.Response
 *     .entity()
 *     .build()
 * will create an OutboundJaxrsResponse which doesn't implement readEntity-methods.
 * Only readEntity-Methods are implemented. Everything else will call the original response methods.
 */
public class JAXRS3Response extends Response {

    private final Response originalResponse;
    private final ObjectMapper objectMapper;

    public JAXRS3Response(Response originalResponse, ObjectMapper objectMapper) {
        this.originalResponse = originalResponse;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getStatus() {
        return originalResponse.getStatus();
    }

    @Override
    public StatusType getStatusInfo() {
        return originalResponse.getStatusInfo();
    }

    @Override
    public Object getEntity() {
        return originalResponse.getEntity();
    }

    @Override
    public <T> T readEntity(Class<T> entityType) {
        return readEntity(TypeFactory.defaultInstance().constructType(entityType));
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType) {
        return readEntity(objectMapper.constructType(entityType.getType()));
    }

    @Override
    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
        return readEntity(entityType);
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
        return readEntity(entityType);
    }

    @Override
    public boolean hasEntity() {
        return originalResponse.hasEntity();
    }

    @Override
    public boolean bufferEntity() {
        return originalResponse.bufferEntity();
    }

    @Override
    public void close() {
        originalResponse.close();
    }

    @Override
    public MediaType getMediaType() {
        return originalResponse.getMediaType();
    }

    @Override
    public Locale getLanguage() {
        return originalResponse.getLanguage();
    }

    @Override
    public int getLength() {
        return originalResponse.getLength();
    }

    @Override
    public Set<String> getAllowedMethods() {
        return originalResponse.getAllowedMethods();
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return originalResponse.getCookies();
    }

    @Override
    public EntityTag getEntityTag() {
        return originalResponse.getEntityTag();
    }

    @Override
    public Date getDate() {
        return originalResponse.getDate();
    }

    @Override
    public Date getLastModified() {
        return originalResponse.getLastModified();
    }

    @Override
    public URI getLocation() {
        return originalResponse.getLocation();
    }

    @Override
    public Set<Link> getLinks() {
        return originalResponse.getLinks();
    }

    @Override
    public boolean hasLink(String s) {
        return originalResponse.hasLink(s);
    }

    @Override
    public Link getLink(String s) {
        return originalResponse.getLink(s);
    }

    @Override
    public Link.Builder getLinkBuilder(String s) {
        return originalResponse.getLinkBuilder(s);
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return originalResponse.getMetadata();
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return originalResponse.getStringHeaders();
    }

    @Override
    public String getHeaderString(String s) {
        return originalResponse.getHeaderString(s);
    }

    private <T> T readEntity(JavaType type) {
        try {
            return objectMapper.readValue((String) getEntity(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
