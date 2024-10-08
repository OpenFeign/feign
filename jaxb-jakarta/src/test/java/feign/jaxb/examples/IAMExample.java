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
package feign.jaxb.examples;

import feign.Feign;
import feign.Request;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Target;
import feign.jaxb.JAXBContextFactory;
import feign.jaxb.JAXBDecoder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

public class IAMExample {

  public static void main(String... args) {
    IAM iam =
        Feign.builder()
            .decoder(new JAXBDecoder(new JAXBContextFactory.Builder().build()))
            .target(new IAMTarget(args[0], args[1]));

    GetUserResponse response = iam.userResponse();
    System.out.println("UserId: " + response.result.user.id);
  }

  interface IAM {

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08")
    GetUserResponse userResponse();
  }

  static class IAMTarget extends AWSSignatureVersion4 implements Target<IAM> {

    private IAMTarget(String accessKey, String secretKey) {
      super(accessKey, secretKey);
    }

    @Override
    public Class<IAM> type() {
      return IAM.class;
    }

    @Override
    public String name() {
      return "iam";
    }

    @Override
    public String url() {
      return "https://iam.amazonaws.com";
    }

    @Override
    public Request apply(RequestTemplate in) {
      in.target(url());
      return super.apply(in);
    }
  }

  @XmlRootElement(name = "GetUserResponse", namespace = "https://iam.amazonaws.com/doc/2010-05-08/")
  @XmlAccessorType(XmlAccessType.FIELD)
  static class GetUserResponse {

    @XmlElement(name = "GetUserResult")
    private GetUserResult result;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(name = "GetUserResult")
  static class GetUserResult {

    @XmlElement(name = "User")
    private User user;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(name = "User")
  static class User {

    @XmlElement(name = "UserId")
    private String id;
  }
}
