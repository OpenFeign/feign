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
package feign.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Used for ssl tests to simplify setup.
 */
public final class TrustingSSLSocketFactory extends SSLSocketFactory
    implements X509TrustManager, X509KeyManager {

  private static final Map<String, SSLSocketFactory>
      sslSocketFactories =
      new LinkedHashMap<String, SSLSocketFactory>();
  private static final char[] KEYSTORE_PASSWORD = "password".toCharArray();
  private final static String[] ENABLED_CIPHER_SUITES = {"SSL_RSA_WITH_3DES_EDE_CBC_SHA"};
  private final SSLSocketFactory delegate;
  private final String serverAlias;
  private final PrivateKey privateKey;
  private final X509Certificate[] certificateChain;
  private TrustingSSLSocketFactory(String serverAlias) {
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(new KeyManager[]{this}, new TrustManager[]{this}, new SecureRandom());
      this.delegate = sc.getSocketFactory();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.serverAlias = serverAlias;
    if (serverAlias.isEmpty()) {
      this.privateKey = null;
      this.certificateChain = null;
    } else {
      try {
        KeyStore
            keyStore =
            loadKeyStore(TrustingSSLSocketFactory.class.getResourceAsStream("/keystore.jks"));
        this.privateKey = (PrivateKey) keyStore.getKey(serverAlias, KEYSTORE_PASSWORD);
        Certificate[] rawChain = keyStore.getCertificateChain(serverAlias);
        this.certificateChain = Arrays.copyOf(rawChain, rawChain.length, X509Certificate[].class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static SSLSocketFactory get() {
    return get("");
  }

  public synchronized static SSLSocketFactory get(String serverAlias) {
    if (!sslSocketFactories.containsKey(serverAlias)) {
      sslSocketFactories.put(serverAlias, new TrustingSSLSocketFactory(serverAlias));
    }
    return sslSocketFactories.get(serverAlias);
  }

  static Socket setEnabledCipherSuites(Socket socket) {
    SSLSocket.class.cast(socket).setEnabledCipherSuites(ENABLED_CIPHER_SUITES);
    return socket;
  }

  private static KeyStore loadKeyStore(InputStream inputStream) throws IOException {
    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      keyStore.load(inputStream, KEYSTORE_PASSWORD);
      return keyStore;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      inputStream.close();
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

  public void checkClientTrusted(X509Certificate[] certs, String authType) {
  }

  public void checkServerTrusted(X509Certificate[] certs, String authType) {
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return null;
  }

  @Override
  public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
    return null;
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers) {
    return null;
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
    return serverAlias;
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias) {
    return certificateChain;
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return privateKey;
  }
}
