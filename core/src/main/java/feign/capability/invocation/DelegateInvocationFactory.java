package feign.capability.invocation;

import feign.InvocationHandlerFactory;
import feign.Target;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * invoke enrich,for context
 *     invoke
 *        ↓
 *        ↓
 * [logInvocation]
 *        ↓
 *        ↓
 * [cacheInvocation] i am for it,redis,  guava , eCache ,spring cache, jetCache(it is good,support annotation and spring)
 *        ↓
 *        ↓
 * [hystrixInvocation] i think it is after cache
 *        ↓
 *        ↓
 * [DefaultInvocation] remote process call
 *
 */
public abstract class DelegateInvocationFactory implements InvocationHandlerFactory {
    protected InvocationHandlerFactory delegateFactory;


    public DelegateInvocationFactory(InvocationHandlerFactory delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public DelegateInvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
        InvocationHandler handler = delegateFactory.create(target, dispatch);
        return create(target, dispatch, handler);
    }

    protected abstract DelegateInvocationHandler create(Target target, Map<Method, MethodHandler> dispatch, InvocationHandler handler);

    public InvocationHandlerFactory getDelegateFactory() {
        return delegateFactory;
    }

    public void setDelegateFactory(InvocationHandlerFactory delegateFactory) {
        this.delegateFactory = delegateFactory;
    }
}
