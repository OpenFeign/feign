package feign.capability.invocation;

import feign.Capability;
import feign.InvocationHandlerFactory;


public class MergeInvocationCapability implements Capability {
    private DelegateInvocationFactory delegateInvocationFactory;

    public MergeInvocationCapability(DelegateInvocationFactory delegateInvocationFactory) {
        this.delegateInvocationFactory = delegateInvocationFactory;
    }

    @Override
    public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
        delegateInvocationFactory.setDelegateFactory(invocationHandlerFactory);
        return delegateInvocationFactory;
    }


}
