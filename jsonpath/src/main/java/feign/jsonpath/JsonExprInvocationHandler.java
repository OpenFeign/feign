package feign.jsonpath;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.jayway.jsonpath.DocumentContext;

public class JsonExprInvocationHandler implements InvocationHandler {

  private final Type type;
  private final DocumentContext document;

  public JsonExprInvocationHandler(Type type, DocumentContext document) {
    this.type = type;
    this.document = document;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getName().equals("toString"))
      return type + ":\n" + document.jsonString();

    JsonExpr path = method.getAnnotation(JsonExpr.class);
    if(path == null)
      throw new IllegalStateException("Method not annotated with @JsonExpr " + method);

    return document.read(path.value());
  }
}
