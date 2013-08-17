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
package feign;

import static com.google.common.base.Throwables.propagate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.inject.Provider;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** used for ssl tests so that they can avoid having to read a keystore. */
final class TrustingSSLSocketFactory extends SSLSocketFactory
    implements X509TrustManager, KeyManager {

  public static SSLSocketFactory get() {
    return Singleton.INSTANCE.get();
  }

  private final SSLSocketFactory delegate;

  private TrustingSSLSocketFactory() {
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(new KeyManager[] {this}, new TrustManager[] {this}, new SecureRandom());
      this.delegate = sc.getSocketFactory();
    } catch (Exception e) {
      throw propagate(e);
    }
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return ENABLED_CIPHER_SUITES;
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return ENABLED_CIPHER_SUITES;
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
      throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(s, host, port, autoClose));
  }

  static Socket setEnabledCipherSuites(Socket socket) {
    SSLSocket.class.cast(socket).setEnabledCipherSuites(ENABLED_CIPHER_SUITES);
    return socket;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(host, port, localHost, localPort));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(address, port, localAddress, localPort));
  }

  public X509Certificate[] getAcceptedIssuers() {
    return null;
  }

  public void checkClientTrusted(X509Certificate[] certs, String authType) {}

  public void checkServerTrusted(X509Certificate[] certs, String authType) {}

  private static final String[] ENABLED_CIPHER_SUITES = {"SSL_DH_anon_WITH_RC4_128_MD5"};

  private static enum Singleton implements Provider<SSLSocketFactory> {
    INSTANCE;

    private final SSLSocketFactory sslSocketFactory = new TrustingSSLSocketFactory();

    @Override
    public SSLSocketFactory get() {
      return sslSocketFactory;
    }
  }
}
