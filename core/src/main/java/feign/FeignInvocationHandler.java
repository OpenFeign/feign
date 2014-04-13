package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static feign.Util.checkNotNull;

/**
* User: spencergibb
* Date: 4/12/14
* Time: 2:19 PM
*/
public class FeignInvocationHandler implements InvocationHandler {

  private final Target target;
  private final Map<Method, MethodHandler> methodToHandler;

  FeignInvocationHandler(Target target, Map<Method, MethodHandler> methodToHandler) {
    this.target = checkNotNull(target, "target");
    this.methodToHandler = checkNotNull(methodToHandler, "methodToHandler for %s", target);
  }

  @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("equals".equals(method.getName())) {
      try {
        Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
        return equals(otherHandler);
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    if ("hashCode".equals(method.getName())) {
      return hashCode();
    }
    return methodToHandler.get(method).invoke(args);
  }

  @Override public int hashCode() {
    return target.hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (FeignInvocationHandler.class != obj.getClass()) {
      return false;
    }
    FeignInvocationHandler that = FeignInvocationHandler.class.cast(obj);
    return this.target.equals(that.target);
  }

  @Override public String toString() {
    return "target(" + target + ")";
  }
}
