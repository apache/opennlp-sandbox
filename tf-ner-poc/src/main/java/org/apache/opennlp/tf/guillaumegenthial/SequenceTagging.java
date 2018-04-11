package org.apache.opennlp.tf.guillaumegenthial;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class SequenceTagging implements AutoCloseable {

  private final SavedModelBundle model;
  private final Session session;
  private final WordIndexer wordIndexer;
  private final IndexTagger indexTagger;

  public SequenceTagging(PredictionConfiguration config) throws IOException {
    this.model = SavedModelBundle.load(config.getSavedModel(), "serve");
    this.session = model.session();
    this.wordIndexer = new WordIndexer(new GZIPInputStream(new FileInputStream(config.getVocabWords())),
            new GZIPInputStream(new FileInputStream(config.getVocabChars())));
    this.indexTagger = new IndexTagger(new GZIPInputStream(new FileInputStream(config.getVocabTags())));
  }

  public String[] predict(String[] sentence) {
    TokenIds tokenIds = wordIndexer.toTokenIds(sentence);
    return predict(tokenIds)[0];
  }

  public String[][] predict(String[][] sentences) {
    TokenIds tokenIds = wordIndexer.toTokenIds(sentences);
    return predict(tokenIds);
  }

  private String[][] predict(TokenIds tokenIds) {
    FeedDictionary fd = FeedDictionary.create(tokenIds);

    List<Tensor<?>> run = session.runner()
            .feed("char_ids:0", fd.getCharIdsTensor())
            .feed("dropout:0", fd.getDropoutTensor())
            .feed("sequence_lengths:0", fd.getSentenceLengthsTensor())
            .feed("word_ids:0", fd.getWordIdsTensor())
            .feed("word_lengths:0", fd.getWordLengthsTensor())
            .fetch("proj/logits", 0)
            .fetch("trans_params", 0).run();


    float[][][] logits = new float[fd.getNumberOfSentences()][fd.getMaxSentenceLength()][indexTagger.getNumberOfTags()];
    run.get(0).copyTo(logits);

    float[][] trans_params = new float[indexTagger.getNumberOfTags()][indexTagger.getNumberOfTags()];
    run.get(1).copyTo(trans_params);

    //# iterate over the sentences because no batching in vitervi_decode
    //for logit, sequence_length in zip(logits, sequence_lengths):
    //List<List<Integer>> viterbi_sequences = new ArrayList<>();

    String[][] returnValue = new String[fd.getNumberOfSentences()][];
    for (int i=0; i < logits.length; i++) {
      //logit = logit[:sequence_length] # keep only the valid steps
      float[][] logit = Arrays.copyOf(logits[i], fd.getSentenceLengths()[i]);
      returnValue[i] = Viterbi.decode(logit, trans_params).stream().map(indexTagger::getTag).toArray(String[]::new);
    }

    return returnValue;
  }

  @Override
  public void close() throws Exception {
    session.close();
  }
}
