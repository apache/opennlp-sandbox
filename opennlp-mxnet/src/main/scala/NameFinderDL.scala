import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.util
import java.util.stream.{Collectors, IntStream}

import main.GloveUtil
import opennlp.tools.namefind.{BioCodec, NameSample, NameSampleDataStream, TokenNameFinder}
import opennlp.tools.util.{MarkableFileInputStreamFactory, ObjectStream, PlainTextByLineStream, Span}
import ml.dmlc.mxnet.optimizer.Adam
import ml.dmlc.mxnet._
import org.kohsuke.args4j.CmdLineParser

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class NameFinderDL(val wordVectors: Map[String, Array[Float]], val modelPrefix: String, numLstmLayer: Int,
                   numHidden: Int, numEpoch: Int, ctx: Context = Context.cpu(),
                   dropout: Float = 0f) extends TokenNameFinder {

  val (_, argParams, _) = Model.loadCheckpoint(modelPrefix, numEpoch) // number is the checkpoint

  val model = new RnnModel.LSTMInferenceModel(numLstmLayer,
     numHidden = numHidden,
     numLabel = 3, argParams = argParams, dropout = 0.2f)

  val outcomeMapping = Array("other", "default-start", "default-cont")

  var sentCount = 0;

  override def find(tokens: Array[String]): Array[Span] = {

    val inputWordNdarray = NDArray.zeros(1, 300)

    val outcomes = Array.ofDim[String](tokens.length)

    var isNewSentence = true;
    for (i <- 0 until tokens.length) {

      val wordVector = wordVectors.get(tokens(i)).getOrElse(Array.ofDim(300))

      inputWordNdarray.set(wordVector)

      val prob = model.forward(inputWordNdarray, isNewSentence)

      var best = 0;
      for (i <- 0 until outcomeMapping.length) {
        if (prob(i) > prob(best)) {
          best = i;
        }
      }

      outcomes(i) = outcomeMapping(best)

      isNewSentence = false
    }

    println("Eval count: " + sentCount)
    sentCount = sentCount + 1

    new BioCodec().decode(outcomes.toList.asJava)
  }

  override def clearAdaptiveData(): Unit = {
  }
}
