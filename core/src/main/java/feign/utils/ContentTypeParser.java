package feign.utils;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public final class ContentTypeParser {

	private ContentTypeParser() {
	}

	public static ContentTypeResult parseContentTypeFromHeaders(Map<String, Collection<String>> headers, String ifMissing) {
		// The header map *should* be a case insensitive treemap
		for (String val : headers.getOrDefault("content-type", Collections.emptyList())) {
			return parseContentTypeHeader(val);
		}
		
		return new ContentTypeResult(ifMissing, null);
	}
	
	public static ContentTypeResult parseContentTypeHeader(String contentTypeHeader) {

      String[] contentTypeParmeters = contentTypeHeader.split(";");
      String contentType = contentTypeParmeters[0];
      String charsetString = "";
      if (contentTypeParmeters.length > 1) {
        String[] charsetParts = contentTypeParmeters[1].split("=");
        if (charsetParts.length == 2 && "charset".equalsIgnoreCase(charsetParts[0].trim())) {
          // TODO: KD - this doesn't really implement the full parser definition for the content-type header (esp related to quoted strings, etc...) - see https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
          charsetString = charsetParts[1].trim();
          if (charsetString.length() > 1 &&  charsetString.startsWith("\"") && charsetString.endsWith("\""))
        	charsetString = charsetString.substring(1, charsetString.length()-1);
        }
      }
      
      return new ContentTypeResult(contentType, Charset.forName(charsetString, null));
	}
	
	public static class ContentTypeResult{
		public static final ContentTypeResult MISSING = new ContentTypeResult("", null);
		
		private String contentType;
		private Optional<Charset> charset;
		
		public ContentTypeResult(String contentType, Charset charset) {
			this.contentType = contentType;
			this.charset = Optional.ofNullable(charset);
		}
		
		public String getContentType() {
			return contentType;
		}
		
		public Optional<Charset> getCharset() {
			return charset;
		}
	}
}
