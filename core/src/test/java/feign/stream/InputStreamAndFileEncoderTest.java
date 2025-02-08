package feign.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import feign.Feign;
import feign.FeignException;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Response;
import feign.RetryableException;
import feign.codec.StringDecoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class InputStreamAndFileEncoderTest {
	  public final MockWebServer server = new MockWebServer();
	  private static final Long NON_RETRYABLE = null;

	  interface LargeStreamTestInterface {

		    @RequestLine("POST /")
		    String postLargeStream(InputStream stream);

		    @RequestLine("POST /")
		    void postLargeFile(File file);

	  }
	  
	  @Test
	  void streamingRequest() throws Exception{
		    server.enqueue(new MockResponse());

		    byte[] expectedResponse = new byte[16184];
			new Random().nextBytes(expectedResponse);
		    
		    
		    LargeStreamTestInterface api = Feign.builder()
		    		.encoder(new InputStreamAndFileEncoder(null))
		        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

		    try(InputStream is = new ByteArrayInputStream(expectedResponse)){
		    	api.postLargeStream(is);
		    }
		    
		    byte[] result = server.takeRequest().getBody().readByteArray();
		    
		    assertThat(result.length).isEqualByComparingTo(expectedResponse.length);
		    assertThat(result).isEqualTo(expectedResponse);
	  }
	  
	  Feign.Builder createRetryableFeignBuilder(){
		  return Feign.builder()
			.encoder(new InputStreamAndFileEncoder(null))
			.decoder(
	              new StringDecoder() {
	                @Override
	                public Object decode(Response response, Type type) throws IOException {
	                  String string = super.decode(response, type).toString();
	                  if ("retry!".equals(string)) {
	                    throw new RetryableException(
	                        response.status(),
	                        string,
	                        HttpMethod.POST,
	                        NON_RETRYABLE,
	                        response.request());
	                  }
	                  return string;
	                }
	              });
	  }
	  
	  @Test
	  void streamingRequestCanRetry() throws Exception {
		  
	    server.enqueue(new MockResponse().setBody("retry!"));
	    server.enqueue(new MockResponse().setBody("success!"));

	    LargeStreamTestInterface api = createRetryableFeignBuilder()
	        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());
	    
	    byte[] requestData = new byte[16184];
		new Random().nextBytes(requestData);
	    
		// if we use a plain ByteArrayInputStream, then it is infinitely resettable, so the following will retry 
		// if instead we wrapped this in a reset limited inputstream (like a BufferedInputStream with size set to 1024), retry would fail.
		// As of now, I don't see a way to tell the server to read *some* of the request and then fail, so we aren't getting good test coverage of the partial request reset scenario
	    String rslt = api.postLargeStream(new ByteArrayInputStream(requestData));
	    byte[] dataReceivedByServer = server.takeRequest().getBody().readByteArray();
	    
	    assertThat(rslt).isEqualTo("success!");
	    assertThat(server.getRequestCount()).isEqualTo(2);
	    assertThat(dataReceivedByServer).isEqualTo(requestData);
	  }  
	  
	  @Test
	  void streamingRequestRetryFailsIfTooMuchDataRead() throws Exception {
		  
	    server.enqueue(new MockResponse().setBody("retry!"));
	    server.enqueue(new MockResponse().setBody("success!"));

	    LargeStreamTestInterface api = createRetryableFeignBuilder()
	        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());
	    
	    byte[] requestData = new byte[16184];
		new Random().nextBytes(requestData);
	    
		// if we wrap the inputstream in a reset limited inputstream like a BufferedInputStream, retry will fail
		// As of now, I don't see a way to tell the server to read *some* of the request and then fail, so we aren't getting good test coverage of the partial request reset scenario
	    FeignException e = assertThrows(FeignException.class, () -> api.postLargeStream(new BufferedInputStream(new ByteArrayInputStream(requestData), 1024)) );
	    
	    assertThat(e).hasCauseInstanceOf(IOException.class);
	  }    
	  
	  @Test
	  void streamingFileRequest(@TempDir Path tempPath) throws Exception{
		    server.enqueue(new MockResponse());

		    byte[] expectedResponse = new byte[16184];
			new Random().nextBytes(expectedResponse);
		    
			Path fileToSend = tempPath.resolve("temp.dat"); 
			
			Files.write(fileToSend, expectedResponse);
		    
		    LargeStreamTestInterface api = Feign.builder()
		    		.encoder(new InputStreamAndFileEncoder(null))
		        	.target( LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

		    api.postLargeFile(fileToSend.toFile());
		    
		    byte[] result = server.takeRequest().getBody().readByteArray();
		    
		    assertThat(result.length).isEqualByComparingTo(expectedResponse.length);
		    assertThat(result).isEqualTo(expectedResponse);
	  }

}
