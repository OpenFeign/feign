/**
 * Copyright 2012-2020 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.metrics5;


import feign.*;
import feign.capability.invocation.DelegateInvocationFactory;
import feign.capability.invocation.DelegateInvocationHandler;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.Timer.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Warp feign {@link InvocationHandler} with metrics.
 */
public class MeteredInvocationHandleFactory extends DelegateInvocationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MeteredInvocationHandleFactory.class);

    /**
     * Methods that are declared by super class object and, if invoked, we don't wanna record metrics
     * for
     */
    private static final List<String> JAVA_OBJECT_METHODS =
            Arrays.asList("equals", "toString", "hashCode");


    protected final MetricRegistry metricRegistry;

    protected final FeignMetricName metricName;

    protected final MetricSuppliers metricSuppliers;

    public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
                                           MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
        super(invocationHandler);
        this.metricRegistry = metricRegistry;
        this.metricSuppliers = metricSuppliers;
        this.metricName = new FeignMetricName(Feign.class);
    }


    @Override
    protected DelegateInvocationHandler create(Target target, Map<Method, MethodHandler> dispatch, InvocationHandler handler) {
        return new MeteredInvocationHandle(target, handler, metricRegistry, metricName, metricSuppliers);
    }

    protected class MeteredInvocationHandle implements DelegateInvocationHandler {
        protected InvocationHandler invocationHandler;
        protected final MetricRegistry metricRegistry;
        protected final FeignMetricName metricName;
        protected final MetricSuppliers metricSuppliers;
        protected final String url;
        protected final Class clientClass;

        public MeteredInvocationHandle(Target target, InvocationHandler invocationHandler, MetricRegistry metricRegistry, FeignMetricName metricName, MetricSuppliers metricSuppliers) {
            this.invocationHandler = invocationHandler;
            this.metricRegistry = metricRegistry;
            this.metricName = metricName;
            this.metricSuppliers = metricSuppliers;
            url = target.url();
            clientClass = target.type();
        }

        @Override
        public InvocationHandler getNextInvocationHandler() {
            return invocationHandler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (JAVA_OBJECT_METHODS.contains(method.getName())
                    || Util.isDefault(method)) {
                LOG.trace("Skipping metrics for method={}", method);
                return invocationHandler.invoke(proxy, method, args);
            }

            try (final Context classTimer =
                         metricRegistry.timer(metricName.metricName(clientClass, method, url),
                                 metricSuppliers.timers()).time()) {

                return invocationHandler.invoke(proxy, method, args);
            } catch (final FeignException e) {
                metricRegistry.meter(
                        metricName.metricName(clientClass, method, url)
                                .resolve("http_error")
                                .tagged("http_status", String.valueOf(e.status()))
                                .tagged("error_group", e.status() / 100 + "xx"),
                        metricSuppliers.meters()).mark();

                throw e;
            } catch (final Throwable e) {
                metricRegistry
                        .meter(metricName.metricName(clientClass, method, url)
                                        .resolve("exception")
                                        .tagged("exception_name", e.getClass().getSimpleName()),
                                metricSuppliers.meters())
                        .mark();

                throw e;
            }
        }
    }


}
