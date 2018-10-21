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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.OldSoftMax;
import org.nd4j.linalg.api.ops.impl.transforms.ReplaceNans;
import org.nd4j.linalg.api.ops.impl.transforms.SoftMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * A basic char/word-level stacked RNN model (2 hidden recurrent layers), based on Stacked RNN architecture from ICLR 2014's
 * "How to Construct Deep Recurrent Neural Networks" by Razvan Pascanu, Caglar Gulcehre, Kyunghyun Cho and Yoshua Bengio
 * and Andrej Karpathy's notes on RNNs.
 * See also:
 *
 * @see <a href="http://karpathy.github.io/2015/05/21/rnn-effectiveness">The Unreasonable Effectiveness of Recurrent Neural Networks</a>
 * @see <a href="https://arxiv.org/abs/1312.6026">How to Construct Deep Recurrent Neural Networks</a>
 */
public class StackedRNN extends RNN {

  // model parameters
  private final INDArray wxh; // input to hidden
  private final INDArray whh; // hidden to hidden
  private final INDArray whh2; // hidden to hidden2
  private final INDArray wh2y; // hidden2 to output
  private final INDArray wxh2;
  private final INDArray bh; // hidden bias
  private final INDArray bh2; // hidden2 bias
  private final INDArray by; // output bias

  private final double eps = 1e-8;
  private final double decay = 0.95;
  private final boolean rmsProp;

  private INDArray hPrev = null; // memory state
  private INDArray hPrev2 = null; // memory state

  public StackedRNN(float learningRate, int seqLength, int hiddenLayerSize, int epochs, String text) {
    this(learningRate, seqLength, hiddenLayerSize, epochs, text, 1, true, false);
  }

  public StackedRNN(float learningRate, int seqLength, int hiddenLayerSize, int epochs, String text, int batch, boolean useChars, boolean rmsProp) {
    super(learningRate, seqLength, hiddenLayerSize, epochs, text, batch, useChars);

    this.rmsProp = rmsProp;
    wxh = Nd4j.randn(hiddenLayerSize, vocabSize).div(Math.sqrt(hiddenLayerSize));
    whh = Nd4j.randn(hiddenLayerSize, hiddenLayerSize).div(Math.sqrt(hiddenLayerSize));
    whh2 = Nd4j.randn(hiddenLayerSize, hiddenLayerSize).div(Math.sqrt(hiddenLayerSize));
    wxh2 = Nd4j.randn(hiddenLayerSize, hiddenLayerSize).div(Math.sqrt(hiddenLayerSize));
    wh2y = Nd4j.randn(vocabSize, hiddenLayerSize).div(Math.sqrt(vocabSize));
    bh = Nd4j.zeros(hiddenLayerSize, 1);
    bh2 = Nd4j.zeros(hiddenLayerSize, 1);
    by = Nd4j.zeros(vocabSize, 1);
  }

  public void learn() {

    int currentEpoch = -1;

    int n = 0;
    int p = 0;

    // memory variables for Adagrad
    INDArray mWxh = Nd4j.zerosLike(wxh);
    INDArray mWxh2 = Nd4j.zerosLike(wxh2);
    INDArray mWhh = Nd4j.zerosLike(whh);
    INDArray mWhh2 = Nd4j.zerosLike(whh2);
    INDArray mWh2y = Nd4j.zerosLike(wh2y);

    INDArray mbh = Nd4j.zerosLike(bh);
    INDArray mbh2 = Nd4j.zerosLike(bh2);
    INDArray mby = Nd4j.zerosLike(by);

    // loss at iteration 0
    double smoothLoss = -Math.log(1.0 / vocabSize) * seqLength;

    while (true) {
      // prepare inputs (we're sweeping from left to right in steps seqLength long)
      if (p + seqLength + 1 >= data.size() || n == 0) {
        hPrev = Nd4j.zeros(hiddenLayerSize, 1); // reset RNN memory
        hPrev2 = Nd4j.zeros(hiddenLayerSize, 1); // reset RNN memory
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
        for (int i = 0; i < 3; i++) {
          String txt = sample(inputs.getInt(0));
          System.out.printf("\n---\n %s \n----\n", txt);
        }
      }

      INDArray dWxh = Nd4j.zerosLike(wxh);
      INDArray dWxh2 = Nd4j.zerosLike(wxh2);
      INDArray dWhh = Nd4j.zerosLike(whh);
      INDArray dWhh2 = Nd4j.zerosLike(whh2);
      INDArray dWh2y = Nd4j.zerosLike(wh2y);

      INDArray dbh = Nd4j.zerosLike(bh);
      INDArray dbh2 = Nd4j.zerosLike(bh);
      INDArray dby = Nd4j.zerosLike(by);

      // forward seqLength characters through the net and fetch gradient
      double loss = lossFun(inputs, targets, dWxh, dWhh, dWxh2, dWhh2, dWh2y, dbh, dbh2, dby);
      double newLoss = smoothLoss * 0.999 + loss * 0.001;

      if (newLoss > smoothLoss) {
        learningRate *= 0.999 ;
      }
      smoothLoss = newLoss;
      if (Double.isNaN(smoothLoss) || Double.isInfinite(smoothLoss)) {
        System.out.println("loss is " + smoothLoss + "(" + loss + ") (over/underflow occurred, try adjusting hyperparameters)");
        break;
      }
      if (n % 100 == 0) {
        System.out.printf("iter %d, loss: %f\n", n, smoothLoss); // print progress
      }

      if (n % batch == 0) {
        if (rmsProp) {
          // perform parameter update with RMSprop
          mWxh = mWxh.mul(decay).add(1 - decay).mul((dWxh).mul(dWxh));
          wxh.subi(dWxh.mul(learningRate).div(Transforms.sqrt(mWxh).add(eps)));

          mWxh2 = mWxh2.mul(decay).add(1 - decay).mul((dWxh2).mul(dWxh2));
          wxh2.subi(dWxh2.mul(learningRate).div(Transforms.sqrt(mWxh2).add(eps)));

          mWhh = mWhh.mul(decay).add(1 - decay).mul((dWhh).mul(dWhh));
          whh.subi(dWhh.mul(learningRate).div(Transforms.sqrt(mWhh).add(eps)));

          mWhh2 = mWhh2.mul(decay).add(1 - decay).mul((dWhh2).mul(dWhh2));
          whh2.subi(dWhh2.mul(learningRate).div(Transforms.sqrt(mWhh2).add(eps)));

          mbh2 = mbh2.mul(decay).add(1 - decay).mul((dbh2).mul(dbh2));
          bh2.subi(dbh2.mul(learningRate).div(Transforms.sqrt(mbh2).add(eps)));

          mWh2y = mWh2y.mul(decay).add(1 - decay).mul((dWh2y).mul(dWh2y));
          wh2y.subi(dWh2y.mul(learningRate).div(Transforms.sqrt(mWh2y).add(eps)));

          mbh = mbh.mul(decay).add(1 - decay).mul((dbh).mul(dbh));
          bh.subi(dbh.mul(learningRate).div(Transforms.sqrt(mbh).add(eps)));

          mby = mby.mul(decay).add(1 - decay).mul((dby).mul(dby));
          by.subi(dby.mul(learningRate).div(Transforms.sqrt(mby).add(eps)));
        } else {
          // perform parameter update with Adagrad
          mWxh.addi(dWxh.mul(dWxh));
          wxh.subi(dWxh.mul(learningRate).div(Transforms.sqrt(mWxh).add(eps)));

          mWxh2.addi(dWxh2.mul(dWxh2));
          wxh2.subi(dWxh2.mul(learningRate).div(Transforms.sqrt(mWxh2).add(eps)));

          mWhh.addi(dWhh.mul(dWhh));
          whh.subi(dWhh.mul(learningRate).div(Transforms.sqrt(mWhh).add(eps)));

          mWhh2.addi(dWhh2.mul(dWhh2));
          whh2.subi(dWhh2.mul(learningRate).div(Transforms.sqrt(mWhh2).add(eps)));

          mbh2.addi(dbh2.mul(dbh2));
          bh2.subi(dbh2.mul(learningRate).div(Transforms.sqrt(mbh2).add(eps)));

          mWh2y.addi(dWh2y.mul(dWh2y));
          wh2y.subi(dWh2y.mul(learningRate).div(Transforms.sqrt(mWh2y).add(eps)));

          mbh.addi(dbh.mul(dbh));
          bh.subi(dbh.mul(learningRate).div(Transforms.sqrt(mbh).add(eps)));

          mby.addi(dby.mul(dby));
          by.subi(dby.mul(learningRate).div(Transforms.sqrt(mby).add(eps)));
        }
      }

      p += seqLength; // move data pointer
      n++; // iteration counter
    }
  }

  /**
   * inputs, targets are both list of integers
   * hprev is Hx1 array of initial hidden state
   * returns the loss, gradients on model parameters and last hidden state
   */
  private double lossFun(INDArray inputs, INDArray targets, INDArray dWxh, INDArray dWhh, INDArray dWxh2, INDArray dWhh2, INDArray dWh2y,
                         INDArray dbh, INDArray dbh2, INDArray dby) {

    INDArray xs = Nd4j.zeros(seqLength, vocabSize);
    INDArray hs = null;
    INDArray hs2 = null;
    INDArray ys = null;
    INDArray ps = null;

    double loss = 0;

    // forward pass
    for (int t = 0; t < seqLength; t++) {
      int tIndex = inputs.getScalar(t).getInt(0);
      xs.putScalar(t, tIndex, 1); // encode in 1-of-k representation

      INDArray xst = xs.getRow(t);

      hPrev = Transforms.tanh((wxh.mmul(xst.transpose()).add(whh.mmul(hPrev)).add(bh))); // hidden state
      if (hs == null) {
        hs = init(seqLength, hPrev.shape());
      }
      hs.putRow(t, hPrev.dup());

      hPrev2 = Transforms.tanh((wxh2.mmul(hPrev).add(whh2.mmul(hPrev2)).add(bh2))); // hidden state 2
      if (hs2 == null) {
        hs2 = init(seqLength, hPrev2.shape());
      }
      hs2.putRow(t, hPrev2.dup());

      INDArray yst = wh2y.mmul(hPrev2).add(by); // unnormalized log probabilities for next chars
      if (ys == null) {
        ys = init(seqLength, yst.shape());
      }
      ys.putRow(t, yst);

      INDArray pst = Nd4j.getExecutioner().execAndReturn(new ReplaceNans(Nd4j.getExecutioner().execAndReturn(new OldSoftMax(yst)), 0d)); // probabilities for next chars
      if (ps == null) {
        ps = init(seqLength, pst.shape());
      }
      ps.putRow(t, pst);

      loss += -Math.log(pst.getDouble(targets.getInt(t), 0)); // softmax (cross-entropy loss)
    }

    // backward pass: compute gradients going backwards
    INDArray dhNext = Nd4j.zerosLike(hPrev);
    INDArray dh2Next = Nd4j.zerosLike(hPrev2);

    for (int t = seqLength - 1; t >= 0; t--) {
      INDArray dy = ps.getRow(t);
      dy.getRow(targets.getInt(t)).subi(1); // backprop into y

      INDArray hs2t = hs2.getRow(t);
      INDArray hs2tm1 = t == 0 ? hPrev2 : hs2.getRow(t - 1);

      dWh2y.addi(dy.mmul(hs2t.transpose()));
      dby.addi(dy);

      INDArray dh2 = wh2y.transpose().mmul(dy).add(dh2Next); // backprop into h2
      INDArray dhraw2 = (Nd4j.ones(hs2t.shape()).sub(hs2t.mul(hs2t))).mul(dh2); //  backprop through tanh nonlinearity
      dbh2.addi(dhraw2);
      INDArray hst = hs.getRow(t);
      dWxh2.addi(dhraw2.mmul(hst.transpose()));
      dWhh2.addi(dhraw2.mmul(hs2tm1.transpose()));
      dh2Next = whh2.transpose().mmul(dhraw2);

      INDArray dh = wxh2.transpose().mmul(dhraw2).add(dhNext); // backprop into h
      INDArray dhraw = (Nd4j.ones(hst.shape()).sub(hst.mul(hst))).mul(dh); // backprop through tanh nonlinearity
      dbh.addi(dhraw);
      dWxh.addi(dhraw.mmul(xs.getRow(t)));
      INDArray hsRow = t == 0 ? hPrev : hs.getRow(t - 1);
      dWhh.addi(dhraw.mmul(hsRow.transpose()));
      dhNext = whh.transpose().mmul(dhraw);
    }

    return loss;
  }

  /**
   * sample a sequence of integers from the model, using current (hPrev) memory state, seedIx is seed letter for first time step
   */
  @Override
  public String sample(int seedIx) {

    INDArray x = Nd4j.zeros(vocabSize, 1);
    x.putScalar(seedIx, 1);
    int sampleSize = 100;
    INDArray ixes = Nd4j.create(sampleSize);

    INDArray h = hPrev.dup();
    INDArray h2 = hPrev2.dup();

    for (int t = 0; t < sampleSize; t++) {
      h = Transforms.tanh((wxh.mmul(x)).add(whh.mmul(h)).add(bh));
      h2 = Transforms.tanh((wxh2.mmul(h)).add(whh2.mmul(h2)).add(bh2));
      INDArray y = wh2y.mmul(h2).add(by);
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

  @Override
  public void serialize(String prefix) throws IOException {
    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(new File(prefix + new Date().toString() + ".txt")));
    bufferedWriter.write("wxh");
    bufferedWriter.write(wxh.toString());
    bufferedWriter.write("whh");
    bufferedWriter.write(whh.toString());
    bufferedWriter.write("wxh2");
    bufferedWriter.write(wxh2.toString());
    bufferedWriter.write("whh2");
    bufferedWriter.write(whh2.toString());
    bufferedWriter.write("wh2y");
    bufferedWriter.write(wh2y.toString());
    bufferedWriter.write("bh");
    bufferedWriter.write(bh.toString());
    bufferedWriter.write("bh2");
    bufferedWriter.write(bh2.toString());
    bufferedWriter.write("by");
    bufferedWriter.write(by.toString());
    bufferedWriter.flush();
    bufferedWriter.close();
  }

}