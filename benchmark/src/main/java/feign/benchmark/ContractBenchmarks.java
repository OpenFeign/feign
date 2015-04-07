package feign.benchmark;

import feign.Contract;
import feign.jaxrs.JAXRSContract;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class ContractBenchmarks {

  private Contract feignContract;
  private Contract jaxrsContract;

  @Setup
  public void setup() {
    feignContract = new Contract.Default();
    jaxrsContract = new JAXRSContract();
  }

  @Benchmark
  public void parseFeign() {
    feignContract.parseAndValidatateMetadata(FeignTestInterface.class);
  }

  @Benchmark
  public void parseJAXRS() {
    jaxrsContract.parseAndValidatateMetadata(JAXRSTestInterface.class);
  }
}
