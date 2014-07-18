/*
 * Copyright 2014 Netflix, Inc.
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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

public class IAMExample {

    interface IAM {
        @RequestLine("GET /?Action=GetUser&Version=2010-05-08") GetUserResponse userResponse();
    }

    public static void main(String... args) {
        IAM iam = Feign.builder()
                .decoder(new JAXBDecoder(new JAXBContextFactory.Builder().build()))
                .target(new IAMTarget(args[0], args[1]));

        GetUserResponse response = iam.userResponse();
        System.out.println("UserId: " + response.getUserResult().getUser().getUserId());
        System.out.println("UserName: " + response.getUserResult().getUser().getUsername());
    }

    static class IAMTarget extends AWSSignatureVersion4 implements Target<IAM> {

        @Override public Class<IAM> type() {
            return IAM.class;
        }

        @Override public String name() {
            return "iam";
        }

        @Override public String url() {
            return "https://iam.amazonaws.com";
        }

        private IAMTarget(String accessKey, String secretKey) {
            super(accessKey, secretKey);
        }

        @Override public Request apply(RequestTemplate in) {
            in.insert(0, url());
            return super.apply(in);
        }
    }

    @XmlRootElement(name = "GetUserResponse", namespace = "https://iam.amazonaws.com/doc/2010-05-08/")
    @XmlAccessorType(XmlAccessType.FIELD)
    static class GetUserResponse {
        @XmlElement(name = "GetUserResult")
        private GetUserResult userResult;

        @XmlElement(name = "ResponseMetadata")
        private ResponseMetadata responseMetadata;

        public GetUserResult getUserResult() {
            return userResult;
        }

        public void setUserResult(GetUserResult userResult) {
            this.userResult = userResult;
        }

        public ResponseMetadata getResponseMetadata() {
            return responseMetadata;
        }

        public void setResponseMetadata(ResponseMetadata responseMetadata) {
            this.responseMetadata = responseMetadata;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "ResponseMetadata")
    static class ResponseMetadata {
        @XmlElement(name = "RequestId")
        private String requestId;

        public ResponseMetadata() {}

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "GetUserResult")
    static class GetUserResult {
        @XmlElement(name = "User")
        private User user;

        public GetUserResult() {}

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "User")
    static class User {
        @XmlElement(name = "UserId")
        private String userId;

        @XmlElement(name = "Path")
        private String path;

        @XmlElement(name = "UserName")
        private String username;

        @XmlElement(name = "Arn")
        private String arn;

        @XmlElement(name = "CreateDate")
        private String createDate;

        public User() {}

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getArn() {
            return arn;
        }

        public void setArn(String arn) {
            this.arn = arn;
        }

        public String getCreateDate() {
            return createDate;
        }

        public void setCreateDate(String createDate) {
            this.createDate = createDate;
        }
    }
}
