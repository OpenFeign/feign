package feign.form.spring;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.FormEncoder;

/**
 * Adds support for {@link MultipartFile} type to {@link FormEncoder}.
 * 
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 14.09.2016
 */
public class SpringFormEncoder extends FormEncoder {
    
    private final Encoder delegate;

    public SpringFormEncoder () {
        this(new Encoder.Default());
    }

    public SpringFormEncoder(Encoder delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        if (!bodyType.equals(MultipartFile.class)) {
            delegate.encode(object, bodyType, template);
            return;
        }
        
        MultipartFile file = (MultipartFile) object;
        Map<String, Object> data = Collections.singletonMap(file.getName(), object);
        new SpringMultipartEncodedDataProcessor().process(data, template);
    }

}
