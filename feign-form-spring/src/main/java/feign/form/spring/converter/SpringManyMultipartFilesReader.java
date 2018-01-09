package feign.form.spring.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.MultipartStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link HttpMessageConverter} that can read multipart/form-data HTTP bodies
 * (writing is not handled because that is already supported by {@link FormHttpMessageConverter}).
 *
 * <p>
 * This reader supports an array of {@link MultipartFile} as the mapping return class type - each
 * multipart body is read into an underlying byte array (in memory) implemented via
 * {@link ByteArrayMultipartFile}.
 */
public class SpringManyMultipartFilesReader extends AbstractHttpMessageConverter<MultipartFile[]> {

    private static final Pattern NEWLINES_PATTERN = Pattern.compile("\\R");
    private static final Pattern COLON_PATTERN = Pattern.compile(":");

    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");
    private static final Pattern EQUALITY_SIGN_PATTERN = Pattern.compile("=");

    private final int bufSize;

    /**
     * Construct an {@code AbstractHttpMessageConverter} that can read mulitpart/form-data.
     * @param bufSize The size of the buffer (in bytes) to read the HTTP multipart body.
     */
    public SpringManyMultipartFilesReader(final int bufSize) {
        super(MediaType.MULTIPART_FORM_DATA);
        this.bufSize = bufSize;
    }

    @Override
    protected boolean canWrite(final MediaType mediaType) {
        return false; // Class NOT meant for writing multipart/form-data HTTP bodies
    }

    @Override
    protected boolean supports(final Class<?> clazz) {
        return MultipartFile[].class == clazz;
    }

    @Override
    protected MultipartFile[] readInternal(final Class<? extends MultipartFile[]> clazz,
                                           final HttpInputMessage inputMessage) throws IOException {
        final byte[] boundary = getMultiPartBoundary(inputMessage.getHeaders().getContentType());
        final MultipartStream multipartStream = new MultipartStream(inputMessage.getBody(), boundary, bufSize, null);

        final List<ByteArrayMultipartFile> multiparts = new LinkedList<ByteArrayMultipartFile>();
        for (boolean nextPart = multipartStream.skipPreamble(); nextPart; nextPart = multipartStream.readBoundary()) {
            try {
                multiparts.add(readMultiPart(multipartStream));
            } catch (final Exception e) {
                throw new HttpMessageNotReadableException("Multipart body could not be read.", e);
            }
        }

        return multiparts.toArray(new ByteArrayMultipartFile[multiparts.size()]);
    }

    @Override
    protected void writeInternal(final MultipartFile[] byteArrayMultipartFiles, final HttpOutputMessage outputMessage) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support writing to HTTP body.");
    }

    private byte[] getMultiPartBoundary(final MediaType contentType) {
        final String boundaryStr = unquote(contentType.getParameter("boundary"));
        if (!StringUtils.isEmpty(boundaryStr)) {
            return boundaryStr.getBytes();
        } else {
            throw new HttpMessageNotReadableException("Content-Type missing boundary information.");
        }
    }

    private ByteArrayMultipartFile readMultiPart(final MultipartStream multipartStream) throws IOException {
        final IgnoreKeyCaseMap multiPartHeaders = splitIntoKeyValuePairs(multipartStream.readHeaders(),
                NEWLINES_PATTERN, COLON_PATTERN, false);

        final String multipartContentType = multiPartHeaders.get(HttpHeaders.CONTENT_TYPE);
        final IgnoreKeyCaseMap contentDisposition = splitIntoKeyValuePairs(
                multiPartHeaders.get(HttpHeaders.CONTENT_DISPOSITION), SEMICOLON_PATTERN, EQUALITY_SIGN_PATTERN, true);

        if (!contentDisposition.containsKey("form-data")) {
            throw new HttpMessageNotReadableException("Content-Disposition is not of type form-data.");
        }

        final ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        multipartStream.readBodyData(bodyStream);

        return new ByteArrayMultipartFile(contentDisposition.get("name"), contentDisposition.get("filename"),
                multipartContentType, bodyStream.toByteArray());
    }

    private IgnoreKeyCaseMap splitIntoKeyValuePairs(final String str,
                                                    final Pattern entriesSeparatorPattern,
                                                    final Pattern keyValueSeparatorPattern,
                                                    final boolean unquoteValue) {
        final IgnoreKeyCaseMap keyValuePairs = new IgnoreKeyCaseMap();
        if (!StringUtils.isEmpty(str)) {
            final String[] entries = entriesSeparatorPattern.split(str);
            for (final String entry : entries) {
                final String[] pair = keyValueSeparatorPattern.split(entry.trim(), 2);
                final String key = pair[0].trim();
                final String value = pair.length > 1 ? pair[1].trim() : "";
                keyValuePairs.put(key, unquoteValue ? unquote(value) : value);
            }
        }
        return keyValuePairs;
    }

    private String unquote(final String value) {
        return value != null
                ? (isSurroundedBy(value, "\"") || isSurroundedBy(value, "'")
                        ? value.substring(1, value.length() - 1)
                        : value)
                : null;
    }

    private boolean isSurroundedBy(final String value, final String preSuffix) {
        return value.length() > 1 && value.startsWith(preSuffix) && value.endsWith(preSuffix);
    }
}
