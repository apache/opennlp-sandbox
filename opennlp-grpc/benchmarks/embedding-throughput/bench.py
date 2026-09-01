# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the specific
# language governing permissions and limitations under the License.

"""Fixed-duration concurrent embed throughput baseline, methodology-matched to the JVM
harness (ConcurrentEmbedBench.java): N workers round-robin over the same sentence file,
warmup seconds discarded, measure seconds counted. Prints texts/sec and RSS.

Modes:
  seq      - one worker, encode one text per call (the naive request-handler shape)
  threads  - N Python threads, encode one text per call (GIL-bound)
  procs    - N processes, each with its own model copy (how Python actually scales)
  batch    - single worker, encode the whole sentence list per call (numpy best case)
"""

import multiprocessing as mp
import sys
import threading
import time

from model2vec import StaticModel


def rss_mb(pid=None):
    path = f"/proc/{pid or 'self'}/status"
    with open(path) as f:
        for line in f:
            if line.startswith("VmRSS:"):
                return int("".join(c for c in line if c.isdigit())) / 1024.0
    return -1.0


def run_worker(model, sentences, stop_flag, counter, offset, batch):
    i = offset
    n = 0
    while not stop_flag["stop"]:
        if batch:
            model.encode(sentences)
            n += len(sentences)
        else:
            model.encode(sentences[i % len(sentences)])
            n += 1
            i += 1
    counter.append(n)


def timed_phase(model, sentences, workers, seconds, batch):
    stop_flag = {"stop": False}
    counters = []
    threads = []
    for t in range(workers):
        counter = []
        counters.append(counter)
        th = threading.Thread(
            target=run_worker, args=(model, sentences, stop_flag, counter, t, batch))
        th.start()
        threads.append(th)
    time.sleep(seconds)
    stop_flag["stop"] = True
    for th in threads:
        th.join()
    return sum(c[0] for c in counters)


def proc_worker(model_dir, sentence_file, warmup, measure, queue):
    model = StaticModel.from_pretrained(model_dir)
    sentences = load_sentences(sentence_file)
    timed_phase(model, sentences, 1, warmup, batch=False)
    count = timed_phase(model, sentences, 1, measure, batch=False)
    queue.put((count, rss_mb()))


def load_sentences(path):
    with open(path) as f:
        return [line.strip() for line in f if line.strip()]


def main():
    mode, model_dir, sentence_file, workers, warmup, measure = (
        sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]),
        int(sys.argv[6]))
    sentences = load_sentences(sentence_file)

    if mode == "procs":
        queue = mp.Queue()
        procs = [mp.Process(target=proc_worker,
                            args=(model_dir, sentence_file, warmup, measure, queue))
                 for _ in range(workers)]
        for p in procs:
            p.start()
        results = [queue.get() for _ in procs]
        for p in procs:
            p.join()
        total = sum(r[0] for r in results)
        rss = sum(r[1] for r in results)
    else:
        model = StaticModel.from_pretrained(model_dir)
        batch = mode == "batch"
        n_threads = workers if mode == "threads" else 1
        timed_phase(model, sentences, n_threads, warmup, batch)
        total = timed_phase(model, sentences, n_threads, measure, batch)
        rss = rss_mb()

    print(f"mode={mode} workers={workers} texts={total} seconds={measure} "
          f"texts_per_sec={total / measure:.1f} rss_mb={rss:.1f}")


if __name__ == "__main__":
    main()
