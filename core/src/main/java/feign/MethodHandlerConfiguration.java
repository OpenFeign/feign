/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static feign.Util.checkNotNull;

import java.util.List;

public class MethodHandlerConfiguration {

  private final MethodMetadata metadata;

  private final Target<?> target;

  private final Retryer retryer;

  private final List<RequestInterceptor> requestInterceptors;

  private final Logger logger;

  private final Logger.Level logLevel;

  private final RequestTemplate.Factory buildTemplateFromArgs;

  private final Request.Options options;

  private final ExceptionPropagationPolicy propagationPolicy;

  public MethodMetadata getMetadata() {
    return metadata;
  }

  public Target<?> getTarget() {
    return target;
  }

  public Retryer getRetryer() {
    return retryer;
  }

  public List<RequestInterceptor> getRequestInterceptors() {
    return requestInterceptors;
  }

  public Logger getLogger() {
    return logger;
  }

  public Logger.Level getLogLevel() {
    return logLevel;
  }

  public RequestTemplate.Factory getBuildTemplateFromArgs() {
    return buildTemplateFromArgs;
  }

  public Request.Options getOptions() {
    return options;
  }

  public ExceptionPropagationPolicy getPropagationPolicy() {
    return propagationPolicy;
  }

  public MethodHandlerConfiguration(
      MethodMetadata metadata,
      Target<?> target,
      Retryer retryer,
      List<RequestInterceptor> requestInterceptors,
      Logger logger,
      Logger.Level logLevel,
      RequestTemplate.Factory buildTemplateFromArgs,
      Request.Options options,
      ExceptionPropagationPolicy propagationPolicy) {
    this.target = checkNotNull(target, "target");
    this.retryer = checkNotNull(retryer, "retryer for %s", target);
    this.requestInterceptors =
        checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
    this.logger = checkNotNull(logger, "logger for %s", target);
    this.logLevel = checkNotNull(logLevel, "logLevel for %s", target);
    this.metadata = checkNotNull(metadata, "metadata for %s", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
    this.options = checkNotNull(options, "options for %s", target);
    this.propagationPolicy = propagationPolicy;
  }
}
