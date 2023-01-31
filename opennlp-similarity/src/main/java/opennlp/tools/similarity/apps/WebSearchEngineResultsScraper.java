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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class WebSearchEngineResultsScraper {
  public WebSearchEngineResultsScraper(){
    buildCache();
  }

  protected static String fetchPageSearchEngine(String url) {
    System.out.println("fetch url " + url);
    String pageContent = null;
    StringBuffer buf = new StringBuffer();
    try {
      URLConnection connection = new URL(url).openConnection();
      connection.setReadTimeout(50000);
      connection
          .setRequestProperty(
              "User-Agent",
              "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-GB; rv:1.9.0.3) Gecko/2008092417 Firefox/3.0.3");
      String line;
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));
      } catch (Exception e) {
        System.err.println("Unable to complete search engine request "+url);
        //e.printStackTrace();
      }

      while ((line = reader.readLine()) != null) {
        buf.append(line);
      }

    } catch (Exception e) {
      // e.printStackTrace();
      System.err.println("error fetching url " + url);
    }

    return buf.toString();
  }

  private static List<String> extractURLsFromPage(String content, String domain) {
    List<String> results = new ArrayList<String>();
    if (content == null)
      return results;
    content = StringUtils.substringBetween(content, ">Advanced</a></div>",
        "<input type=\"text\" value=");
    if (content == null)
      return results;
    String[] urls = content.split("<cite>");
    if (urls == null)
      return results;
    for (String u : urls) {
      int endPos = u.indexOf("</cite>");

      if (endPos > 0) {
        u = u.substring(0, endPos).replace("</strong>", "")
            .replace("<strong>", "");
        if (!u.equals(domain))
          results.add(u);
      }
    }

    return results;
  }

  private static List<HitBase> extractSearchResultFromPage(String content) {
    List<HitBase> results = new ArrayList<HitBase>();
    if (content == null)
      return results;
    content = StringUtils.substringBetween(content, "<div id=\"results",
        "class=\"pagination");
    if (content == null)
      return results;
    String[] srchResArea = content.split("</p>");
    if (srchResArea == null)
      return results;
    for (String u : srchResArea) {
      try {
        u = u.substring(5);
        HitBase hit = new HitBase();
        String url = StringUtils.substringBetween(u, "class=\"url", "</span>");
        if (url!=null)
            url = url.substring(2);
        String title = StringUtils.substringBetween(u, "\">", "</a><br />");
        title = title.substring(title.indexOf("\">")+2);
        String abstr = StringUtils.substringBetween(u, "\"body\">", "</span><br /");
        hit.setUrl(url);
        hit.setAbstractText(abstr);
        hit.setTitle(title);
        results.add(hit);
      } catch (Exception e) {
        //problem parsing SERP page; source - specific problem so we swallow exceptions here
      }
    }

    return results;
  }
  
  private static String formRequestURL(String query) {
    String requestUrl = "http://www.hakia.com/search/web?q=" + query.replace(' ','+');
    return requestUrl;
  }
  
  private static String formRequestURLAlt(String query) {
    String requestUrl = "https://www.google.com/search?q=" + query.replace(' ','+');
    return requestUrl;
  }

  public List<String> getURLsForWebDomain(String domain) {
    return extractURLsFromPage(fetchPageSearchEngine(formRequestURL(domain)), domain);
  }

  public Set<String> getURLsForWebDomainIterations(String domain) {
    List<String> results = new ArrayList<String>();
    List<String> res = extractURLsFromPage(
        fetchPageSearchEngine(formRequestURL(domain)), domain);
    for (String r : res)
      results.addAll(extractURLsFromPage(fetchPageSearchEngine(formRequestURL(r)), r));

    return new HashSet<String>(results);
  }
  
  public List<HitBase> runSearch(String query) {
    List<HitBase> hits = new ArrayList<HitBase>();
    /*  Actual external web search is commented out
    try {
      String serp = fetchPageSearchEngine(formRequestURL(query));
      hits = extractSearchResultFromPage(serp);

    } catch (Exception e) {
    
    } */
    // if there is a problem, we use cached search results
    if (hits.size()<1) {
      String[][] cachedHits = cachedSearchEngineData.get(query.replace(' ','+'));
      if (cachedHits==null){ // not found in cache
        return hits;
      }
      for(String[] h : cachedHits){
        HitBase hb = new HitBase();
        hb.setTitle(h[0]);
        hb.setAbstractText(h[1]);
        hits.add(hb);
      }
    }
//    hits = HitBase.removeDuplicates(hits);
    return hits;
    
  }

  public static void main(String[] args) {
    WebSearchEngineResultsScraper scraper = new WebSearchEngineResultsScraper();
    System.out.println(scraper.runSearch("lady gaga in san francisco"));        
  }
  
  Map<String, String[][]> cachedSearchEngineData = new HashMap<String, String[][]>();
  
  
  private void buildCache(){
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+for+details", new String[][]{
        {"Remember The Milk - Services / Remember The Milk for Email", "to Remember The Milk just by sending an email to a special address. Read this page to learn all the details, and if you have any questions or run into any .... email address] Buy birthday present for Mike today Pay the phone bill tomorrow Make"},
        {"Services / Remember The Milk for Android / Features", "Sync with Remember The Milk online (limit once every 24 hours). ... include extra details about tasks in the 'Add task' bar (e.g., Pick up the milk tomorrow). ... Detect your current location to see nearby tasks; plan the best way to get things done."},
        {"Remember The Milk - Getting Started", "This guide is intended as a brief introduction to using Remember The Milk. ... you can select it in the list below and use the task details box on the right. ... The overview screen is a handy way to see what's due today and tomorrow, and the"},
        {"Remember The Milk - Services / Remember The Milk for iPad / FAQ", "How do I get rid of some cards? If you're finished viewing a task. Can I share lists with other Remember The Milk users? While the app doesn't currently ..."},
        {"Get Organized with Remember the Milk", "Let's take a closer look at Remember the Milk's basic and more ... tabs based on when they're due: today, tomorrow, and overdue. Once you do, on the right hand side, a task box will contain editable details about that task."},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+from+trader+joes", new String[][]{
        {"What I buy at Trader Joe's - 100 Days of Real Food", " First of all, I don't do all my shopping at Trader Joe's (I get produce, dairy, and other items at .... I use the olive oil, I buy my milk and eggs from them. ... Remember I'm talking raw non-homogenized which is even more delicate."},
        {"Trader Joe's List: Gluten Free List", "That's one way to remember it. ... I've been getting reactions to much of what I buy at Trader Joe's. ... The rice milk is made by the same company that makes Rice Dream and Rice Dream uses barley gluten in the process, than claims it is taken ..."},
        {"BPA-Free Versions of Popular Foods | Mark's Daily Apple", "Here's a list of BPA-free coconut milk, fish, pumpkin, and tomatoes. ... and some (like Trader Joe's) don't even put the label on their products. .... but just remember to check with the supplier before making your purchase. ..... Polls go up tomorrow! http://t.co/aOa2Bsf5 2 days ago; Contest Poll: 2012 Primal"},
        {"Trader Joes Opens in Plano TX Tomorrow", "I love their unsweetened vanilla almond milk! .... I love Trader Joes, and will go out of my way to stop at the one in Santa Fe, NM every time I go"},
        {"My Favorite Trader Joe's Pantry Items", "Because I'm somewhat obsessed with shopping at Trader Joe's, people always ..... See you tomorrow night! .... I bake with the almond meal often and buy the fresh almond milk (blue container), .... I remember I used to get excited to go shopping at the mall, now I have those same feelings towards TJ's."},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+from+3+to+jones", new String[][]{
        {"My mom wants me to get some groceries... - Page 3 - Social Anxiety", "When you go the the store and buy some milk you should tell everyone in line who dares to look at you funny yeah that's right I'm buying milk."},
        {"Men in Black 3 Quotes - 'Is there anybody here who is not an alien?'", "Men in Black 3 quotes are a mixed bag but it has a series of okay gags with ... with his dead-on impression of Tommy Lee Jones as young Agent K. The best ..... [as no one seems to remember K at MIB headquarters, J thinks everybody is ... [the agent that had gone to get J some chocolate milk comes up to J with the milk]"},
        {"How to get weed out of your system Fast", "Once you get home you are going to immidiatly start drinking the .... for four hours... remember no toxins 48 hours before and no alcohol .... Robert Jones 2 years ago ... this month. what are my chances of passing my test tomorrow? ..... I can't drink milk nor any juices because of my hyperacidity (GERD)."},
        {"DoubleJones Blog - tidbits of love, life, laughter, and food", "It's sometimes hard to function and hard to want to get out of bed ... Posted in baby amelia | 3 Comments » ... I realized that time has gotten away from me, for tomorrow Amelia would have been four weeks old. ... by now, and hopefully, had become an accomplished milk-cow. .... And then I remembered."},
        {"Jermaine Jones - Milk Chocolate Shitcanned | Vote for the Worst", "JJonesAI11. guess i am the jennifer hudson of the new american idol.... :) ill take it !!!!! "},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+from+third+to+jones", new String[][]{
        {"My mom wants me to get some groceries... - Page 3 - Social Anxiety", "When you go the the store and buy some milk you should tell everyone in line who dares to look at you funny yeah that's right I'm buying milk."},
        {"Men in Black 3 Quotes - 'Is there anybody here who is not an alien?'", "Men in Black 3 quotes are a mixed bag but it has a series of okay gags with ... with his dead-on impression of Tommy Lee Jones as young Agent K. The best ..... [as no one seems to remember K at MIB headquarters, J thinks everybody is ... [the agent that had gone to get J some chocolate milk comes up to J with the milk]"},
        {"How to get weed out of your system Fast", "Once you get home you are going to immidiatly start drinking the .... for four hours... remember no toxins 48 hours before and no alcohol .... Robert Jones 2 years ago ... this month. what are my chances of passing my test tomorrow? ..... I can't drink milk nor any juices because of my hyperacidity (GERD)."},
        {"DoubleJones Blog - tidbits of love, life, laughter, and food", "It's sometimes hard to function and hard to want to get out of bed ... Posted in baby amelia | 3 Comments » ... I realized that time has gotten away from me, for tomorrow Amelia would have been four weeks old. ... by now, and hopefully, had become an accomplished milk-cow. .... And then I remembered."},
        {"Jermaine Jones - Milk Chocolate Shitcanned | Vote for the Worst", "JJonesAI11. guess i am the jennifer hudson of the new american idol.... :) ill take it !!!!! "},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+for+for+details", new String[][]{
        {"Remember The Milk - Services / Remember The Milk for Email", "to Remember The Milk just by sending an email to a special address. Read this page to learn all the details, and if you have any questions or run into any .... email address] Buy birthday present for Mike today Pay the phone bill tomorrow Make"},
        {"Services / Remember The Milk for Android / Features", "Sync with Remember The Milk online (limit once every 24 hours). ... include extra details about tasks in the 'Add task' bar (e.g., Pick up the milk tomorrow). ... Detect your current location to see nearby tasks; plan the best way to get things done."},
        {"Remember The Milk - Getting Started", "This guide is intended as a brief introduction to using Remember The Milk. ... you can select it in the list below and use the task details box on the right. ... The overview screen is a handy way to see what's due today and tomorrow, and the"},
        {"Remember The Milk - Services / Remember The Milk for iPad / FAQ", "How do I get rid of some cards? If you're finished viewing a task. Can I share lists with other Remember The Milk users? While the app doesn't currently ..."},
        {"Get Organized with Remember the Milk", "Let's take a closer look at Remember the Milk's basic and more ... tabs based on when they're due: today, tomorrow, and overdue. Once you do, on the right hand side, a task box will contain editable details about that task."},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+from+third+to+joes", new String[][]{
        {"Remember The Milk - Services / Remember The Milk for Email", "to Remember The Milk just by sending an email to a special address. Read this page to learn all the details, and if you have any questions or run into any .... email address] Buy birthday present for Mike today Pay the phone bill tomorrow Make"},
        {"BPA-Free Versions of Popular Foods | Mark's Daily Apple", "Here's a list of BPA-free coconut milk, fish, pumpkin, and tomatoes. ... and some (like Trader Joe's) don't even put the label on their products. .... but just remember to check with the supplier before making your purchase. ..... Polls go up tomorrow! http://t.co/aOa2Bsf5 2 days ago; Contest Poll: 2012 Primal"},
        {"20 Best Online To Do List Apps for Freelancers", "Remember the Milk is a web application with numerous mobile app ... complete today will automatically get moved to the tomorrow column."},
        {"Harvey Milk - Wikipedia, the free encyclopedia", "A black and white photograph of Harvey Milk sitting at the mayor's desk .... In 1956, he met Joe Campbell, at the Jacob Riis Park beach, a popular location for .... but remembered Milk's attitude: I think he was happier than at any time I had ever .... He often repeated his philosophy that gays should buy from gay businesses."},
        {"Test Utterances - MIT", "remember to pick up milk at seven (smste00006) get some cleaning supplies at ... tomorrow (smste00025) can we have a quick dinner this week (smste00026) ..... (smste00217) remind me to call estefana+1 on the twenty third (smste00218) ... of joe's+2 coffee+2 shop+2 then we'll go together (smste00220) can we have a"},
    });
    cachedSearchEngineData.put("remember+to+buy+milk+tomorrow+from+for+d+jones", new String[][]{
        {"djones - Rock Island/Milan School District #41", "Item 361 - 380 – Profile picture of djones. djones. @djones active 6 months, 3 weeks ago ... There are many buy one, get one free offers for area restaurants, museums, zoo, sporting events, etc. ... Vicki Todd wrote a new blog post: PLEASE REMEMBER… ... NOTE: although we collect Swiss Valley milk caps and Campbell's"},
        {"D-Jones (dcomplex12) on Twitter", "D-Jones. @dcomplex12. Just tryna take over the World...Nothing Major. #TeamGNATION SALUTE!!! CST- Stamford · http://Www.facebook.com/dcomplex12 "},
        {"Tagged - Damaya Lady D Jones's Profile", "Join Tagged and be friends with Damaya Lady D Jones - it's free! ... Purpose Driven Life by Rick Warren, Milk In My Coffee by Eric Jerome Dickey, ... I know tomorrow is not promised so I always try to remember to cherish LIFE because"},
    });
    
    cachedSearchEngineData.put("How+can+I+pay+tax+on+my+income+abroad", new String[][]{
        {"Ask Stacy: Do I Have to Pay Taxes If I'm Not in the US?", "You'd think that if you're living abroad and earning all your money from a foreign ... My son has been living out of the country for the last five years but is moving back ... Could he be responsible to pay when there's no income?"},
        {"Do I have to pay US taxes when I work abroad?", "Even if you avoid U.S. income tax, you will likely pay some form of income tax to the ... My son is working overseas and pays all taxes and health insurance there."},
        {"Big Paychecks, Tiny Tax Burdens: How 21,000 Wealthy Americans", "$2.8 billion, but she did not pay a dime in state income tax in 2010, the ... investing in local and state governments, earning money overseas and ... I want my tax dollars to go to my children's schools, not the president of GE"},
        {"U.S. Citizens Living Abroad Taxes: Frequently Asked Questions","For the United States income tax return, you will have several options available to you regarding claiming a .... My employer withheld the taxes from my pay."},
        {"Going to Work Abroad - Revenue Commissioners", "A guide to Irish income tax and capital gains tax liability based on some commonly ... In general, tax is deducted from your salary through what is known as the Pay As You Earn (PAYE) ..... What happens if my income is also taxable abroad?"},
        {"Tax on overseas income : Directgov - Money, tax and benefits", "If you live in the UK permanently you'll pay tax on overseas income. If you live here temporarily, you'll normally pay tax only on overseas income you bring into"},
        {"FAQs - AARO - Association of Americans Resident Overseas", "All my income comes from my host country and I pay plenty of taxes there! ... is that earned income from overseas can be exempted from US income tax (but only .."},
    });
    cachedSearchEngineData.put("Can+I+estimate+what+my+income+tax+would+be+by+using+my+last+pay", new String[][]{
        {"How do I estimate my tax return with my last pay stub", "How do I estimate my tax return with my last pay stub? ... no dependents, your non taxable income is $9350, any excess of that will be taxed at ... Using my pay stub for taxes file my taxes, is H.R.block.  Can I use only my last paystub to do my taxes "},
        {"IRS Withholding Calculator", "You can use your results from the calculator to help fill out the form. Who Can Benefit From The ... and deductions, who would prefer to have tax on that income withheld from their paychecks rather than make periodic separate payments through the estimated tax procedures"},
        {"Tax Foundation's Tax Policy Calculator", "The goal with this calculator is to show the effects of the Bush and Obama tax cuts ... Calculate My Income Tax ... The calculator will not display correctly if more than four scenarios are"},
        {"Use Tax Questions and Answers","This series of questions and answers refers specifically to the use tax incurred on ... For taxpayers who do not have records to document their use tax liability, the department will estimate liability. ... Individual Income Tax Return, on or before April 15 of the following year (for tax ... Can I file and pay my use tax electronically?"},
        {"Web Pay - Frequently Asked Questions | California Franchise Tax", "What will happen to my information if I log out before I submit my request? ... Can I use Web Pay if my last name changed in 2011? ... an electronic payment from your bank account to pay your personal and business income taxes. ... Estimated tax; Tax return; Billing notice; Extension; Notice of Proposed Assessment; Tax "},
        {"Can I use turbo tax to file using my last paystub, not my w-2", "Can I use turbo tax to file using my last paystub, not my w-2. ... if you use your last pay stub it does not have all the information you need, usually there ... I hope you are only trying to do some initial estimating but will wait to receive your ... plan, no income which may be recharacterized, no pension plan, etc."},
        {"Can I Find Out How Much I Will Get Back on My Taxes Without My W", "While you will need your W-2 or a substitute, you can figure out your tax obligation ... final pay stub for a job (if you are no longer employed) or the last pay stub for the year ... the year, you can estimate your income and taxes from a single pay stub. ... However, if you worked at the same job the previous year, you can use the ..."},
    });
  }

}
