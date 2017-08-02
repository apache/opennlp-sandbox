import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets

import main.GloveUtil
import ml.dmlc.mxnet.{Callback, Context, CustomMetric, NDArray, Symbol, Xavier}
import ml.dmlc.mxnet.optimizer.Adam
import opennlp.tools.namefind.{NameSample, NameSampleDataStream, TokenNameFinderEvaluator}
import opennlp.tools.util.{MarkableFileInputStreamFactory, ObjectStream, PlainTextByLineStream}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object NameFinderDLTrainer {

  def main(args: Array[String]): Unit = {

    if (args.length != 3) {
      println("Usage: gloveFile trainingFile evaluationFile")
      return
    }

    val wordVectors = GloveUtil.loadGloveVectors(new FileInputStream(args(0))).toMap

    val nameSamples: ObjectStream[NameSample] = new NameSampleDataStream(new PlainTextByLineStream(
      new MarkableFileInputStreamFactory(new File(args(1))), StandardCharsets.UTF_8))

    // The batch size for training
    val batchSize = 64
    // We can support various length input
    // For this problem, we cut each input sentence to length of 129
    // So we only need fix length bucket
    val buckets = Array(50)
    // hidden unit in LSTM cell
    val numHidden = 256

    // number of lstm layer
    val numLstmLayer = 2
    // we will show a quick demo in 2 epoch
    // and we will see result by training 75 epoch
    val numEpoch = 2
    // learning rate
    val learningRate = 0.001f
    // we will use pure sgd without momentum
    val momentum = 0.0f

    val ctx = Context.gpu(0)

    try {
      // generate symbol for a length
      // Number of outcomes are hard coded to three, otherwise it should be
      // the number of actual outcomes (depends on the used codec)
      def symGen(seqLen: Int): Symbol = {
        Lstm.lstmUnroll(numLstmLayer, seqLen, //256, // <- number of hidden layers
          numHidden = numHidden,
          numLabel = 3, dropout = 0.2f)
      }

      // initalize states for LSTM
      val initC = for (l <- 0 until numLstmLayer) yield (s"l${l}_init_c", (batchSize, numHidden))
      val initH = for (l <- 0 until numLstmLayer) yield (s"l${l}_init_h", (batchSize, numHidden))
      val initStates = initC ++ initH

      val dataTrain = new BucketIo.BucketNameSampleIter(nameSamples, wordVectors, buckets,
        batchSize, initStates)

      // the network symbol
      val symbol = symGen(buckets(0))

      val datasAndLabels = dataTrain.provideData ++ dataTrain.provideLabel
      val (argShapes, outputShapes, auxShapes) = symbol.inferShape(datasAndLabels)

      val initializer = new Xavier(factorType = "in", magnitude = 2.34f)

      val argNames = symbol.listArguments()
      val argDict = argNames.zip(argShapes.map(NDArray.zeros(_, ctx))).toMap
      val auxNames = symbol.listAuxiliaryStates()
      val auxDict = auxNames.zip(auxShapes.map(NDArray.zeros(_, ctx))).toMap

      val gradDict = argNames.zip(argShapes).filter { case (name, shape) =>
        !datasAndLabels.contains(name)
      }.map(x => x._1 -> NDArray.empty(x._2, ctx) ).toMap

      argDict.foreach { case (name, ndArray) =>
        if (!datasAndLabels.contains(name)) {
          initializer.initWeight(name, ndArray)
        }
      }

      val data = argDict("data")
      val label = argDict("softmax_label")

      val executor = symbol.bind(ctx, argDict, gradDict)

      val opt = new Adam(learningRate = learningRate, wd = 0.0001f)

      val paramsGrads = gradDict.toList.zipWithIndex.map { case ((name, grad), idx) =>
        (idx, name, grad, opt.createState(idx, argDict(name)))
      }

      val evalMetric = new CustomMetric(Utils.perplexity, "perplexity")
      val batchEndCallback = new Callback.Speedometer(batchSize, 50)
      val epochEndCallback = Utils.doCheckpoint("namefinder.model")

      for (epoch <- 0 until numEpoch) {
        // Training phase
        val tic = System.currentTimeMillis
        evalMetric.reset()
        var nBatch = 0
        var epochDone = false
        // Iterate over training data.
        dataTrain.reset()
        while (!epochDone) {
          var doReset = true
          while (doReset && dataTrain.hasNext) {
            val dataBatch = dataTrain.next()

            data.set(dataBatch.data(0))
            label.set(dataBatch.label(0))
            executor.forward(isTrain = true)
            executor.backward()
            paramsGrads.foreach { case (idx, name, grad, optimState) =>
              opt.update(idx, argDict(name), grad, optimState)
            }

            // evaluate at end, so out_cpu_array can lazy copy
            evalMetric.update(dataBatch.label, executor.outputs)

            nBatch += 1
            batchEndCallback.invoke(epoch, nBatch, evalMetric)
          }
          if (doReset) {
            dataTrain.reset()
          }
          // this epoch is done
          epochDone = true
        }
        val (name, value) = evalMetric.get
        name.zip(value).foreach { case (n, v) =>
          println(s"Epoch[$epoch] Train-$n=$v")
        }
        val toc = System.currentTimeMillis
        println(s"Epoch[$epoch] Time cost=${toc - tic}")

        epochEndCallback.invoke(epoch, symbol, argDict, auxDict)
      }
      executor.dispose()
    } catch {
      case ex: Exception => {
        ex.printStackTrace()
        sys.exit(1)
      }
    }

    // String, numLstmLayer: Int, inputSize: Int,numHidden: Int, numEmbed: Int, numLabel: Int,

    val evalStream = new NameSampleDataStream(new PlainTextByLineStream(
      new MarkableFileInputStreamFactory(new File(args(2))), StandardCharsets.UTF_8))

    val nameFinder = new NameFinderDL(wordVectors, modelPrefix = "namefinder.model", numLstmLayer = numLstmLayer,
      numHidden = numHidden, numEpoch = numEpoch)

    print("Evaluating ... ")
    val nameFinderEvaluator = new TokenNameFinderEvaluator(nameFinder)
    nameFinderEvaluator.evaluate(evalStream)
    println("Done")
    println()
    println()
    println("Results")

    println(nameFinderEvaluator.getFMeasure.toString)
  }
}
