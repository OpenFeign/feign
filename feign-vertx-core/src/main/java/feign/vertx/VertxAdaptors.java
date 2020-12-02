package feign.vertx;

import com.github.zafarkhaja.semver.Version;
import feign.vertx.adaptor.AbstractVertxAdaptor;
import feign.vertx.adaptor.vertx35.Vertx35Adaptor;

public final class VertxAdaptors {
  private static final String VERTX_CLASSNAME = "io.vertx.core.Vertx";
  private static final AbstractVertxAdaptor ADAPTOR = fromClasspath();

  public static AbstractVertxAdaptor getAdaptor() {
    return ADAPTOR;
  }

  private static AbstractVertxAdaptor fromClasspath() {
    Class<?> vertxClass;

    try {
      vertxClass = VertxAdaptors.class.getClassLoader().loadClass(VERTX_CLASSNAME);
    } catch (ClassNotFoundException noVertxException) {
      throw new IllegalStateException(String.format("Class %s not found on classpath.", VERTX_CLASSNAME));
    }

    final String vertxVersion = vertxClass.getPackage().getImplementationVersion();
    final Version semver = Version.valueOf(vertxVersion);

    if (semver.getMajorVersion() == 3 && semver.getMinorVersion() == 5) {
      return new Vertx35Adaptor();
    } else {
      throw new IllegalStateException(String.format("Unsupported Vertx version %s.", vertxVersion));
    }
  }
}
