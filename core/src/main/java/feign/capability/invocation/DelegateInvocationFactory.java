/**
 * Copyright 2012-2020 The Feign Authors
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
