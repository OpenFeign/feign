package feign;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;

public final class MethodMetadata implements Serializable {
  MethodMetadata() {
  }

  private String configKey;
  private transient TypeToken<?> returnType;
  private Integer urlIndex;
  private Integer bodyIndex;
  private RequestTemplate template = new RequestTemplate();
  private List<String> formParams = Lists.newArrayList();
  private SetMultimap<Integer, String> indexToName = LinkedHashMultimap.create();

  /**
   * @see Feign#configKey(Method)
   */
  public String configKey() {
    return configKey;
  }

  MethodMetadata configKey(String configKey) {
    this.configKey = configKey;
    return this;
  }

  public TypeToken<?> returnType() {
    return returnType;
  }

  MethodMetadata returnType(TypeToken<?> returnType) {
    this.returnType = returnType;
    return this;
  }

  public Integer urlIndex() {
    return urlIndex;
  }

  MethodMetadata urlIndex(Integer urlIndex) {
    this.urlIndex = urlIndex;
    return this;
  }

  public Integer bodyIndex() {
    return bodyIndex;
  }

  MethodMetadata bodyIndex(Integer bodyIndex) {
    this.bodyIndex = bodyIndex;
    return this;
  }

  public RequestTemplate template() {
    return template;
  }

  public List<String> formParams() {
    return formParams;
  }

  public SetMultimap<Integer, String> indexToName() {
    return indexToName;
  }

  private static final long serialVersionUID = 1L;
}