package feign.form.feign.spring.converter;

import static java.util.Collections.singletonList;

import feign.form.spring.converter.SpringManyMultipartFilesReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public class SpringManyMultipartFilesReaderTest {

  private static final String DUMMY_MULTIPART_BOUNDARY = "Boundary_4_574237629_1500021738802";

  @Test
  public void readMultipartFormDataTest() throws IOException {
    final SpringManyMultipartFilesReader multipartFilesReader =
        new SpringManyMultipartFilesReader(4096);
    final MultipartFile[] multipartFiles =
        multipartFilesReader.read(MultipartFile[].class, new ValidMulitpartMessage());

    Assert.assertEquals(2, multipartFiles.length);

    Assert.assertEquals(MediaType.APPLICATION_JSON_VALUE, multipartFiles[0].getContentType());
    Assert.assertEquals("form-item-1", multipartFiles[0].getName());
    Assert.assertFalse(multipartFiles[0].isEmpty());

    Assert.assertEquals(MediaType.TEXT_PLAIN_VALUE, multipartFiles[1].getContentType());
    Assert.assertEquals("form-item-2-file-1", multipartFiles[1].getOriginalFilename());
    Assert.assertEquals(
        "Plain text", IOUtils.toString(multipartFiles[1].getInputStream(), "US-ASCII"));
  }

  public static class ValidMulitpartMessage implements HttpInputMessage {
    @Override
    public InputStream getBody() throws IOException {
      final String multipartBody =
          "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "\r\n"
              + "Content-Type: application/json\r\n"
              + "Content-Disposition: form-data; name=\"form-item-1\"\r\n"
              + "\r\n"
              + "{\"id\":1}"
              + "\r\n"
              + "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "\r\n"
              + "content-type: text/plain\r\n"
              + "content-disposition: Form-Data; Filename=\"form-item-2-file-1\";"
              + " Name=\"form-item-2\"\r\n"
              + "\r\n"
              + "Plain text\r\n"
              + "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "--\r\n";

      return new ByteArrayInputStream(multipartBody.getBytes("US-ASCII"));
    }

    @Override
    public HttpHeaders getHeaders() {
      final HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.put(
          HttpHeaders.CONTENT_TYPE,
          singletonList(
              MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + DUMMY_MULTIPART_BOUNDARY));
      return httpHeaders;
    }
  }
}
