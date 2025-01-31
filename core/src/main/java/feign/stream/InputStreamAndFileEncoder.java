package feign.stream;

import static java.lang.String.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Type;

import feign.HttpBodyFactory;
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;

public class InputStreamAndFileEncoder implements Encoder {

	private final Encoder delegateEncoder;
	
	public InputStreamAndFileEncoder(Encoder delegateEncoder) {
		this.delegateEncoder = delegateEncoder;
	}
	
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
		if (bodyType instanceof Class) {
			Class<?> bodyClass = (Class<?>)bodyType;
	    	if (InputStream.class.isAssignableFrom(bodyClass)) {
	    		InputStream is = (InputStream)object;
				// TODO: KD - Should we pull encoding from the headers??
	    		template.body(HttpBodyFactory.forInputStream(is));
	    		if (!template.headers().containsKey(Util.CONTENT_TYPE))
	    			template.headerLiteral(Util.CONTENT_TYPE, "application/octet-stream");
	    		return;
	    	}
	    	
	    	if (File.class.isAssignableFrom(bodyClass)) {
	    		try {
					File file = (File)object;
					template.body(HttpBodyFactory.forFile(file));
		    		if (!template.headers().containsKey(Util.CONTENT_TYPE))
		    			template.headerLiteral(Util.CONTENT_TYPE, "application/octet-stream");
		    		return;
				} catch (FileNotFoundException e) {
					throw new EncodeException(format("Unable to encode missing file - %s", e.getMessage(), object.getClass()));
				}
	    	}
	    	
		}
    	
      
		if (delegateEncoder != null) {
			delegateEncoder.encode(object, bodyType, template);
			return;
		}

		throw new EncodeException(format("%s is not a type supported by this encoder.", object.getClass()));
      
//    	if (bodyType == String.class) {
//        template.body(object.toString());
//      } else if (bodyType == byte[].class) {
//        template.body((byte[]) object, null);
//      } else if (object != null) {
//        throw new EncodeException(
//            format("%s is not a type supported by this encoder.", object.getClass()));
//      }
    }
}
