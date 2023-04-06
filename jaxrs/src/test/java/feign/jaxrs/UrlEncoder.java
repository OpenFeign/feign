/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.jaxrs;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import feign.RequestTemplate;
import feign.codec.Encoder;

public final class UrlEncoder implements Encoder {

	@SuppressWarnings("unchecked")
	@Override
	public void encode(Object object, Type bodyType, RequestTemplate template) {
		String body = null;
		Set<String> namesToIgnoreEncode = template.getAlreadyEncoded();
		boolean namesToIgnoreEncodeNotEmpty = namesToIgnoreEncode != null && !namesToIgnoreEncode.isEmpty();

		if (object instanceof Map) {
			Map<String, String> map = (Map<String, String>) object;
			if (namesToIgnoreEncodeNotEmpty) {
				StringBuilder sb = new StringBuilder();
				for (Map.Entry<String, String> entry : map.entrySet()) {
					String key = entry.getKey(), value = entry.getValue();
					sb.append(key).append('=')
							.append(namesToIgnoreEncodeNotEmpty && namesToIgnoreEncode.contains(key)
									? value
									: URLEncoder.encode(value, Charset.defaultCharset()));
				}
				body = sb.toString();
			} else {
				List<BasicNameValuePair> nameValuePairs = map.entrySet().stream()
						.map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue())).toList();
				body = URLEncodedUtils.format(nameValuePairs, Charset.defaultCharset());
			}
		} else if (object instanceof String) {
			body = (String) object;
		}
		if (body != null) {
			template.body(body);
		}
	}

}