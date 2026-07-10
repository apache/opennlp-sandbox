import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;

/**
 * Fixed-duration concurrent embed throughput harness over the gRPC hop, methodology-matched
 * to the in-process harness (ConcurrentEmbedBench.java) and the Python baseline (bench.py).
 * The server end is whatever backend the server was configured with, which is the point:
 * the same harness measures any backend behind the same wire.
 *
 * <p>Two modes. {@code unary} (the default) sends one blocking AnalyzeDocument request per
 * text with an EMBED profile: N threads, each with exactly one call in flight, the classic
 * request-handler shape. {@code stream} opens one EmbedText bidi stream per worker and pumps
 * texts as fast as gRPC flow control admits them, so many messages are in flight per stream
 * and per-text round-trip waiting disappears; counting is windowed on the live streams
 * (first {@code warmup} seconds discarded, next {@code measure} seconds counted).</p>
 */
public final class GrpcEmbedBench {

  public static void main(String[] args) throws Exception {
    final String host = args[0];
    final int port = Integer.parseInt(args[1]);
    final Path sentenceFile = Path.of(args[2]);
    final int threads = Integer.parseInt(args[3]);
    final int warmupSec = Integer.parseInt(args[4]);
    final int measureSec = Integer.parseInt(args[5]);
    // One channel is the standard client shape; more channels (= more HTTP/2 connections)
    // shows whether a measured ceiling is the connection or the server.
    final int channelCount = args.length > 6 ? Integer.parseInt(args[6]) : 1;
    final String mode = args.length > 7 ? args[7] : "unary";

    final List<String> lines = Files.readAllLines(sentenceFile);
    final String[] sentences = lines.toArray(new String[0]);

    final ManagedChannel[] channels = new ManagedChannel[channelCount];
    for (int c = 0; c < channelCount; c++) {
      channels[c] = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }
    try {
      final long count;
      if (mode.equals("stream")) {
        count = runStreamWindow(channels, sentences, threads, warmupSec, measureSec);
      } else if (mode.equals("unary")) {
        runPhase(channels, sentences, threads, warmupSec);
        count = runPhase(channels, sentences, threads, measureSec);
      } else {
        throw new IllegalArgumentException("mode must be 'unary' or 'stream': " + mode);
      }
      final double perSec = count / (double) measureSec;
      System.out.printf("mode=%s threads=%d channels=%d texts=%d seconds=%d texts_per_sec=%.1f%n",
          mode, threads, channelCount, count, measureSec, perSec);
    } finally {
      for (ManagedChannel channel : channels) {
        channel.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }

  // One continuous EmbedText stream per worker; the send loop runs inside gRPC's
  // on-ready handler, so flow control paces it and the count of received vectors is
  // read at the window edges without disturbing the streams.
  private static long runStreamWindow(ManagedChannel[] channels, String[] sentences,
                                      int workers, int warmupSec, int measureSec)
      throws InterruptedException {
    final AtomicLong[] received = new AtomicLong[workers];
    @SuppressWarnings("unchecked")
    final ClientCallStreamObserver<EmbedTextRequest>[] calls =
        new ClientCallStreamObserver[workers];
    final CountDownLatch terminated = new CountDownLatch(workers);
    for (int w = 0; w < workers; w++) {
      final int id = w;
      received[id] = new AtomicLong();
      final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceStub stub =
          OpenNlpAnalysisServiceGrpc.newStub(channels[id % channels.length]);
      stub.embedText(new ClientResponseObserver<EmbedTextRequest, EmbedTextResponse>() {
        private long next = id;

        @Override
        public void beforeStart(ClientCallStreamObserver<EmbedTextRequest> requestStream) {
          calls[id] = requestStream;
          requestStream.setOnReadyHandler(() -> {
            while (requestStream.isReady()) {
              requestStream.onNext(EmbedTextRequest.newBuilder()
                  .setSequence(next)
                  .setText(sentences[(int) (next % sentences.length)])
                  .build());
              next++;
            }
          });
        }

        @Override
        public void onNext(EmbedTextResponse value) {
          received[id].incrementAndGet();
        }

        @Override
        public void onError(Throwable t) {
          terminated.countDown();
        }

        @Override
        public void onCompleted() {
          terminated.countDown();
        }
      });
    }
    Thread.sleep(warmupSec * 1000L);
    final long atWindowStart = sum(received);
    Thread.sleep(measureSec * 1000L);
    final long atWindowEnd = sum(received);
    for (ClientCallStreamObserver<EmbedTextRequest> call : calls) {
      if (call != null) {
        call.cancel("benchmark window complete", null);
      }
    }
    terminated.await(5, TimeUnit.SECONDS);
    return atWindowEnd - atWindowStart;
  }

  private static long sum(AtomicLong[] counters) {
    long total = 0;
    for (AtomicLong counter : counters) {
      total += counter.get();
    }
    return total;
  }

  private static long runPhase(ManagedChannel[] channels, String[] sentences, int threads,
                               int seconds) throws InterruptedException {
    final AtomicBoolean stop = new AtomicBoolean(false);
    final long[] counts = new long[threads];
    final double[] sink = new double[threads];
    final CountDownLatch ready = new CountDownLatch(threads);
    final CountDownLatch go = new CountDownLatch(1);
    final Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      final int id = t;
      workers[t] = new Thread(() -> {
        final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub stub =
            OpenNlpAnalysisServiceGrpc.newBlockingStub(channels[id % channels.length]);
        ready.countDown();
        try {
          go.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        long n = 0;
        double acc = 0;
        int i = id;
        while (!stop.get()) {
          final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
              .setDocument(OpenNlpDocument.newBuilder()
                  .setRawText(sentences[i % sentences.length]))
              .setProfile(AnalysisProfile.newBuilder()
                  .addSteps(PipelineStep.PIPELINE_STEP_EMBED))
              .build();
          final AnalyzeDocumentResponse response = stub.analyzeDocument(request);
          acc += response.getDocument().getEmbeddings(0).getVector(0);
          i++;
          n++;
        }
        counts[id] = n;
        sink[id] = acc;
      });
      workers[t].start();
    }
    ready.await();
    go.countDown();
    Thread.sleep(seconds * 1000L);
    stop.set(true);
    long total = 0;
    double sinkTotal = 0;
    for (int t = 0; t < threads; t++) {
      workers[t].join();
      total += counts[t];
      sinkTotal += sink[t];
    }
    if (Double.isNaN(sinkTotal)) {
      System.err.println("sink NaN (should not happen)");
    }
    return total;
  }
}
