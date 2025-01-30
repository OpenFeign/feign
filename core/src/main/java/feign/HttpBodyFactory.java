package feign;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Optional;

public class HttpBodyFactory {
	private static final int RESET_SIZE = 8092;
	private static final long UNKNOWN_SIZE = -1;
	private static final Charset UNKNOWN_ENCODING = null;

	private HttpBodyFactory() {
		
	}

	public static HttpBody forInputStream(InputStream is) {
		return forInputStream(is, UNKNOWN_SIZE, UNKNOWN_ENCODING);
	}

	public static HttpBody forInputStream(InputStream is, long length) {
		return forInputStream(is, length, UNKNOWN_ENCODING);
	}

	public static HttpBody forInputStream(InputStream is, Charset encoding) {
		return forInputStream(is, UNKNOWN_SIZE, encoding);
	}
	
	public static HttpBody forInputStream(InputStream is, long length, Charset encoding) {
		return new StreamHttpBody(is, RESET_SIZE, length, encoding);
	}
	
	public static HttpBody forBytes(byte[] data, Charset encoding) {
		if (data == null || data.length == 0) return empty();
		
		return new StreamHttpBody(new ByteArrayInputStream(data), RESET_SIZE, data.length, encoding);
	}
	
	public static HttpBody empty() {
		return new StreamHttpBody(new ByteArrayInputStream(new byte[0]), RESET_SIZE, 0, null);
	}
	

	
	/**
	 * Represents the body of an http request or response.
	 * @implNote for now, we implement Response.Body to minimize disruption to existing code.  We also make the class have the same methods as the Request.Body class.
	 */
	public interface HttpBody extends Closeable, Response.Body{
		
		// ******** Non-deprecated methods ***********
		Optional<Charset> getEncoding();
		
		long getLength();
		
		InputStream asInputStream();
		
		Reader asReader();
		
		boolean tryReset();
		
		public PeekResult peek();
		
		public void close() throws IOException;
		
		
		// ******** Methods from Request.Body ***********
		//Optional<Charset> getEncoding();
		
		// Covered by Integer length()
		// public int length();
		
		//  throws IOException is a change from Request.Body - let's see if that breaks too many things
		@Deprecated
		byte[] asBytes();
		
		//  throws IOException is a change from Request.Body - let's see if that breaks too many things
		@Deprecated
		String asString() ;
		
		@Deprecated
		boolean isBinary();
		
		
		
		// ******** Methods from Response.Body ***********
	    /**
	     * length in bytes, if known. Null if unknown or greater than {@link Integer#MAX_VALUE}. <br>
	     * <br>
	     * <br>
	     * <b>Note</b><br>
	     * This is an integer as most implementations cannot do bodies greater than 2GB.
	     */
		@Deprecated
	    Integer length();

	    /** True if {@link #asInputStream()} and {@link #asReader()} can be called more than once. */
		@Deprecated
	    boolean isRepeatable();

	    ///** It is the responsibility of the caller to close the stream. */
	    //InputStream asInputStream() throws IOException;
		
//	    /**
//	     * It is the responsibility of the caller to close the stream.
//	     *
//	     * @deprecated favor {@link Body#asReader(Charset)}
//	     */
//	    @Deprecated
//	    default Reader asReader() throws IOException {
//	      return asReader(StandardCharsets.UTF_8);
//	    }

	    /** It is the responsibility of the caller to close the stream. */
		@Deprecated
	    Reader asReader(Charset charset) throws IOException;

	    
	}
	
    public static class StreamHttpBody implements HttpBody{
    	private final InputStream resettableInputStream;
    	private final Optional<Charset> encoding;
    	private final int resetSize;
    	private final long length;
    	
    	public StreamHttpBody(InputStream is, int resetSize, long length, Charset encoding) {
    		if (!is.markSupported())
    			is = new BufferedInputStream(is, resetSize);
    		is.mark(resetSize);
    		this.resettableInputStream = is;
    		this.resetSize = resetSize;
    		this.length = length;
    		this.encoding = Optional.ofNullable(encoding);
    	}
    	
    	public InputStream asInputStream() {
    		return resettableInputStream;
    	}
    	
    	public long getLength() {
    		return length;
    	}
    	
    	public Optional<Charset> getEncoding() {
    		return encoding;
    	}
    	
    	public Reader asReader() {
    		return getEncoding()
    				.map(e -> new InputStreamReader(resettableInputStream, e) )
    				.orElseThrow(() -> new IllegalArgumentException("No encoding specified, asReader() not allowed"));
    	}
    	
    	public boolean tryReset() {
    		try {
				resettableInputStream.reset();
				return true;
			} catch (IOException e) {
				return false;
			}
    	}
    	
    	public PeekResult peek() {
    		try {
	    		byte[] buf = new byte[resetSize];
	    		int count = resettableInputStream.read(buf);
	    		
	    		return new PeekResult(count >= resetSize, buf, count, encoding);
    		} catch (IOException e) {
    			throw FeignException.bodyException(e.getMessage(), e);
    		}
    	}
    	
		@Override
		public void close() throws IOException {
			resettableInputStream.close();
		}
    	
    	@Deprecated
		@Override
		public byte[] asBytes() {
			// TODO: Any way to do proper logging of these types of messages? 
			System.err.println("Deprecated method HttpBody.asBytes() called");
			
			try {
				return resettableInputStream.readAllBytes();
			} catch (IOException e) {
				throw FeignException.bodyException(e.getMessage(), e);
			}
		}

    	@Deprecated
		@Override
		public String asString() {
			// TODO: Any way to do proper logging of these types of messages? 
			System.err.println("Deprecated method HttpBody.asString() called");
			try {
				return Util.toString(asReader());
			} catch (IOException e) {
				throw FeignException.bodyException(e.getMessage(), e);
			}
		}

    	@Deprecated
		@Override
		public boolean isBinary() {
			return encoding.isEmpty();
		}

    	@Deprecated
		@Override
		public Integer length() {
			// TODO: Any way to do proper logging of these types of messages? 
			System.err.println("Deprecated method HttpBody.length() called");
			if (length > Integer.MAX_VALUE) throw new IllegalArgumentException("Length is larger than Integer.MAX_VALUE");
			return length == -1 ? null : Integer.valueOf((int)length);
		}

    	@Deprecated
		@Override
		public boolean isRepeatable() {
			// TODO: Any way to do proper logging of these types of messages? 
			System.err.println("Deprecated method HttpBody.isRepeatable() called");
			return tryReset();
		}

    	@Deprecated
		@Override
		public Reader asReader(Charset charset) throws IOException {
			// TODO: Any way to do proper logging of these types of messages? 
			System.err.println("Deprecated method HttpBody.asReader(Charset) called");
			return new InputStreamReader(resettableInputStream, charset);
		}    	
    }

    public static class PeekResult{
    	private final boolean partial;
    	private final InputStream peekData;
    	private final Optional<Charset> encoding;
    	
    	PeekResult(boolean partial, byte[] peekData, int size, Optional<Charset> encoding){
    		this.partial = partial;
    		this.peekData = new ByteArrayInputStream(peekData, 0, size > 0 ? size : 0);
    		this.peekData.mark(size);
    		this.encoding = encoding;
    	}
    	
    	public Reader asReader() {
    		return encoding
    				.map(e -> new InputStreamReader(asInputStream(), e) )
    				.orElseThrow(() -> new IllegalArgumentException("No encoding specified, asReader() not allowed"));
    	}
    	
    	public InputStream asInputStream() {
    		try {
				peekData.reset();
	    		return peekData;
			} catch (IOException e) {
				throw FeignException.bodyException(e.getMessage(), e);
			}
    	}
    	
    	public boolean isPartial() {
    		return partial;
    	}
    	
    	public String asStringDescription() {
    		if (encoding.isEmpty())
    			return "BINARY DATA";

    		try {
	    		String rslt = Util.toString(asReader());
	    		
	    		if (isPartial())
	    			rslt = "PARTIAL: " + rslt;
	    		
	    		return rslt;
    		} catch (IOException e) {
    			return "Unable to determine body text - " + e.getMessage();
    		}
    	}
    }

}
