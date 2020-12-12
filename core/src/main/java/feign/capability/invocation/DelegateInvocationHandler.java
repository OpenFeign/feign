package feign.capability.invocation;

import java.lang.reflect.InvocationHandler;

public interface DelegateInvocationHandler extends InvocationHandler {

    InvocationHandler getNextInvocationHandler();
}
