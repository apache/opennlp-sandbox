import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import opennlp.embeddings.StaticEmbeddingModel;

/**
 * Fixed-duration concurrent embed throughput harness, methodology-matched to the Python
 * baseline script (bench.py): N threads round-robin over the same sentence file, warmup
 * seconds discarded, measure seconds counted. Prints texts/sec and RSS.
 */
public final class ConcurrentEmbedBench {

  public static void main(String[] args) throws Exception {
    final Path modelDir = Path.of(args[0]);
    final Path sentenceFile = Path.of(args[1]);
    final int threads = Integer.parseInt(args[2]);
    final int warmupSec = Integer.parseInt(args[3]);
    final int measureSec = Integer.parseInt(args[4]);

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(modelDir);
    final List<String> lines = Files.readAllLines(sentenceFile);
    final String[] sentences = lines.toArray(new String[0]);

    // Warmup phase (JIT compilation, caches), counts discarded.
    runPhase(model, sentences, threads, warmupSec);
    // Measured phase.
    final long count = runPhase(model, sentences, threads, measureSec);

    final double perSec = count / (double) measureSec;
    System.out.printf("threads=%d texts=%d seconds=%d texts_per_sec=%.1f rss_mb=%.1f%n",
        threads, count, measureSec, perSec, rssMb());
  }

  private static long runPhase(StaticEmbeddingModel model, String[] sentences, int threads,
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
        ready.countDown();
        try {
          go.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        long n = 0;
        double acc = 0;
        int i = id;  // offset start per thread so threads do not stride in lockstep
        while (!stop.get()) {
          final float[] v = model.embed(sentences[i % sentences.length]);
          acc += v[0];  // prevents dead-code elimination of the embed call
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

  private static double rssMb() throws Exception {
    for (final String line : Files.readAllLines(Path.of("/proc/self/status"))) {
      if (line.startsWith("VmRSS:")) {
        return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024.0;
      }
    }
    return -1;
  }
}
