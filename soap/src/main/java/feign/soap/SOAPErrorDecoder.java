/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.soap;

import java.io.IOException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.soap.SOAPFaultException;
import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * Wraps the returned {@link SOAPFault} if present into a {@link SOAPFaultException}. So you need to
 * catch {@link SOAPFaultException} to retrieve the reason of the {@link SOAPFault}.
 * 
 * <p>
 * If no faults is returned then the default {@link ErrorDecoder} is used to return exception and
 * eventually retry the call.
 * </p>
 *
 */
public class SOAPErrorDecoder implements ErrorDecoder {

  private final String soapProtocol;

  public SOAPErrorDecoder() {
    this.soapProtocol = SOAPConstants.DEFAULT_SOAP_PROTOCOL;
  }

  /**
   * SOAPErrorDecoder constructor allowing you to specify the SOAP protocol.
   * 
   * @param soapProtocol a string constant representing the MessageFactory protocol.
   * 
   * @see SOAPConstants#SOAP_1_1_PROTOCOL
   * @see SOAPConstants#SOAP_1_2_PROTOCOL
   * @see SOAPConstants#DYNAMIC_SOAP_PROTOCOL
   * @see MessageFactory#newInstance(String)
   */
  public SOAPErrorDecoder(String soapProtocol) {
    this.soapProtocol = soapProtocol;
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    if (response.body() == null || response.status() == 503)
      return defaultErrorDecoder(methodKey, response);

    SOAPMessage message;
    try {
      message = MessageFactory.newInstance(soapProtocol).createMessage(null,
          response.body().asInputStream());
      if (message.getSOAPBody() != null && message.getSOAPBody().hasFault()) {
        return new SOAPFaultException(message.getSOAPBody().getFault());
      }
    } catch (SOAPException | IOException e) {
      // ignored
    }
    return defaultErrorDecoder(methodKey, response);
  }

  private Exception defaultErrorDecoder(String methodKey, Response response) {
    return new ErrorDecoder.Default().decode(methodKey, response);
  }

}
