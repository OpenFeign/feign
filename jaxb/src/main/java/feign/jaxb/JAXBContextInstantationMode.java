/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.jaxb;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Provides differents ways to instantiate a JAXB Context.
 */
public enum JAXBContextInstantationMode {

  CLASS {
    @Override
    JAXBContextCacheKey getJAXBContextCacheKey(Class<?> clazz) {
      return new JAXBContextClassCacheKey(clazz);
    }

    @Override
    JAXBContext getJAXBContext(Class<?> clazz) throws JAXBException {
      return JAXBContext.newInstance(clazz);
    }
  },

  PACKAGE {
    @Override
    JAXBContextCacheKey getJAXBContextCacheKey(Class<?> clazz) {
      return new JAXBContextPackageCacheKey(clazz.getPackage().getName(), clazz.getClassLoader());
    }

    @Override
    JAXBContext getJAXBContext(Class<?> clazz) throws JAXBException {
      return JAXBContext.newInstance(clazz.getPackage().getName(), clazz.getClassLoader());
    }
  };

  abstract JAXBContextCacheKey getJAXBContextCacheKey(Class<?> clazz);

  abstract JAXBContext getJAXBContext(Class<?> clazz) throws JAXBException;
}
