/*
 * Copyright 2013 Netflix, Inc.
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
package feign.ribbon;

/*
 * Copyright 2013 Netflix, Inc.
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

import com.google.common.base.Throwables;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.niws.client.http.RestClient;
import com.sun.jersey.api.client.ClientResponse;
import dagger.Provides;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.http.protocol.HTTP;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

/**
 * Adding this module will override URL resolution of {@link feign.Client Feign's client},
 * adding smart routing and resiliency capabilities provided by Ribbon.
 * <br>
 * When using this, ensure the {@link feign.Target#url()} is set to as {@code http://clientName}
 * or {@code https://clientName}. {@link com.netflix.client.config.IClientConfig#getClientName() clientName}
 * will lookup the real url and port of your service dynamically.
 * <br>
 * Ex.
 * <pre>
 * MyService api = Feign.create(MyService.class, "http://myAppProd", new RibbonModule());
 * </pre>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers} configuration
 * is set.
 */
@dagger.Module(overrides = true, library = true, complete = false)
public class RibbonModule {

    @Provides @Singleton
    Client httpClient(RibbonClient ribbon) {
        return ribbon;
    }

    @Singleton
    static class RibbonClient implements Client {

        @Inject
        public RibbonClient() {

        }

        @Override public Response execute(Request request, Request.Options options) throws IOException {
            try {
                URI asUri = URI.create(request.url());
                String clientName = asUri.getHost();
                RestClient client = (RestClient)ClientFactory.getNamedClient( clientName);
                HttpRequest httpRequest = createHttpRequest( asUri, request);
                HttpResponse httpResponse = client.executeWithLoadBalancer( httpRequest);
                return createResponse( httpResponse);
            } catch (ClientException e) {
                if (e.getCause() instanceof IOException) {
                    throw IOException.class.cast(e.getCause());
                }
                throw Throwables.propagate(e);
            }
        }

        private Response createResponse(HttpResponse response) throws ClientException {
            int status = response.getStatus();
            ClientResponse.Status oStatus = ClientResponse.Status.fromStatusCode( status);
            String reason = null;
            if( oStatus != null){
               reason = oStatus.toString();
            }
            return Response.create( status, reason, response.getHeaders(), response.getInputStream(), null);
        }

        private HttpRequest createHttpRequest(URI uri, Request request){
            String uriWithoutSchemeAndPort = uri.getPath();
            if( uri.getQuery() != null){
                uriWithoutSchemeAndPort += "?" + uri.getQuery();
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uriWithoutSchemeAndPort);
            builder.verb(HttpRequest.Verb.valueOf( request.method()));
            setHeaders( builder, request);
            builder.entity( request.body());
            return builder.build();
        }

        private void setHeaders(HttpRequest.Builder builder, Request request) {
            Map<String, Collection<String>> headerMap = request.headers();
            for(String headerName : headerMap.keySet()){
                if(!HTTP.CONTENT_LEN.equals( headerName) && !HTTP.TRANSFER_ENCODING.equals( headerName)) {
                    Collection<String> headerValues = headerMap.get( headerName);
                    if( headerValues != null){
                        StringBuilder headerValueBuilder = new StringBuilder();
                        for (String headerValue : headerValues) {
                            if (headerValueBuilder.length() != 0) {
                                headerValueBuilder.append(",");
                            }
                            headerValueBuilder.append(headerValue);
                        }
                        builder.header( headerName, headerValueBuilder.toString());
                    }
                }
            }
        }
    }
}
