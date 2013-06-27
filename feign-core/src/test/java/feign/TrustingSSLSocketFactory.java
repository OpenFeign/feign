package feign;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.inject.Provider;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static com.google.common.base.Throwables.propagate;

/**
 * used for ssl tests so that they can avoid having to read a keystore.
 */
final class TrustingSSLSocketFactory extends SSLSocketFactory implements X509TrustManager, KeyManager {

  public static SSLSocketFactory get() {
    return Singleton.INSTANCE.get();
  }

  private final SSLSocketFactory delegate;

  private TrustingSSLSocketFactory() {
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(new KeyManager[]{this}, new TrustManager[]{this}, new SecureRandom());
      this.delegate = sc.getSocketFactory();
    } catch (Exception e) {
      throw propagate(e);
    }
  }

  @Override public String[] getDefaultCipherSuites() {
    return ENABLED_CIPHER_SUITES;
  }

  @Override public String[] getSupportedCipherSuites() {
    return ENABLED_CIPHER_SUITES;
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(s, host, port, autoClose));
  }

  static Socket setEnabledCipherSuites(Socket socket) {
    SSLSocket.class.cast(socket).setEnabledCipherSuites(ENABLED_CIPHER_SUITES);
    return socket;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    return setEnabledCipherSuites(delegate.createSocket(host, port));
  }

  @Override public Socket createSocket(InetAddress host, int port) throws IOException {
    return setEnabledCipherSuites(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException,
      UnknownHostException {
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
    return;
  }

  public void checkServerTrusted(X509Certificate[] certs, String authType) {
    return;
  }

  private final static String[] ENABLED_CIPHER_SUITES = {"SSL_DH_anon_WITH_RC4_128_MD5"};

  private static enum Singleton implements Provider<SSLSocketFactory> {
    INSTANCE;

    private final SSLSocketFactory sslSocketFactory = new TrustingSSLSocketFactory();

    @Override public SSLSocketFactory get() {
      return sslSocketFactory;
    }
  }
}
