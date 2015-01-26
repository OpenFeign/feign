package feign.assertj;

import static org.assertj.core.error.ShouldNotContain.shouldNotContain;

import com.squareup.okhttp.mockwebserver.RecordedRequest;
import feign.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.internal.ByteArrays;
import org.assertj.core.internal.Failures;
import org.assertj.core.internal.Iterables;
import org.assertj.core.internal.Objects;

public final class RecordedRequestAssert
    extends AbstractAssert<RecordedRequestAssert, RecordedRequest> {

  ByteArrays arrays = ByteArrays.instance();
  Objects objects = Objects.instance();
  Iterables iterables = Iterables.instance();
  Failures failures = Failures.instance();

  public RecordedRequestAssert(RecordedRequest actual) {
    super(actual, RecordedRequestAssert.class);
  }

  public RecordedRequestAssert hasMethod(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.getMethod(), expected);
    return this;
  }

  public RecordedRequestAssert hasPath(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.getPath(), expected);
    return this;
  }

  public RecordedRequestAssert hasBody(String utf8Expected) {
    isNotNull();
    objects.assertEqual(info, actual.getUtf8Body(), utf8Expected);
    return this;
  }

  public RecordedRequestAssert hasGzippedBody(byte[] expectedUncompressed) {
    isNotNull();
    byte[] compressedBody = actual.getBody();
    byte[] uncompressedBody;
    try {
      uncompressedBody =
          Util.toByteArray(new GZIPInputStream(new ByteArrayInputStream(compressedBody)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    arrays.assertContains(info, uncompressedBody, expectedUncompressed);
    return this;
  }

  public RecordedRequestAssert hasBody(byte[] expected) {
    isNotNull();
    arrays.assertContains(info, actual.getBody(), expected);
    return this;
  }

  public RecordedRequestAssert hasHeaders(String... headers) {
    isNotNull();
    iterables.assertContainsSubsequence(info, actual.getHeaders(), headers);
    return this;
  }

  public RecordedRequestAssert hasNoHeaderNamed(final String... names) {
    isNotNull();
    Set<String> found = new LinkedHashSet<String>();
    for (String header : actual.getHeaders()) {
      for (String name : names) {
        if (header.toLowerCase().startsWith(name.toLowerCase() + ":")) {
          found.add(header);
        }
      }
    }
    if (found.isEmpty()) {
      return this;
    }
    throw failures.failure(info, shouldNotContain(actual.getHeaders(), names, found));
  }
}
