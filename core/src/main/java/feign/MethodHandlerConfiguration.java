/*
 * Copyright 2012-2024 The Feign Authors
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
package feign;

import java.util.List;

public class MethodHandlerConfiguration {

  private MethodMetadata metadata;

  private Target<?> target;

  private Retryer retryer;

  private List<RequestInterceptor> requestInterceptors;

  private Logger logger;

  private Logger.Level logLevel;

  private RequestTemplate.Factory buildTemplateFromArgs;

  private Request.Options options;

  private ExceptionPropagationPolicy propagationPolicy;

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

  public void setMetadata(MethodMetadata metadata) {
    this.metadata = metadata;
  }

  public void setTarget(Target<?> target) {
    this.target = target;
  }

  public void setRetryer(Retryer retryer) {
    this.retryer = retryer;
  }

  public void setRequestInterceptors(List<RequestInterceptor> requestInterceptors) {
    this.requestInterceptors = requestInterceptors;
  }

  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  public void setLogLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
  }

  public void setBuildTemplateFromArgs(RequestTemplate.Factory buildTemplateFromArgs) {
    this.buildTemplateFromArgs = buildTemplateFromArgs;
  }

  public void setOptions(Request.Options options) {
    this.options = options;
  }

  public void setPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
    this.propagationPolicy = propagationPolicy;
  }

  public MethodHandlerConfiguration(MethodMetadata metadata, Target<?> target,
      Retryer retryer, List<RequestInterceptor> requestInterceptors,
      Logger logger,
      Logger.Level logLevel, RequestTemplate.Factory buildTemplateFromArgs,
      Request.Options options, ExceptionPropagationPolicy propagationPolicy) {
    this.metadata = metadata;
    this.target = target;
    this.retryer = retryer;
    this.requestInterceptors = requestInterceptors;
    this.logger = logger;
    this.logLevel = logLevel;
    this.buildTemplateFromArgs = buildTemplateFromArgs;
    this.options = options;
    this.propagationPolicy = propagationPolicy;
  }
}
