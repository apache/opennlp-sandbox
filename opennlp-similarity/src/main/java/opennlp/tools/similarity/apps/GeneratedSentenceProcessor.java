/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.similarity.apps;

import java.util.Arrays;
import java.util.List;

import opennlp.tools.similarity.apps.utils.Utils;

import org.apache.commons.lang.StringUtils;

public class GeneratedSentenceProcessor {
  public static String acceptableMinedSentence(String sent) {
    // if too many commas => seo text

    String[] commas = StringUtils.split(sent, ',');
    String[] spaces = StringUtils.split(sent, ' ');
    if ((float) commas.length / (float) spaces.length > 0.7) {
      System.out.println("Rejection: too many commas");
      return null;
    }

    String[] pipes = StringUtils.split(sent, '|');
    if (StringUtils.split(sent, '|').length > 2
        || StringUtils.split(sent, '>').length > 2) {
      System.out.println("Rejection: too many |s or >s ");
      return null;
    }
    String sentTry = sent.toLowerCase();
    // if too many long spaces
    String sentSpaces = sentTry.replace("   ", "");
    if (sentSpaces.length() - sentTry.length() > 10) // too many spaces -
      // suspicious
      return null;

    if (sentTry.indexOf("click here") > -1 || sentTry.indexOf(" wikip") > -1
        || sentTry.indexOf("copyright") > -1
        || sentTry.indexOf("operating hours") > -1
        || sentTry.indexOf("days per week") > -1
        || sentTry.indexOf("click for") > -1 || sentTry.indexOf("photos") > -1
        || sentTry.indexOf("find the latest") > -1
        || sentTry.startsWith("subscribe")
        || sentTry.indexOf("Terms of Service") > -1
        || sentTry.indexOf("clicking here") > -1
        || sentTry.indexOf("skip to") > -1 || sentTry.indexOf("sidebar") > -1
        || sentTry.indexOf("Tags:") > -1 || sentTry.startsWith("Posted by")
        || sentTry.indexOf("available online") > 0
        || sentTry.indexOf("get online") > 0
        || sentTry.indexOf("buy online") > 0
        || sentTry.indexOf("not valid") > 0 || sentTry.indexOf("discount") > 0
        || sentTry.indexOf("official site") > 0
        || sentTry.indexOf("this video") > 0
        || sentTry.indexOf("this book") > 0
        || sentTry.indexOf("this product") > 0
        || sentTry.indexOf("paperback") > 0 || sentTry.indexOf("hardcover") > 0
        || sentTry.indexOf("audio cd") > 0
        || sentTry.indexOf("related searches") > 0
        || sentTry.indexOf("permission is granted") > 0
        || sentTry.indexOf("[edit") > 0
        || sentTry.indexOf("edit categories") > 0
        || sentTry.indexOf("free license") > 0
        || sentTry.indexOf("permission is granted") > 0
        || sentTry.indexOf("under the terms") > 0
        || sentTry.indexOf("rights reserved") > 0
        || sentTry.indexOf("wikipedia") > 0 || sentTry.endsWith("the")
        || sentTry.endsWith("the.") || sentTry.startsWith("below")

    )
      return null;

    // count symbols indicating wrong parts of page to mine for text
    // if short and contains too many symbols indicating wrong area: reject
    String sentWrongSym = sentTry.replace(">", "&&&").replace("�", "&&&")
        .replace("|", "&&&").replace(":", "&&&").replace("/", "&&&")
        .replace("-", "&&&").replace("%", "&&&");
    if ((sentWrongSym.length() - sentTry.length()) >= 4
        && sentTry.length() < 200) // twice ot more
      return null;

    sent = sent.replace('[', ' ').replace(']', ' ')
        .replace("_should_find_orig_", "").replace(".   .", ". ")
        .replace("amp;", " ").replace("1.", " ").replace("2.", " ")
        .replace("3.", " ").replace("4.", " ").replace("2009", "2011")
        .replace("2008", "2011").replace("2006", "2011")
        .replace("2007", "2011").replace("VIDEO:", " ").replace("Video:", " ")
        .replace("no comments", " ").replace("  ", " ").replace("  ", " ")
        .replace("(more.)", "").replace("more.", "").replace("<more>", "")
        .replace("[more]", "").replace(".,", ".").replace("&lt;", "")
        .replace("p&gt;", "").replace("product description", "");

    // TODO .replace("a.", ".");

    int endIndex = sent.indexOf(" posted");
    if (endIndex > 0)
      sent = sent.substring(0, endIndex);

    return sent;
  }

  public static String processSentence(String pageSentence) {
    if (pageSentence == null)
      return "";
    pageSentence = Utils.fullStripHTML(pageSentence);
    pageSentence = StringUtils.chomp(pageSentence, "..");
    pageSentence = StringUtils.chomp(pageSentence, ". .");
    pageSentence = StringUtils.chomp(pageSentence, " .");
    pageSentence = StringUtils.chomp(pageSentence, ".");
    pageSentence = StringUtils.chomp(pageSentence, "...");
    pageSentence = StringUtils.chomp(pageSentence, " ....");
    pageSentence = pageSentence.replace("::", ":").replace(".,", ". ")
        .replace("(.)", "");

    pageSentence = pageSentence.trim();
    pageSentence = pageSentence.replaceAll("\\s+", " "); // make single
    // spaces
    // everywhere

    String[] pipes = StringUtils.split(pageSentence, '|'); // removed
    // shorter part
    // of sentence
    // at the end
    // after pipe
    if (pipes.length == 2
        && ((float) pipes[0].length() / (float) pipes[1].length() > 3.0)) {
      int pipePos = pageSentence.indexOf("|");
      if (pipePos > -1)
        pageSentence = pageSentence.substring(0, pipePos - 1).trim();

    }

    if (!StringUtils.contains(pageSentence, '.')
        && !StringUtils.contains(pageSentence, '?')
        && !StringUtils.contains(pageSentence, '!'))
      pageSentence = pageSentence + ". ";

    pageSentence = pageSentence.replace(" .", ".").replace("..", ".").trim();
    if (!pageSentence.endsWith("."))
      pageSentence += ". ";
    return pageSentence;
  }

  public static void main(String[] args) {

    String para = "About Albert Einstein     15 External links  16 Credits         Youth and schooling  Albert Einstein was born into a Jewish family";
    para = "inventions of albert einstein                            what was albert einsteins invention                            invention of einstein                            what were albert einsteins inventions ";

    para = para.replaceAll("  [A-Z]", ". $0");
    System.out.println(para);

    para = "Page 2 of 93";

    System.exit(0);
    RelatedSentenceFinder f = new RelatedSentenceFinder();
    try {
      List<HitBase> hits = f
          .findRelatedOpinionsForSentence(
              "Give me a break, there is no reason why you can't retire in ten years if you had been a rational investor and not a crazy trader",
              Arrays
                  .asList(new String[] { "Give me a break there is no reason why you can't retire in ten years if you had been a rational investor and not a crazy trader. For example you went to cash in 2008 and stay in cash until now you made nothing. Whereas people who rode out the storm are doing fine so let's quit focusing on the loser who think they are so smart and went to 100% cash and are wondering what happen. Its a market that always moves unlike your mattress.", }));
      StringBuffer buf = new StringBuffer();

      for (HitBase h : hits) {
        List<Fragment> frags = h.getFragments();
        for (Fragment fr : frags) {
          if (fr.getResultText() != null && fr.getResultText().length() > 3)
            buf.append(fr.getResultText());
        }
      }

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  public static String normalizeForSentenceSplitting(String pageContent) {
    pageContent.replace("Jan.", "January").replace("Feb.", "February")
        .replace("Mar.", "March").replace("Apr.", "April")
        .replace("Jun.", "June").replace("Jul.", "July")
        .replace("Aug.", "August").replace("Sep.", "September")
        .replace("Oct.", "October").replace("Nov.", "November")
        .replace("Dec.", "December");

    return pageContent;

  }
}