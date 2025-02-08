package feign.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.Reader;
import java.util.Random;

import org.junit.jupiter.api.Test;

import feign.Feign;
import feign.RequestLine;
import feign.Util;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

public class InputStreamAndReaderDecoderTest {
	  public final MockWebServer server = new MockWebServer();

	  interface LargeStreamTestInterface {

		    @RequestLine("GET /")
		    InputStream getLargeStream();
		    
		    @RequestLine("GET /")
		    Reader getLargeReader();
		    
	  }
	  
	  @Test
	  void streamingResponse() throws Exception{
		    byte[] expectedResponse = new byte[16184];
			new Random().nextBytes(expectedResponse);
		    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse)));

		    LargeStreamTestInterface api = Feign.builder()
		    		.decoder(new InputStreamAndReaderDecoder(null))
		        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

		    try(InputStream is = api.getLargeStream()){
		    	byte[] out = is.readAllBytes();
		    	assertThat(out.length).isEqualTo(expectedResponse.length);
		    	assertThat(out).isEqualTo(expectedResponse);
		    }
	  }
	  
	  @Test
	  void streamingReaderResponse() throws Exception{
		    String expectedResponse = new Random().ints(1, 1500 + 1)
		    	      .limit(16184)
		    	      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
		    	      .toString();

		    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse.getBytes(Util.UTF_8))).addHeader("content-type", "text/plan; charset=utf-8"));

		    LargeStreamTestInterface api = Feign.builder()
		    		.decoder(new InputStreamAndReaderDecoder(null))
		        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

		    try(Reader r = api.getLargeReader()){
		    	String out = Util.toString(r);
		    	assertThat(out.length()).isEqualTo(expectedResponse.length());
		    	assertThat(out).isEqualTo(expectedResponse);
		    }
	  }  
	  
	  @Test
	  void streamingReaderResponseWithNoCharset() throws Exception{
		    String expectedResponse = new Random().ints(1, 1500 + 1)
		    	      .limit(16184)
		    	      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
		    	      .toString();

		    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse.getBytes(Util.UTF_8))).addHeader("content-type", "text/plan"));

		    LargeStreamTestInterface api = Feign.builder()
		    		.decoder(new InputStreamAndReaderDecoder(null))
		        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

		    try(Reader r = api.getLargeReader()){
		    	String out = Util.toString(r);
		    	assertThat(out.length()).isEqualTo(expectedResponse.length());
		    	assertThat(out).isEqualTo(expectedResponse);
		    }
	  }    

}
