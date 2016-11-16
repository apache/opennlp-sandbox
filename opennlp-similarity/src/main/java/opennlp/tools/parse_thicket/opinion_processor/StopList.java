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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.stemmer.PStemmer;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class StopList {
    private static StopList m_StopList = null;
    private static Hashtable<String, HashSet<String>> m_stopHash = new Hashtable<String, HashSet<String>>();
    public static final Log logger = LogFactory.getLog(StopList.class);
    private static final String DEFAULT_STOPLIST = "STANDARD";
    public static String resourceDir =null;
    private static PStemmer stemmer = new PStemmer();

    static {
        synchronized (StopList.class) {
            try {
                LoadStopList();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the StopList singleton instance.
     * 
     * @return The StopList
     */
    static public synchronized StopList getInstance() {

        if (m_StopList == null) {
            m_StopList = new StopList();

            try {
                m_StopList.LoadStopList();
            } catch (Exception e) {

            }
        }
        return m_StopList;
    }

    static public synchronized StopList getInstance(String dir) {
        resourceDir = dir;
        if (m_StopList == null) {
            m_StopList = new StopList();

            try {
                m_StopList.LoadStopList();
            } catch (Exception e) {

            }
        }
        return m_StopList;
    }

    private static void LoadStopList() throws IOException {

        File dir = new File(resourceDir + "/maps");
        String[] children = dir.list();
        if (children == null) {
            System.err.println("Problem reading Stop Lists!");
        } else {
            for (int i = 0; i < children.length; i++) {
                String fn = children[i];
                if (fn.endsWith(".vcb")) {
                    String fileName = resourceDir + "/maps/" + fn;
                    File f = new File(fileName);
                    loadStopListFile(f);
                }
            }
        }
    }

    private static void loadStopListFile(File f) throws FileNotFoundException {

        FileReader fileReader = new FileReader(f);
        BufferedReader in = new BufferedReader(fileReader);

        String str = new String();
        boolean fLine = true;
        HashSet<String> t = new HashSet<String>();
        String listName = "";

        try {
            while ((str = in.readLine()) != null) {
                if (fLine && str.length() > 0) {
                    fLine = false;
                    listName = str;
                } else {
                    t.add(str);
                }
            }
        } catch (IOException ioe) {

        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (fileReader != null) {
                    fileReader.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        if (listName.length() > 0) {
            HashSet<String> l = m_stopHash.get(listName);
            if (l != null) {
                synchronized (l) {
                    m_stopHash.put(listName, t);
                }
            } else {
                m_stopHash.put(listName, t);
            }
        }
    }

    /**
     * Is the given word in the stop words list? Uses the defaut "STANDARD"
     * stoplist
     * 
     * @param str
     *            The word to check
     * @return is a stop word
     */
    public static boolean isStopWord(String str) {
        boolean retVal = false;
        if (m_stopHash.containsKey(DEFAULT_STOPLIST))
            retVal = m_stopHash.get(DEFAULT_STOPLIST).contains(str);
        return retVal;
    }

    public static boolean isFirstName(String str) {
        boolean retVal = false;
        if (m_stopHash.containsKey("FIRST_NAMES"))
            retVal = m_stopHash.get("FIRST_NAMES").contains(str.toUpperCase());
        return retVal;
    }

    public String getRandomFirstName() {
        HashSet<String> firstNames = m_stopHash.get("FIRST_NAMES");
        int indexRand = (int) (Math.random() * new Float(firstNames.size()));
        Iterator iter = firstNames.iterator();
        for (int i = 0; i < indexRand; i++) {
            iter.next();
        }
        return ((String) iter.next()).toLowerCase();
    }

    public static boolean isCommonWord(String str) {
        if (str == null)
            return true;
        String stemmed="";
		try {
			stemmed = stemmer.stem(str).toLowerCase();
		} catch (Exception e) {
			//stemming exceptions are not informative, jiust ignore wthis word
			//e.printStackTrace();
		}

        boolean retVal = false;
        if (m_stopHash.containsKey("ENG_DICT"))
            retVal = m_stopHash.get("ENG_DICT").contains(stemmed);
        return retVal;
    }

    public boolean isCommonEventWord(String str) {
        if (str == null)
            return true;
        boolean retVal = false;

        try {
            String stemmed = str.toLowerCase();

            if (m_stopHash.containsKey("fREQUENTEVENTNAMEWORDS"))
                retVal = m_stopHash.get("fREQUENTEVENTNAMEWORDS").contains(
                        stemmed);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return retVal;
    }

    /**
     * Is the given word in the stop words list provided?
     * 
     * @param str
     *            The word to check
     * @param stop_list
     *            the name of the stoplist to check against
     * @return is a stop word
     */
    public static boolean isStopWord(String str, String stop_list) {
        boolean retVal = false;
        if (m_stopHash.containsKey(stop_list))
            retVal = m_stopHash.get(stop_list).contains(str);
        return retVal;
    }

    public boolean isStopWordAll(String str) {
        return isStopWord(str);
    }

    public HashSet<String> getStopListMap(String name) {
        return m_stopHash.get(name);
    }

    public static List<List<String>> preFilterCommonEnglishExpressions(
            List<String> userLikes) {
        List<List<String>> results = new ArrayList<List<String>>();

        List<String> resultUserLikes = new ArrayList<String>(), potentialCategs = new ArrayList<String>();
        if (userLikes.size() < 6) {// too short, do not filter
            results.add(userLikes);
            results.add(potentialCategs);
            return results;

        }

        for (String like : userLikes) {
            like = like.toLowerCase();
            if (!StringUtils.isAlphanumeric(like.replace(" ", ""))) {
                logger.info("removed isAlphanumeric " + like);
                continue;
            }

            if (StringUtils.isNumeric(like)) {
                logger.info("removed isNumericSpace " + like);
                continue;
            }

            if (like.length() < 4) {
                logger.info("removed too short likes " + like);
                continue;
            }
            boolean existFirstName = false, allWordsCommonEnglish = true, bStop = false;
            String[] comps = like.split(" ");
            StringBuffer buf = new StringBuffer();
            for (String word : comps) {
                boolean isCommon = isCommonWord(word);
                boolean isName = isFirstName(word);
                if (!isCommon)
                    allWordsCommonEnglish = false;
                if (isName)
                    existFirstName = true;
                if (isStopWord(word) || word.length() < 3)
                    bStop = true;
                else
                    buf.append(word + " ");
            } // / does not have to include stop word
            if (!existFirstName && allWordsCommonEnglish && comps.length < 3) {
                logger.info("moved to category:  NoFirstName+AllCommonEng+ShorterThan3 "
                        + like);

                continue;
            }
            if (!existFirstName && allWordsCommonEnglish && comps.length == 1) {
                logger.info("moved to category: NoFirstName+AllCommonEng+Short1word "
                        + like);
                potentialCategs.add(like);
                continue;
            }

            if (existFirstName && comps.length == 1) {
                logger.info("removed : only first name, no last name " + like);

                continue;
            }

            resultUserLikes.add(buf.toString().trim());

        }

        resultUserLikes = new ArrayList<String>(new HashSet<String>(
                resultUserLikes));
        if (resultUserLikes.size() > 1) {
            results.add(resultUserLikes);
            results.add(potentialCategs);
            return results;
        }

        else {// do not do reduction
            results.add(userLikes);
            results.add(potentialCategs);
            return results;
        }
    }

    public static boolean isAcceptableIndividualLikes(String like) {
        StopList finder = StopList.getInstance();
        like = like.toLowerCase();
        if (!StringUtils.isAlphanumeric(like.replace(" ", ""))) {
            logger.info("removed isAlphanumeric " + like);
            return false;
        }

        if (StringUtils.isNumeric(like)) {
            logger.info("removed isNumericSpace " + like);
            return false;
        }

        if (like.length() < 4) {
            logger.info("removed too short likes " + like);
            return false;
        }
        boolean existFirstName = false, allWordsCommonEnglish = true, bStop = false;
        String[] comps = like.split(" ");
        StringBuffer buf = new StringBuffer();
        for (String word : comps) {
            boolean isCommon = finder.isCommonWord(word);
            boolean isName = finder.isFirstName(word);
            if (!isCommon)
                allWordsCommonEnglish = false;
            if (isName)
                existFirstName = true;
            if (finder.isStopWord(word) || word.length() < 3)
                bStop = true;
            else
                buf.append(word + " ");
        } // / does not have to include stop word
        if (!existFirstName && allWordsCommonEnglish && comps.length < 3) {
            logger.info("  NoFirstName+AllCommonEng+ShorterThan3 " + like);

            return false;
        }
        if (!existFirstName && allWordsCommonEnglish && comps.length == 1) {
            logger.info(" NoFirstName+AllCommonEng+Short1word " + like);

            return false;
        }

        if (existFirstName && comps.length == 1) {
            logger.info("removed : only first name, no last name " + like);

            return false;
        }

        return true;
    }

    @SuppressWarnings("all")
    public static void main(String[] args) {

        StopList list = StopList
                .getInstance("/Users/borisgalitsky/Documents/workspace/opennlp-similarity/src/test/resources/");
        Boolean b = list.isCommonWord("demonstration");

        String fname = list.getRandomFirstName();

        b = list.isCommonEventWord("tour");
        b = list.isCommonEventWord("dance");
        b = list.isCommonEventWord("salsa");
        b = list.isCommonEventWord("center");
        b = list.isCommonEventWord("family");

      

        b = isAcceptableIndividualLikes("forest glen");
        b = isAcceptableIndividualLikes("drive");
        b = isAcceptableIndividualLikes("house");
        b = isAcceptableIndividualLikes("Timothy Kloug");
        b = isAcceptableIndividualLikes("Mamma Mia");

    }
}
