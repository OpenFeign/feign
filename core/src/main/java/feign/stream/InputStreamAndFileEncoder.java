package feign.stream;

import static java.lang.String.format;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;

import feign.Request.OutputStreamSender;
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
	    		
	    		// Support some degree of retry - if the stream is too long, then any retry will throw
	    		final int BUFSIZE = 8092;
	    		
	    		InputStream is = (InputStream)object;
	    		InputStream streamToSend = is.markSupported() ? is : new BufferedInputStream(is, BUFSIZE);
	    		streamToSend.mark(BUFSIZE);
	    		
	    		OutputStreamSender sender = os -> {
	    			streamToSend.reset();
	    			Util.copy(streamToSend, os);
	    		};
	    		
	    		template.bodyOutputStreamSender(sender, "-- Binary Data (Unknown Length) --");
	    		
	    		return;
	    	}
	    	
	    	if (File.class.isAssignableFrom(bodyClass)) {
				File file = (File)object;
				
				if (!file.isFile())
					throw new EncodeException(format("Unable to encode missing file - %s", file));
				
				template.bodyOutputStreamSender(os -> Files.copy(file.toPath(), os), "-- Content of " + file + " (" + file.length() + " bytes) --");
				template.header(Util.CONTENT_LENGTH, Long.toString(file.length()));
				
	    		return;
	    	}
	    	
		}
    	
      
		if (delegateEncoder != null) {
			delegateEncoder.encode(object, bodyType, template);
			return;
		}

		throw new EncodeException(format("%s is not a type supported by this encoder.", object.getClass()));

    }
}
