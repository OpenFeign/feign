package feign.slf4j;

import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static feign.Util.UTF_8;
import static feign.Util.checkNotNull;
import static java.util.Arrays.asList;

/**
 * Logs to SLF4J at the debug level, if the underlying logger has debug logging enabled.
 * The underlying logger can be specified at construction-time, defaulting to the logger
 * for {@link feign.Logger}.
 *
 * Logs one line per request/response/retry/error
 * Adds request-id to match request with response
 * Uses tab-separated key-value format.
 * It's very convenient to grep such logs in file by using {@code | cut -f 2} to fetch some column
 *
 * Example:
 *
 * {@code
 *  DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]
 *  call=[someMethod()]	method=[GET]	uri=[http://api.example.com]
 *
 *  DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]	status=[200]	reason=[OK]
 *  elapsed-ms=[273]	length=[0]
 * }
 *
 * @author lanwen (Merkushev Kirill)
 */
public class Slf4jTskvLogger extends Logger {
    private static final String REQ_ID_KEY = "req-id";
    private static final int HTTP_NO_CONTENT_204 = 204;
    private static final int HTTP_RESET_CONTENT_205 = 205;
    private final ThreadLocal<String> requestId = new ThreadLocal<String>();
    private final org.slf4j.Logger log;

    public Slf4jTskvLogger() {
        this(feign.Logger.class);
    }

    public Slf4jTskvLogger(Class<?> clazz) {
        this(LoggerFactory.getLogger(clazz));
    }

    public Slf4jTskvLogger(String name) {
        this(LoggerFactory.getLogger(name));
    }

    Slf4jTskvLogger(org.slf4j.Logger logger) {
        this.log = logger;
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        if (!log.isDebugEnabled()) {
            return;
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<Map.Entry<String, Object>>(asList(
                field(REQ_ID_KEY, reqId()),
                field("call", configKey),
                field("method", request.method()),
                field("uri", request.url())
        ));

        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            fields.add(field("headers", request.headers()));
        }

        if (request.body() != null) {
            fields.add(field("length", request.body().length));
            if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                String bodyText = request.charset() != null ? new String(request.body(), request.charset()) : null;
                fields.add(field("body", bodyText != null ? bodyText.replaceAll("\t", "\\\\t") : "binary_data"));
            }
        }

        log(fields);
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime)
            throws IOException {

        if (!log.isDebugEnabled()) {
            return response;
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<Map.Entry<String, Object>>(asList(
                field(REQ_ID_KEY, reqId()),
                field("status", response.status()),
                field("reason", response.reason()),
                field("elapsed-ms", elapsedTime)
        ));

        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            fields.add(field("headers", response.headers()));
        }

        int bodyLength;
        if (response.body() != null
                // HTTP 204 No Content "...response MUST NOT include a message-body"
                && !(response.status() == HTTP_NO_CONTENT_204
                // HTTP 205 Reset Content "...response MUST NOT include an entity"
                || response.status() == HTTP_RESET_CONTENT_205)) {

            byte[] bodyData = Util.toByteArray(response.body().asInputStream());
            bodyLength = bodyData.length;
            fields.add(field("length", bodyLength));
            if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0) {
                fields.add(
                        field("body", decodeOrDefault(bodyData, UTF_8, "binary_data")
                                .replaceAll("\t", "\\\\t"))
                );
            }
            log(fields);
            requestId.remove();
            return response.toBuilder().body(bodyData).build();
        }

        log(fields);
        requestId.remove();
        return response;
    }

    @Override
    protected void logRetry(String configKey, Level logLevel) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log(asList(
                field("state", "retry"),
                field(REQ_ID_KEY, requestId.get())
        ));
    }

    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        if (!log.isDebugEnabled()) {
            return ioe;
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<Map.Entry<String, Object>>(asList(
                field("state", "error"),
                field(REQ_ID_KEY, reqId()),
                field("class", ioe.getClass().getSimpleName()),
                field("message", ioe.getMessage()),
                field("elapsed-ms", elapsedTime)
        ));

        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            fields.add(field("trace", sw.toString().replaceAll("\t", " ")));
        }

        log(fields);
        return ioe;
    }


    @Override
    protected void log(String configKey, String format, Object... args) {
        log.debug(format, args);
    }

    private String reqId() {
        if (requestId.get() == null) {
            requestId.set(UUID.randomUUID().toString());
        }
        return requestId.get();
    }

    private void log(Collection<Map.Entry<String, Object>> tskv) {
        StringBuilder log = new StringBuilder();
        for (Map.Entry<String, Object> entry : tskv) {
            log.append("\t").append(entry.getKey()).append("=[").append(entry.getValue()).append("]");
        }

        log("", "http{}", log);
    }

    private static Map.Entry<String, Object> field(String key, Object value) {
        return new AbstractMap.SimpleImmutableEntry<String, Object>(key, value);
    }

    static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        checkNotNull(charset, "charset");
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) {
            return defaultValue;
        }
    }

}
