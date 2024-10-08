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

class ParentPojo {
  public String parentPublicProperty;
  protected String parentProtectedProperty;
  private String parentPrivatePropertyAlteredByGetter;

  public String getParentPrivatePropertyAlteredByGetter() {
    return parentPrivatePropertyAlteredByGetter + "FromGetter";
  }

  public void setParentPrivatePropertyAlteredByGetter(String parentPrivatePropertyAlteredByGetter) {
    this.parentPrivatePropertyAlteredByGetter = parentPrivatePropertyAlteredByGetter;
  }

  public String getParentPublicProperty() {
    return parentPublicProperty;
  }

  public void setParentPublicProperty(String parentPublicProperty) {
    this.parentPublicProperty = parentPublicProperty;
  }

  public String getParentProtectedProperty() {
    return parentProtectedProperty;
  }

  public void setParentProtectedProperty(String parentProtectedProperty) {
    this.parentProtectedProperty = parentProtectedProperty;
  }
}

public class ChildPojo extends ParentPojo {
  private String childPrivateProperty;

  public String getChildPrivateProperty() {
    return childPrivateProperty;
  }

  public void setChildPrivateProperty(String childPrivateProperty) {
    this.childPrivateProperty = childPrivateProperty;
  }
}
