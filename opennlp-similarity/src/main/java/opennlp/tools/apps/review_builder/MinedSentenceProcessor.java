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

package opennlp.tools.apps.review_builder;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.Utils;

public class MinedSentenceProcessor {

  public static String acceptableMinedSentence(String sent) {
    // if too many commas => seo text

    String[] commas = StringUtils.split(sent, ',');
    String[] spaces = StringUtils.split(sent, ' ');
    if ((float) commas.length / (float) spaces.length > 0.7) {
      System.out.println("Rejection: too many commas");
      return null;
    }
    
    String[] otherDelimiters = StringUtils.split(sent, '/');
    if ((float) otherDelimiters.length / (float) spaces.length > 0.7) {
        System.out.println("Rejection: too many delimiters");
        return null;
    }
    
    otherDelimiters = StringUtils.split(sent, '.');
    if ((float) otherDelimiters.length / (float) spaces.length > 0.7) {
        System.out.println("Rejection: too many delimiters");
        return null;
    }
    otherDelimiters = StringUtils.split(sent, '!');
    if ((float) otherDelimiters.length / (float) spaces.length > 0.7) {
        System.out.println("Rejection: too many delimiters");
        return null;
    }
    otherDelimiters = StringUtils.split(sent, '=');
    if ((float) otherDelimiters.length / (float) spaces.length > 0.7) {
        System.out.println("Rejection: too many delimiters");
        return null;
    }
    
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

    if (sentTry.contains("click here") || sentTry.contains(" wikip")
        || sentTry.contains("copyright")
        || sentTry.contains("operating hours")
        || sentTry.contains("days per week")
        || sentTry.contains("click for") || sentTry.contains("photos")
        || sentTry.contains("find the latest")
        || sentTry.startsWith("subscribe")
        || sentTry.contains("Terms of Service")
        || sentTry.contains("clicking here")
        || sentTry.contains("skip to") || sentTry.contains("sidebar")
        || sentTry.contains("Tags:") || sentTry.startsWith("Posted by")
        || sentTry.contains("available online")
        || sentTry.contains("get online")
        || sentTry.contains("buy online")
        || sentTry.contains("not valid") || sentTry.contains("discount")
        || sentTry.contains("official site")
        || sentTry.contains("this video")
        || sentTry.contains("this book")
        || sentTry.contains("this product")
        || sentTry.contains("paperback") || sentTry.contains("hardcover")
        || sentTry.contains("audio cd")
        || sentTry.contains("related searches")
        || sentTry.contains("permission is granted")
        || sentTry.contains("[edit")
        || sentTry.contains("edit categories")
        || sentTry.contains("free license")
        || sentTry.contains("under the terms")
        || sentTry.contains("rights reserved")
        || sentTry.contains("wikipedia") || sentTry.endsWith("the")
        || sentTry.endsWith("the.") || sentTry.startsWith("below") 
        || sentTry.contains("recipient of") || sentTry.contains("this message")
        || sentTry.contains("mailing list") || sentTry.contains("purchase order")
        || sentTry.contains("mon-fri") || sentTry.contains("email us") || sentTry.contains("privacy pol") || sentTry.contains("back to top")
        || sentTry.contains("for details") || sentTry.contains("assistance?") || sentTry.contains("chat live")
        || sentTry.contains("free shipping") || sentTry.contains("company info") || sentTry.contains("satisfaction g") || sentTry.contains("contact us")
        ||sentTry.startsWith("write") || sentTry.startsWith( "email")|| sentTry.contains("conditions")
        ||sentTry.startsWith("we ") || sentTry.contains("the recipient") || sentTry.contains("day return") || sentTry.contains("days return")
        
        ||sentTry.startsWith("fax") || sentTry.contains("refund it") || sentTry.contains("your money")
        ||sentTry.startsWith("free") || sentTry.contains("purchase orders")
        ||sentTry.startsWith("exchange it ") || sentTry.contains("return it") || sentTry.contains("credit card")
        
        || sentTry.contains("storeshop") || sentTry.startsWith( "find") || sentTry.startsWith( "shop") || sentTry.startsWith( "unlimited")
        || sentTry.contains("for a limited time") || sentTry.contains("prime members") || sentTry.contains("amazon members") || sentTry.contains("unlimited free")
        || sentTry.contains("shipping") || sentTry.startsWith( "amazon")
        // not a script text
        || sentTry.contains("document.body") || sentTry.contains(" var ") || sentTry.contains("search suggestions") ||sentTry.startsWith( "Search")
        
    		)
      return null;
    
    //Millions of Amazon Prime members enjoy instant videos, free Kindle books and unlimited free two-day shipping.

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

  public static String normalizeForSentenceSplitting(String pageContent) {
    pageContent = pageContent.replace("Jan.", "January").replace("Feb.", "February")
        .replace("Mar.", "March").replace("Apr.", "April")
        .replace("Jun.", "June").replace("Jul.", "July")
        .replace("Aug.", "August").replace("Sep.", "September")
        .replace("Oct.", "October").replace("Nov.", "November")
        .replace("Dec.", "December");

    return pageContent;
  }
}