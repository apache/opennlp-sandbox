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

package opennlp.tools.parse_thicket.apps;

import java.util.Arrays;
import java.util.List;

import opennlp.tools.similarity.apps.utils.Utils;

import org.apache.commons.lang.StringUtils;

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
        || sentTry.indexOf("available online") > -1
        || sentTry.indexOf("get online") > -1
        || sentTry.indexOf("buy online") > -1
        || sentTry.indexOf("not valid") > -1 || sentTry.indexOf("discount") > -1
        || sentTry.indexOf("official site") > -1
        || sentTry.indexOf("this video") > -1
        || sentTry.indexOf("this book") > -1
        || sentTry.indexOf("this product") > -1
        || sentTry.indexOf("paperback") > -1 || sentTry.indexOf("hardcover") > -1
        || sentTry.indexOf("audio cd") > -1
        || sentTry.indexOf("related searches") > -1
        || sentTry.indexOf("permission is granted") > -1
        || sentTry.indexOf("[edit") > -1
        || sentTry.indexOf("edit categories") > -1
        || sentTry.indexOf("free license") > -1
        || sentTry.indexOf("permission is granted") > -1
        || sentTry.indexOf("under the terms") > -1
        || sentTry.indexOf("rights reserved") > -1
        || sentTry.indexOf("wikipedia") > -1 || sentTry.endsWith("the")
        || sentTry.endsWith("the.") || sentTry.startsWith("below") 
        || sentTry.indexOf("recipient of")>-1 || sentTry.indexOf("this message")>-1 
        ||sentTry.indexOf( "mailing list")>-1 ||sentTry.indexOf( "purchase order")>-1
        ||sentTry.indexOf( "mon-fri")>-1 ||sentTry.indexOf( "email us")>-1 ||sentTry.indexOf( "privacy pol")>-1 ||sentTry.indexOf( "back to top")>-1 
        ||sentTry.indexOf( "click here")>-1 ||sentTry.indexOf( "for details")>-1 ||sentTry.indexOf( "assistance?")>-1 ||sentTry.indexOf( "chat live")>-1
        ||sentTry.indexOf( "free shipping")>-1 ||sentTry.indexOf( "company info")>-1 ||sentTry.indexOf( "satisfaction g")>-1 ||sentTry.indexOf( "contact us")>-1
        ||sentTry.startsWith( "fax") ||sentTry.startsWith( "write") || sentTry.startsWith( "email")||sentTry.indexOf( "conditions")>-1 ||sentTry.indexOf( "chat live")>-1
        ||sentTry.startsWith( "we ") ||sentTry.indexOf( "the recipient")>-1 ||sentTry.indexOf( "day return")>-1 ||sentTry.indexOf( "days return")>-1
        
        ||sentTry.startsWith( "fax") ||sentTry.indexOf( "refund it")>-1 || sentTry.indexOf( "your money")>-1
        ||sentTry.startsWith( "free") ||sentTry.indexOf( "purchase orders")>-1
        ||sentTry.startsWith( "exchange it ") ||sentTry.indexOf( "return it")>-1 ||sentTry.indexOf( "credit card")>-1 
        
        ||sentTry.indexOf( "storeshop")>-1 || sentTry.startsWith( "find") || sentTry.startsWith( "shop") || sentTry.startsWith( "unlimited") 
        ||sentTry.indexOf( "for a limited time")>-1 ||sentTry.indexOf( "prime members")>-1 ||sentTry.indexOf( "amazon members")>-1 ||sentTry.indexOf( "unlimited free")>-1 
        ||sentTry.indexOf( "shipping")>-1 || sentTry.startsWith( "amazon")
// not a script text
        ||sentTry.indexOf( "document.body")>-1 ||sentTry.indexOf( " var ")>-1         ||sentTry.indexOf( "search suggestions")>-1 ||sentTry.startsWith( "Search") 
        
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
    pageContent.replace("Jan.", "January").replace("Feb.", "February")
        .replace("Mar.", "March").replace("Apr.", "April")
        .replace("Jun.", "June").replace("Jul.", "July")
        .replace("Aug.", "August").replace("Sep.", "September")
        .replace("Oct.", "October").replace("Nov.", "November")
        .replace("Dec.", "December");

    return pageContent;

  }
}