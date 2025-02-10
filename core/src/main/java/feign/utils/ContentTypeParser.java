/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
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
