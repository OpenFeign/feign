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
package feign.metrics4;


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.capability.invocation.DelegateInvocationFactory;
import feign.capability.invocation.DelegateInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import feign.*;

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


    private final MetricRegistry metricRegistry;

    private final FeignMetricName metricName;

    private final MetricSuppliers metricSuppliers;

    public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
                                          MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
        super(invocationHandler);
        this.metricRegistry = metricRegistry;
        this.metricSuppliers = metricSuppliers;
        this.metricName = new FeignMetricName(Feign.class);
    }


    @Override
    protected DelegateInvocationHandler create(Target target, Map<Method, MethodHandler> dispatch, InvocationHandler invocationHandle) {
        return new MeteredInvocationHandle(target.type(), target.url(), metricRegistry, metricName, metricSuppliers, invocationHandle);
    }

    protected static class MeteredInvocationHandle implements DelegateInvocationHandler {
        protected final MetricRegistry metricRegistry;

        protected final FeignMetricName metricName;

        protected final MetricSuppliers metricSuppliers;

        protected final InvocationHandler invocationHandler;

        protected final Class clientClass;

        protected final String url;

        public MeteredInvocationHandle(Class clazz, String url, MetricRegistry metricRegistry, FeignMetricName metricName, MetricSuppliers metricSuppliers, InvocationHandler invocationHandler) {
            this.metricRegistry = metricRegistry;
            this.metricName = metricName;
            this.metricSuppliers = metricSuppliers;
            this.invocationHandler = invocationHandler;
            this.clientClass = clazz;
            this.url = url;
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

            try (final Timer.Context classTimer =
                         metricRegistry.timer(metricName.metricName(clientClass, method, url),
                                 metricSuppliers.timers()).time()) {

                return invocationHandler.invoke(proxy, method, args);
            } catch (final FeignException e) {
                metricRegistry.meter(
                        MetricRegistry.name(metricName.metricName(clientClass, method, url),
                                "http_error", e.status() / 100 + "xx", String.valueOf(e.status())),
                        metricSuppliers.meters()).mark();

                throw e;
            } catch (final Throwable e) {
                metricRegistry.meter(
                        MetricRegistry.name(metricName.metricName(clientClass, method, url),
                                "exception", e.getClass().getSimpleName()),
                        metricSuppliers.meters())
                        .mark();

                throw e;
            }
        }
    }

}
