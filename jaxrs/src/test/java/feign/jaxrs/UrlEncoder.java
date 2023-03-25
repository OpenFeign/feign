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