package feign.form.spring.converter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

/**
 * Straight-forward implementation of interface {@link MultipartFile} where the file data is held as
 * a byte array in memory.
 */
final class ByteArrayMultipartFile implements MultipartFile {

  private final String name;
  private final String originalFileName;
  private final String contentType;
  private final byte[] data;

  ByteArrayMultipartFile(
      final String name,
      final String originalFileName,
      final String contentType,
      final byte[] data) {
    Assert.notNull(data, "Byte array data may not be null!");

    this.name = name;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.data = data;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalFilename() {
    return originalFileName;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isEmpty() {
    return data.length == 0;
  }

  @Override
  public long getSize() {
    return data.length;
  }

  @Override
  public byte[] getBytes() {
    return data;
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(data);
  }

  @Override
  public void transferTo(final File destination) throws IOException {
    final FileOutputStream fos = new FileOutputStream(destination);
    try {
      fos.write(data);
    } finally {
      fos.close();
    }
  }
}
