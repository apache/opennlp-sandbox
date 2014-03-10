/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.utils.classification;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.classification.SimpleNaiveBayesClassifier}
 */
public class SimpleNaiveBayesClassifierTest {

  @Test
  public void ppsIntegrationTest() throws Exception {
    Map<String, String> trainedCorpus = new HashMap<String, String>();
    trainedCorpus.put("CAVOUR ad.te napoleone III affare: cat. C/2 ottimo" +
            " stato ingresso angolo cottura bagno con doccia e camera. " +
            "ottimo per investimento o piccolo studio per professionisti" +
            " e 99.000 Ag.Imm.", "A");
    trainedCorpus.put("TRASTEVERE via degli Orti di Trastevere in palazzo " +
            "signorile (con s. portineria) appartamento mq 180 + cantina mq" +
            " 6 con rifiniture di pregio marmi & armadi a muro + ampia " +
            "balconata 50 mq assolutamente no agenzie E 930.000", "N");
    trainedCorpus.put("CORSO VITTORIO Emanuele V. del banco di santo spirito" +
            " 3° piano con ascensore appartamento di 142 mq commerciali " +
            "composto da: ingresso disimpegno tre camere soggiorno cucina" +
            " due bagni due cantine per un totale di 15 mq e. 900.000 Ag.Imm.", "A");
    trainedCorpus.put("TRASTEVERE Ippolito Nievo quinto piano tripla " +
            "esposizione ingresso salone doppio cucina abitabile tre " +
            "camere servizio ripostiglio terrazzo e soffitta da ristrutturare " +
            "e 650.000 Ag.Imm.", "A");
    trainedCorpus.put("TRASTEVERE E.Rolli solo privati palazzo epoca doppia" +
            " esposizione ingresso soppalcato soggiorno 2 camere cucinotto " +
            "bagno 84 mq IV piano no ascensore 385.000 giardino condominio", "N");
    trainedCorpus.put("CENTRO monti sforza elegante edificio con ampi spazi" +
            " comuni ristrutturato ingresso soggiorno angolo cucina camera " +
            "letto armadi a muro bagno vasca con finestra pavimenti cotto " +
            "luminoso silenzioso doppio affaccio climatizzato e 405.000 ag. " +
            "imm. cl en.g", "A");
    trainedCorpus.put("SAN LORENZO app.to epoca privato vende salone due " +
            "camere cucina abit. due bagni ripostigli vari II piano con" +
            " ascensore triplo affaccio E 530.000 ", "N");
    trainedCorpus.put("SAN LORENZO Via Porta Labicana appartamento mq 80 " +
            "piano rialzato con ingresso 3 camere cucina bagno E 395.000 ", "N");
    trainedCorpus.put("SAN LORENZO via degli Umbri I° p. 3 stanze cucina " +
            "servizio terrazzino interno buono stato E. 390.000 tratt. " +
            "assoloutamente no agenzie ", "N");


    SimpleNaiveBayesClassifier classifier = new SimpleNaiveBayesClassifier(trainedCorpus);

    Boolean isAgency = classifier.calculateClass("CENTRO S.Maria Maggiore " +
            "angolo Napoleone III in palazzo epoca con portiere 110 mq ristrutt." +
            " IIp salone doppio cucina ab. 2 camere bagno ripost. balcone " +
            "perimetrale E. 730.000 tratt. ").equals("A");
    assertFalse(isAgency);

    isAgency = classifier.calculateClass("TRASTEVERE via del Mattonato in " +
            "piccola palazzina d'epoca app.to finemente ristrutturato " +
            "ingresso salone camera cucina tinello servizio balconcino " +
            "aria condiz. e 540.000 Ag.Imm. ").equals("A");
    assertTrue(isAgency);

  }
}
