package opennlp.tools.disambiguator;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.disambiguator.ims.IMS;

public class Tester {

  public static void main(String[] args) {

    String modelsDir = "src\\test\\resources\\models\\";
    WSDHelper.loadTokenizer(modelsDir + "en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir + "en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir + "en-pos-maxent.bin");

    IMS ims = new IMS();

    String test3 = "The summer is almost over and I haven't been to the beach even once";
    String[] sentence3 = WSDHelper.getTokenizer().tokenize(test3);
    String[] tags3 = WSDHelper.getTagger().tag(sentence3);
    List<String> tempLemmas3 = new ArrayList<String>();
    for (int i = 0; i < sentence3.length; i++) {
      String lemma = WSDHelper.getLemmatizer()
          .lemmatize(sentence3[i], tags3[i]);
      tempLemmas3.add(lemma);
    }
    String[] lemmas3 = tempLemmas3.toArray(new String[tempLemmas3.size()]);

    // output
    List<String[]> senses3 = ims.disambiguate(sentence3, tags3, lemmas3);
    for (int i = 0; i < sentence3.length; i++) {
      System.out.print(sentence3[i] + " : ");
      WSDHelper.printResults(ims, senses3.get(i));
      WSDHelper.print("----------");
    }

  }
}