/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.dl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.api.iter.NdIndexIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.OldSoftMax;
import org.nd4j.linalg.api.ops.impl.transforms.SetRange;
import org.nd4j.linalg.api.ops.impl.transforms.SoftMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * A min char/word-level vanilla RNN model, based on Andrej Karpathy's python code.
 * See also:
 *
 * @see <a href="http://karpathy.github.io/2015/05/21/rnn-effectiveness">The Unreasonable Effectiveness of Recurrent Neural Networks</a>
 * @see <a href="https://gist.github.com/karpathy/d4dee566867f8291f086">Minimal character-level language model with a Vanilla Recurrent Neural Network, in Python/numpy</a>
 */
public class RNN {

  // hyperparameters
  protected float learningRate;
  protected final int seqLength; // no. of steps to unroll the RNN for
  protected final int hiddenLayerSize;
  protected final int epochs;
  protected final boolean useChars;
  protected final int batch;
  protected final int vocabSize;
  protected final Map<String, Integer> charToIx;
  protected final Map<Integer, String> ixToChar;
  protected final List<String> data;
  private final static double eps = 1e-8;
  private final static double decay = 0.9;

  // model parameters
  private final INDArray wxh; // input to hidden
  private final INDArray whh; // hidden to hidden
  private final INDArray why; // hidden to output
  private final INDArray bh; // hidden bias
  private final INDArray by; // output bias

  private INDArray hPrev = null; // memory state

  public RNN(float learningRate, int seqLength, int hiddenLayerSize, int epochs, String text) {
    this(learningRate, seqLength, hiddenLayerSize, epochs, text, 1, true);
  }

  public RNN(float learningRate, int seqLength, int hiddenLayerSize, int epochs, String text, int batch, boolean useChars) {
    this.learningRate = learningRate;
    this.seqLength = seqLength;
    this.hiddenLayerSize = hiddenLayerSize;
    this.epochs = epochs;
    this.batch = batch;
    this.useChars = useChars;

    String[] textTokens = useChars ? toStrings(text.toCharArray()) : text.split(" ");
    data = new LinkedList<>();
    Collections.addAll(data, textTokens);
    Set<String> tokens = new HashSet<>(data);
    vocabSize = tokens.size();

    System.out.printf("data has %d tokens, %d unique.\n", data.size(), vocabSize);
    charToIx = new HashMap<>();
    ixToChar = new HashMap<>();
    int i = 0;
    for (String c : tokens) {
      charToIx.put(c, i);
      ixToChar.put(i, c);
      i++;
    }

    wxh = Nd4j.randn(hiddenLayerSize, vocabSize).mul(0.01);
    whh = Nd4j.randn(hiddenLayerSize, hiddenLayerSize).mul(0.01);
    why = Nd4j.randn(vocabSize, hiddenLayerSize).mul(0.01);
    bh = Nd4j.zeros(hiddenLayerSize, 1);
    by = Nd4j.zeros(vocabSize, 1);
  }

  private String[] toStrings(char[] chars) {
    String[] strings = new String[chars.length];
    for (int i = 0; i < chars.length; i++) {
      strings[i] = String.valueOf(chars[i]);
    }
    return strings;
  }

  public void learn() {

    int currentEpoch = 0;

    int n = 0;
    int p = 0;

    // memory variables for Adagrad
    INDArray mWxh = Nd4j.zerosLike(wxh);
    INDArray mWhh = Nd4j.zerosLike(whh);
    INDArray mWhy = Nd4j.zerosLike(why);

    INDArray mbh = Nd4j.zerosLike(bh);
    INDArray mby = Nd4j.zerosLike(by);

    // loss at iteration 0
    double smoothLoss = -Math.log(1.0 / vocabSize) * seqLength;

    while (true) {
      // prepare inputs (we're sweeping from left to right in steps seqLength long)
      if (p + seqLength + 1 >= data.size() || n == 0) {
        hPrev = Nd4j.zeros(hiddenLayerSize, 1); // reset RNN memory
        p = 0; // go from start of data
        currentEpoch++;
        if (currentEpoch == epochs) {
          System.out.println("training finished: e:" + epochs + ", l: " + smoothLoss + ", h:(" + learningRate + ", " + seqLength + ", " + hiddenLayerSize + ")");
          break;
        }
      }

      INDArray inputs = getSequence(p);
      INDArray targets = getSequence(p + 1);

      // sample from the model every now and then
      if (n % 1000 == 0 && n > 0) {
        String txt = sample(inputs.getInt(0));
        System.out.printf("\n---\n %s \n----\n", txt);
      }

      INDArray dWxh = Nd4j.zerosLike(wxh);
      INDArray dWhh = Nd4j.zerosLike(whh);
      INDArray dWhy = Nd4j.zerosLike(why);

      INDArray dbh = Nd4j.zerosLike(bh);
      INDArray dby = Nd4j.zerosLike(by);

      // forward seqLength characters through the net and fetch gradient
      double loss = lossFun(inputs, targets, dWxh, dWhh, dWhy, dbh, dby);
      smoothLoss = smoothLoss * 0.999 + loss * 0.001;
      if (Double.isNaN(smoothLoss)) {
        System.out.println("loss is NaN (over/underflow occured, try adjusting hyperparameters)");
        break;
      }
      if (n % 100 == 0) {
        System.out.printf("iter %d, loss: %f\n", n, smoothLoss); // print progress
      }

      if (n % batch == 0) {

        // perform parameter update with RMSprop
        mWxh = mWxh.mul(decay).add(1 - decay).mul((dWxh).mul(dWxh));
        wxh.subi(dWxh.mul(learningRate).div(Transforms.sqrt(mWxh).add(eps)));

        mWhh = mWhh.mul(decay).add(1 - decay).mul((dWhh).mul(dWhh));
        whh.subi(dWhh.mul(learningRate).div(Transforms.sqrt(mWhh).add(eps)));

        mWhy = mWhy.mul(decay).add(1 - decay).mul((dWhy).mul(dWhy));
        why.subi(dWhy.mul(learningRate).div(Transforms.sqrt(mWhy).add(eps)));

        mbh = mbh.mul(decay).add(1 - decay).mul((dbh).mul(dbh));
        bh.subi(dbh.mul(learningRate).div(Transforms.sqrt(mbh).add(eps)));

        mby = mby.mul(decay).add(1 - decay).mul((dby).mul(dby));
        by.subi(dby.mul(learningRate).div(Transforms.sqrt(mby).add(eps)));
      }

      p += seqLength; // move data pointer
      n++; // iteration counter
    }
  }

  protected INDArray getSequence(int p) {
    INDArray inputs = Nd4j.create(seqLength);
    int c = 0;
    for (String ch : data.subList(p, p + seqLength)) {
      Integer ix = charToIx.get(ch);
      inputs.putScalar(c, ix);
      c++;
    }
    return inputs;
  }

  /**
   * inputs, targets are both list of integers
   * hprev is Hx1 array of initial hidden state
   * returns the modified loss, gradients on model parameters
   */
  private double lossFun(INDArray inputs, INDArray targets, INDArray dWxh, INDArray dWhh, INDArray dWhy, INDArray dbh,
                         INDArray dby) {

    INDArray xs = Nd4j.zeros(inputs.length(), vocabSize);
    INDArray hs = null;
    INDArray ys = null;
    INDArray ps = null;

    INDArray hs1 = Nd4j.create(hPrev.shape());
    Nd4j.copy(hPrev, hs1);

    double loss = 0;

    // forward pass
    for (int t = 0; t < inputs.length(); t++) {
      int tIndex = inputs.getScalar(t).getInt(0);
      xs.putScalar(t, tIndex, 1); // encode in 1-of-k representation
      INDArray hsRow = t == 0 ? hs1 : hs.getRow(t - 1);
      INDArray hst = Transforms.tanh(wxh.mmul(xs.getRow(t).transpose()).add(whh.mmul(hsRow)).add(bh)); // hidden state
      if (hs == null) {
        hs = init(inputs.length(), hst.shape());
      }
      hs.putRow(t, hst);

      INDArray yst = (why.mmul(hst)).add(by); // unnormalized log probabilities for next chars
      if (ys == null) {
        ys = init(inputs.length(), yst.shape());
      }
      ys.putRow(t, yst);
      INDArray pst = Nd4j.getExecutioner().execAndReturn(new OldSoftMax(yst)); // probabilities for next chars
      if (ps == null) {
        ps = init(inputs.length(), pst.shape());
      }
      ps.putRow(t, pst);
      loss += -Math.log(pst.getDouble(targets.getInt(t),0)); // softmax (cross-entropy loss)
    }

    // backward pass: compute gradients going backwards
    INDArray dhNext = Nd4j.zerosLike(hPrev);
    for (int t = (int) (inputs.length() - 1); t >= 0; t--) {
      INDArray dy = ps.getRow(t);
      dy.putRow(targets.getInt(t), dy.getRow(targets.getInt(t)).sub(1)); // backprop into y
      INDArray hst = hs.getRow(t);
      dWhy.addi(dy.mmul(hst.transpose())); // derivative of hy layer
      dby.addi(dy);
      INDArray dh = why.transpose().mmul(dy).add(dhNext); // backprop into h
      INDArray dhraw = (Nd4j.ones(hst.shape()).sub(hst.mul(hst))).mul(dh); // backprop through tanh nonlinearity
      dbh.addi(dhraw);
      dWxh.addi(dhraw.mmul(xs.getRow(t)));
      INDArray hsRow = t == 0 ? hs1 : hs.getRow(t - 1);
      dWhh.addi(dhraw.mmul(hsRow.transpose()));
      dhNext = whh.transpose().mmul(dhraw);
    }

    this.hPrev = hs.getRow(inputs.length() - 1);

    return loss;
  }

  protected INDArray init(long t, long[] aShape) {
    INDArray as;
    long[] shape = new long[1 + aShape.length];
    shape[0] = t;
    System.arraycopy(aShape, 0, shape, 1, aShape.length);
    as = Nd4j.create(shape);
    return as;
  }

  /**
   * sample a sequence of integers from the model, using current (hPrev) memory state, seedIx is seed letter for first time step
   */
  public String sample(int seedIx) {

    INDArray x = Nd4j.zeros(vocabSize, 1);
    x.putScalar(seedIx, 1);
    int sampleSize = 144;
    INDArray ixes = Nd4j.create(sampleSize);

    INDArray h = hPrev.dup();

    for (int t = 0; t < sampleSize; t++) {
      h = Transforms.tanh(wxh.mmul(x).add(whh.mmul(h)).add(bh));
      INDArray y = (why.mmul(h)).add(by);
      INDArray pm = Nd4j.getExecutioner().execAndReturn(new OldSoftMax(y)).ravel();

      List<Pair<Integer, Double>> d = new LinkedList<>();
      for (int pi = 0; pi < vocabSize; pi++) {
        d.add(new Pair<>(pi, pm.getDouble(0, pi)));
      }
      try {
        EnumeratedDistribution<Integer> distribution = new EnumeratedDistribution<>(d);

        int ix = distribution.sample();

        x = Nd4j.zeros(vocabSize, 1);
        x.putScalar(ix, 1);
        ixes.putScalar(t, ix);
      } catch (Exception e) {
      }
    }

    return getSampleString(ixes);
  }

  protected String getSampleString(INDArray ixes) {
    StringBuilder txt = new StringBuilder();

    NdIndexIterator ndIndexIterator = new NdIndexIterator(ixes.shape());
    while (ndIndexIterator.hasNext()) {
      long[] next = ndIndexIterator.next();
      if (!useChars && txt.length() > 0) {
        txt.append(' ');
      }
      int aDouble = (int) ixes.getDouble(next);
      txt.append(ixToChar.get(aDouble));
    }
    return txt.toString();
  }

  public int getVocabSize() {
    return vocabSize;
  }

  @Override
  public String toString() {
    return getClass().getName() + "{" +
        "learningRate=" + learningRate +
        ", seqLength=" + seqLength +
        ", hiddenLayerSize=" + hiddenLayerSize +
        ", epochs=" + epochs +
        ", vocabSize=" + vocabSize +
        ", useChars=" + useChars +
        ", batch=" + batch +
        '}';
  }

  public void serialize(String prefix) throws IOException {
    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(new File(prefix + new Date().toString() + ".txt")));
    bufferedWriter.write("wxh");
    bufferedWriter.write(wxh.toString());
    bufferedWriter.write("whh");
    bufferedWriter.write(whh.toString());
    bufferedWriter.write("why");
    bufferedWriter.write(why.toString());
    bufferedWriter.write("bh");
    bufferedWriter.write(bh.toString());
    bufferedWriter.write("by");
    bufferedWriter.write(by.toString());
    bufferedWriter.flush();
    bufferedWriter.close();
  }
}
