package feign.jsonpath;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
    if ("equals".equals(method.getName())) {
      return equal(args);
    } else if ("hashCode".equals(method.getName())) {
      return document.hashCode() + type.hashCode();
    } else if ("toString".equals(method.getName())) {
      return type + ": " + document.toString();
    }

    JsonExpr path = method.getAnnotation(JsonExpr.class);
    if(path == null)
      throw new IllegalStateException("Method not annotated with @JsonExpr " + method);

    return document.read(path.value());
  }

  private Object equal(Object[] args) {
    try {
      Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
      if (otherHandler instanceof JsonExprInvocationHandler) {
        JsonExprInvocationHandler other = (JsonExprInvocationHandler) otherHandler;
        return document.equals(other.document);
      }
      return false;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
  

}
