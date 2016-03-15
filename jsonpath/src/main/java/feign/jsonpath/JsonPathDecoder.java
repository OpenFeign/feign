package feign.jsonpath;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;

public class JsonPathDecoder implements Decoder {

  private final Configuration configuration;

  public JsonPathDecoder(Configuration configuration) {
    super();
    this.configuration = configuration;
  }

  public JsonPathDecoder() {
    this(Configuration.defaultConfiguration());
  }

  @Override
  public Object decode(Response response, final Type type) throws IOException, DecodeException, FeignException {
    if (response.status() == 404)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;

	final DocumentContext document = JsonPath.parse(response.body().asInputStream(), configuration);
	if(document.read("$.length()") == null)
		return null;
    return Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] { (Class<?>) type },
        new JsonExprInvocationHandler(type, document));
  }

}
