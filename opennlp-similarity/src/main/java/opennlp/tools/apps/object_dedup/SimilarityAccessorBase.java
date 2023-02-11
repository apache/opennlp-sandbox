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

package opennlp.tools.apps.object_dedup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.LevensteinDistanceFinder;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* This is a template class for deduplicator */

public class SimilarityAccessorBase {
	private static final Logger LOG = LoggerFactory.getLogger(SimilarityAccessorBase.class);

	public static final int MAX_EV_TO_RECOMM = 6;

	private List<String> namesBothSides;

	protected static final String[] englishPrepositions = new String[] { "a", "aboard", "about", "above", "absent",
		"across", "after", "against", "along", "alongside", "among", "around", "as", "at", "before", "behind", "below",
		"beneath", "between", "beyond", "but", "by", "despite", "down", "during", "except", "excluding", "failing",
		"following", "for", "from", "in", "including", "inside", "into", "like", "near", "next", "of", "off", "on",
		"onto", "only", "opposite", "out", "outside", "over", "pace", "past", "per", "since", "than", "through", "and",
		"thru", "till", "to", "toward", "under", "up", "upon", "versus", "with", "within", "you", "must", "know",
		"when" };

	protected List<String> commonWordsInEventTitles = Arrays.asList(new String[] { "community", "party", "film",
		"music", "exhibition", "kareoke", "guitar", "quartet", "reggae", "r&b", "band", "dj ", "piano", "pray",
		"worship", "god", "training", "class", "development", "training", "class", "course", "our", "comedy", ",fun",
		"musical", "group", "alliance", "session", "feeding", "introduction", "school", "conversation", "learning",
		"nursery", "unity", "trivia", "chat", "conference", "tuition", "technology", "teen", "communication",
		"reception", "management", "beginner", "beginning", "collabora", "reuninon", "political", "course", "age",
		"ages", "through", "grade", "networking", "workshop", "demonstration", "tuning", "program", "summit",
		"convention", "day", "night", "one", "two", "outfest", "three", "online", "writing", "seminar", "coach",
		",expo", "advanced", "beginner", "intermediate", "earn", "free", "ii", "iii", "skills", "skill", "artist",
		"summer", "winter", "autumn", "spring", "camp", "vacation", "miscrosoft", "kid", "child", "kids", "children",
		"every", "everyone", "dancer", "dancers", "senior", "seniors", "basic", "elementary", "outfest", "2008",
		"2009", "2010", "2011", "2012", "monday", "tuesday", "wednesday", "thirsday", "friday", "saturday", "sunday",
		"mondays", "tuesdays", "wednesdays", "thirsdays", "fridays", "saturdays", "sundays", "men" // ?
	});

	private final BingQueryRunner webSearch = new BingQueryRunner();

	private final StringDistanceMeasurer stringDistanceMeasurer = new StringDistanceMeasurer();


	public SimilarityAccessorBase() {
	}


	public void init()
	{
		namesBothSides = getWordsThatShouldBeOnBothSidesEvents();
	}

	protected List<String> removeDollarWordAndNonAlphaFromList(List<String> list)
	{
		List<String> result = new ArrayList<String>();
		Pattern p = Pattern.compile("^\\$(\\d{1,3}(\\,\\d{3})*|(\\d+))(\\.\\d{2})?$");
		for (String w : list)
		{
			if (!(p.matcher(w).find()) && StringUtils.isAlphanumeric(w) && (w.length() >= 3 || !StringUtils.isAlpha(w)))
				result.add(w);
		}
		return result;
	}


	public List<String> getWordsThatShouldBeOnBothSidesEvents()
	{
/*
		names.addAll(Arrays.asList(new String[] { "woman", "man", "women", "men", "womans", "mans", "womens", "mens",
			"boy", "girl", "boys", "girls", "men's", "women's", "woman's", "ice", // for disney
			"flight", "intermediate", "advanced", "beginner",
			// "tour", TODO special consideration
			"helicopter", "sexual", "junior", "jr" }));
			*/
		return null;

	}

	protected Boolean applySemanticNameSimilarityRule(Object es1, Object es2) {
		
		//TODO check attributes of objects
		/*
		if (!(es1.getVenueName().endsWith(es2.getVenueName()) || es2.getVenueName().endsWith(es1.getVenueName())))
			return false;
		if (Math.abs(es1.getStarttime().getTime() - es2.getStarttime().getTime()) > 100000)
			return false;
			*/

		return true;

	}

	// this rule extract "OF" part and treats it as a whole expression
	protected void applySubPhraseExtractionRule(List<String> name1Tokens, List<String> name2Tokens)
	{
		if (name1Tokens.indexOf("of") > 0 && name2Tokens.indexOf("of") > 0)
		{
			name1Tokens = extractMainNounPhrase(name1Tokens);
			name2Tokens = extractMainNounPhrase(name2Tokens);
		}
	}

	private Boolean attemptShortTitlesSimilarityInWebSpace(String name1, String name2)
	{

		// first delimeter processing
		String name1v = name1.replace("'", "").replace("-", " ");
		String name2v = name2.replace("'", "").replace("-", " ");
		String name1vv = name1.replace("'", "");
		String name2vv = name2.replace("'", "");
		String name1vvv = name1.replace("-", " ");
		String name2vvv = name2.replace("-", " ");

		if (name1.startsWith(name2) || name1vv.startsWith(name2) || name1.startsWith(name2v)
			|| name1.startsWith(name2vv) || name1.startsWith(name2vvv) || name1v.startsWith(name2v)
			|| name1v.startsWith(name2vv) || name2.startsWith(name1) || name2vv.startsWith(name1)
			|| name2.startsWith(name1v) || name2vvv.startsWith(name1vv) || name2.startsWith(name1vvv)
			|| name2v.startsWith(name1v) || name2v.startsWith(name1vv) || name1.endsWith(name2)
			|| name1vv.endsWith(name2) || name1.endsWith(name2v) || name1.endsWith(name2vv) || name1.endsWith(name2vvv)
			|| name1v.endsWith(name2v) || name1v.endsWith(name2vv) || name2.endsWith(name1) || name2vv.endsWith(name1)
			|| name2.endsWith(name1v) || name1vvv.endsWith(name2vv) || name2.endsWith(name1vvv)
			|| name2v.endsWith(name1v) || name2v.endsWith(name1vv))
		{
			LOG.info("Found fuzzy substring of name1 and name2");
			return true;
		}
		if (name1.length() > 12 && name2.length() > 12)
			return false;

		return areNamesSemanticallyCloseInWebSearchSpace(name1, name2, 0.8f, false).isDecision();

	}

	public Boolean applyBothSidesRuleEvent(String name1, String name2)
	{
		List<String> name1Tokens = TextProcessor.fastTokenize(name1.toLowerCase(), false);
		List<String> name2Tokens = TextProcessor.fastTokenize(name2.toLowerCase(), false);
		// get unique names
		List<String> name1TokensC = new ArrayList<String>(name1Tokens), name2TokensC = new ArrayList<String>(
			name2Tokens);
		;
		name1TokensC.removeAll(name2Tokens);
		name2TokensC.removeAll(name1Tokens);
		// get all unique names
		name1TokensC.addAll(name2TokensC);

		name1TokensC.retainAll(namesBothSides);
		name1Tokens.retainAll(name2Tokens);

		if ((name1TokensC.size() > 0 && name1Tokens.size() < 3) || (name1TokensC.size() > 1 && name1Tokens.size() < 5))
		{ // 'mens == men; case !(name1TokensC.size()==2 && (name1TokensC.get(0).indexOf(name1TokensC.get(1))>-1 ||
			// name1TokensC.get(1).indexOf(name1TokensC.get(0))>-1 ))){
			LOG.info("Found required common word present on one side and not on the other: " + name1TokensC.toString()
				+ " and less than 3 keywords overlap (or >1 common words and less than 5 overl");
			return false;
		}
		else
			return true;
	}

	protected List<String> tokenizeAndStem(String input)
	{

		List<String> results = new ArrayList<String>();
		List<String> toks = TextProcessor.fastTokenize(input.toLowerCase(), false);
		for (String word : toks)
		{
			try
			{
				if (word.equals("theatre"))
					word = "theater";
				results.add(word);
			}
			catch (Exception e)
			{
				results.add(word);
			}
		}
		return results;
	}

	protected List<String> stemList(List<String> toks)
	{

		List<String> results = new ArrayList<String>();
		for (String word : toks)
		{
			try
			{
				if (word.equals("theatre"))
					word = "theater";
				results.add(word);
			}
			catch (Exception e)
			{
				results.add(word);
			}
		}
		return results;
	}

	public List<String> removeVenuePart(ArrayList<String> toks)
	{
		List<String> results = new ArrayList<String>();
		boolean bVenuePart = false;
		for (String word : toks)
		{
			// beginning of venue part
			if (word.equals("at") || word.equals("@"))
				bVenuePart = true;
			// end of venue part
			if (!StringUtils.isAlphanumeric(word) || word.startsWith("<punc"))
				bVenuePart = false;

			if (!bVenuePart && !word.startsWith("<punc"))
				results.add(word);

		}
		return results;
	}

	protected boolean isCapitalized(String lookup)
	{
		String[] titleWords = lookup.split(" ");
		int count = 0;
		for (String word : titleWords)
		{
			if (word.length() < 2) // '-', '|', ':'
				break;

			if (word.equals(word.toLowerCase()) && (!Arrays.asList(englishPrepositions).contains(word))
				&& word.length() > 3 && StringUtils.isAlphanumeric(word))
				continue; // was return false;
			if (count > 3)
				break;
			count++;
		}
		return true;
	}

	protected List<String> extractMainNounPhrase(List<String> name1Tokens)
	{
		List<String> results = new ArrayList<String>();
		int ofPos = name1Tokens.indexOf("of");
		List<String> ofList = name1Tokens.subList(ofPos + 1, name1Tokens.size() - 1);
		// now iterate till next preposition towards the end of noun phrase
		for (String preposCand : ofList)
		{
			if (Arrays.asList(englishPrepositions).contains(preposCand))
				break;
			results.add(preposCand);
		}
		return results;

	}

	public boolean verifyEventAttributesPost(List<String> name1Tokens, List<String> name2Tokens)
	{
		String[] attributeNamesPost = { "age", "ages", "game", "games", "grade", "grades", "level", "levels", "vs",
			"vs.", "versus", "pottery", "competition", "contest", "skill", "skills", "day", "only", "basic", "class",
			"completed",
			// "tour", ?
			"advanced", "beginner", "intermediate", "flight", "workshop", "latin", "adobe", "ballet", "dinner",
			"breakfast", "lunch", "summer", // "canyon"
			"tfestival", "festival", "mfestival" };
		try
		{
			for (String attr : attributeNamesPost)
			{

				int agePos1 = name1Tokens.indexOf(attr);
				int agePos2 = name2Tokens.indexOf(attr);
				if (agePos1 > -1 && agePos2 > -1 && agePos1 < name1Tokens.size() - 1
					&& agePos2 < name2Tokens.size() - 1)
				{
					double dist = LevensteinDistanceFinder.levensteinDistance(name1Tokens.get(agePos1 + 1),
						name2Tokens.get(agePos2 + 1), 1, 10, 1, 10);
					if (!name1Tokens.get(agePos1 + 1).equalsIgnoreCase(name2Tokens.get(agePos2 + 1))
						&& (dist > 2.99 || name1Tokens.get(agePos1 + 1).length() < 4))
					{
						LOG.info("Found disagreement in the attrib value for " + attr + " value = "
							+ name1Tokens.get(agePos1 + 1) + " <=> " + name2Tokens.get(agePos2 + 1));
						return false;
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}

	public boolean verifyEventAttributesPre(List<String> name1Tokens, List<String> name2Tokens)
	{

		String[] attributeNamesPre = { "hour", "vs", "vs.", "versus", "pottery", "program", "day", "only",
			// dance styles followed by a param
			"swing", "rumba", "samba", "doble",
			"violence", //
			// "level",
			"class", "classes", "kid", "kids", "test", "west", "summer_camp", "session", "tfestival", "festival",
			"mfestival" };
		try
		{
			for (String attr : attributeNamesPre)
			{
				int agePos1 = name1Tokens.indexOf(attr);
				int agePos2 = name2Tokens.indexOf(attr);
				if (agePos1 > 0 && agePos2 > 0)
				{ // not the first word is attr name
					if (!name1Tokens.get(agePos1 - 1).equalsIgnoreCase(name2Tokens.get(agePos2 - 1))
						&& (agePos1 < 2 || !name1Tokens.get(agePos1 - 2).equalsIgnoreCase(name2Tokens.get(agePos2 - 1)))
						&&
						// ((agePos1<2 && agePos2 <2) || !name1Tokens.get(agePos1 -
						// 2).equalsIgnoreCase(name2Tokens.get(agePos2 - 2 ))) &&
						(agePos2 < 2 || !name1Tokens.get(agePos1 - 1).equalsIgnoreCase(name2Tokens.get(agePos2 - 2)))

					)
					{
						LOG.info("Found disagreement in the attrib value for " + attr + " value = "
							+ name1Tokens.get(agePos1 - 1) + " and " + name2Tokens.get(agePos2 - 1));
						return false;
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}

	protected boolean bDifferentGroupOneSubnameOfAnother(String name1, String name2)
	{
		// first check a special case that both name1 and name2 are DIFFERENT groups at last.fm
		Map<String, Integer> map1 = null; //LastFM_APIManager.extractTagsForArtist(name1);
		Map<String, Integer> map2 = null; //LastFM_APIManager.extractTagsForArtist(name2);
		if (map1 != null && map2 != null && map1.size() > 0 && map2.size() > 0)
			map1.entrySet().removeAll(map2.entrySet());
		if (map1.size() > 0) // same or subset of tags => different groups
			return true;

		return false;
	}

	public boolean applyBothSidesRule(String name1, String name2)
	{
		List<String> name1Tokens = TextProcessor.fastTokenize(name1.toLowerCase(), false);
		List<String> name2Tokens = TextProcessor.fastTokenize(name2.toLowerCase(), false);
		// get unique names
		List<String> name1TokensC = new ArrayList<String>(name1Tokens), name2TokensC = new ArrayList<String>(
			name2Tokens);
		;
		name1TokensC.removeAll(name2Tokens);
		name2TokensC.removeAll(name1Tokens);
		// get all unique names
		name1TokensC.addAll(name2TokensC);

		name1TokensC.retainAll(namesBothSides);
		if (name1TokensC.size() > 0)
			return false;
		else
			return true;
	}

	private boolean succeededMenWomenSportsRule(String name1, String name2)
	{
		List<String> name1Tokens = TextProcessor.fastTokenize(name1.toLowerCase(), false);
		List<String> name2Tokens = TextProcessor.fastTokenize(name2.toLowerCase(), false);
		if (name1Tokens.contains("men") || name2Tokens.contains("men") || name1Tokens.contains("women")
			|| name2Tokens.contains("women") || name1Tokens.contains("disney") || name2Tokens.contains("disney"))
		{ // all words should be the
			// same
			name1Tokens.removeAll(name2Tokens);
			name1Tokens.removeAll(Arrays.asList(englishPrepositions));
			name1Tokens.removeAll(Arrays.asList(commonWordsInEventTitles));
			if (name1Tokens.size() < 1)
				return true;

			return false;
		}
		else
			return true;

	}

	private boolean succeededSpecialGroupsSymphoniesRule(String name1, String name2)
	{
		List<String> name1Tokens = TextProcessor.fastTokenize(name1.toLowerCase(), false);
		List<String> name2Tokens = TextProcessor.fastTokenize(name2.toLowerCase(), false);
		if (name1Tokens.contains("orchestra") || name2Tokens.contains("symphony") || name2Tokens.contains("orchestra")
			|| name1Tokens.contains("symphony") || name2Tokens.contains("band") || name1Tokens.contains("band")
			|| name2Tokens.contains("trio") || name1Tokens.contains("trio") || name1Tokens.contains("soleil")
			|| name2Tokens.contains("soleil") || name1Tokens.contains("disney") || name2Tokens.contains("disney")
			|| name1Tokens.contains("lang") || name2Tokens.contains("lang")) // special group 'lang lang'
		{ // all words should be the
			// same
			List<String> name1TokensClone = new ArrayList<String>(name1Tokens);
			name1Tokens.removeAll(name2Tokens);
			name2Tokens.removeAll(name1TokensClone);
			name1Tokens.addAll(name2Tokens);
			name1Tokens.removeAll(Arrays.asList(this.englishPrepositions));
			// name1Tokens.removeAll(Arrays.asList(this.commonWordsInEventTitles));
			if (name1Tokens.size() < 1)
				return true;

			return false;
		}
		else
			return true;

	}

	public int getAttemptedNameMerge(String name1, String name2)
	{
		name1 = name1.replaceAll("[a-z][A-Z]", "$0&$0").replaceAll(".&.", " ");
		; // suspected word merge if higher case is in the middle of word
		name2 = name2.replaceAll("[a-z][A-Z]", "$0&$0").replaceAll(".&.", " ");

		name1 = name1.toLowerCase();
		name2 = name2.toLowerCase();
		if (name1.equals(name2) || name1.startsWith(name2) || name2.startsWith(name1) || name1.endsWith(name2)
			|| name1.endsWith(name2) || name1.indexOf(name2) > -1 || name1.indexOf(name2) > -1) // ??
			return 2;
		String name2r = name2.replace(" ", "");
		if (name1.equals(name2r) || name1.startsWith(name2r) || name1.startsWith(name2r) || name1.endsWith(name2r)
			|| name1.endsWith(name2r))
			return 1;
		String name1r = name1.replace(" ", "");
		if (name1r.equals(name2r) || name1r.startsWith(name2r) || name1r.startsWith(name2) || name1r.endsWith(name2r)
			|| name1r.endsWith(name2r) || name2r.equals(name1r) || name2r.startsWith(name1r)
			|| name2r.startsWith(name1) || name2r.endsWith(name1r) || name2r.endsWith(name2)

		)
			return 1;

		if (stringDistanceMeasurer.measureStringDistance(name1, name2) > 0.95)
			return 2;
		if (stringDistanceMeasurer.measureStringDistance(name1, name2) > 0.70)
			return 1;
		return 0;
	}

	private String normalizeGenderAndOtherAttributes(String name1)
	{
		name1 = Utils.convertToASCII(name1.replace("/", " ").replace("w/", "with ")).replace('!', ' ').toLowerCase();

		name1 = name1.replace("woman", "women").replace("womans", "women").replace("womens", "women")
			.replace("women's", "women").replace("woman's", "women");
		name1 = name1.replace(" man ", " men ").replace(" mans ", " men ").replace(" men's ", " men ")
			.replace(" man's ", " men ").replace(" mens ", " men ").replace("summer camp", "summer_camp")
			.replace("gaea theatre festival", "tfestival"); // need regexp for this
		return name1;
	}

	/*
	 * Main semantic similarity function which applies boundary cases rule and focus on web mining rule The main
	 * criteria for a commonality between titles: to form an entity, searchable on the web
	 */
	public DedupResult areNamesSemanticallyCloseWebMineCommonPart(String name1, String name2, String venue)
	{
		// normalize gender
		name1 = normalizeGenderAndOtherAttributes(name1);
		name2 = normalizeGenderAndOtherAttributes(name2);

		Boolean bShortTitlesSimilarInWebSpace = attemptShortTitlesSimilarityInWebSpace(name1, name2);
		if (bShortTitlesSimilarInWebSpace)
			return new DedupResult("Accepted as short title by web mining", 2, true);

		StringBuffer reason = new StringBuffer();
		List<String> venueToks = removeVenuePart(TextProcessor.fastTokenize(venue.toLowerCase(), false));

		LOG.info("\nComputing similarity between name = '" + name1 + "' and name = '" + name2 + "'");
		// convert titles into token lists
		List<String> name1Tokens = removeVenuePart(TextProcessor.fastTokenize(name1.toLowerCase(), true));
		List<String> name2Tokens = removeVenuePart(TextProcessor.fastTokenize(name2.toLowerCase(), true));
		// applySubPhraseExtractionRule()
		Boolean bSameAttrib = verifyEventAttributesPost(name1Tokens, name2Tokens)
			&& verifyEventAttributesPre(name1Tokens, name2Tokens);
		if (!bSameAttrib)
		{
			LOG.info("similar events but different attributes");
			return new DedupResult("similar events but different attributes", 0, false);
		}

		boolean bothSodesSuccess = applyBothSidesRuleEvent(name1, name2);
		if (!bothSodesSuccess)
		{
			return new DedupResult("Failed common words test for sports", 0, false);
		}

		float dist = (float) LevensteinDistanceFinder.levensteinDistance(name1, name2, 1, 10, 1, 10);
		if (dist < 5.1)
		{
			LOG.info("Found low LevensteinDistance for name1 and name2");
			return new DedupResult("Found low LevensteinDistance", 2, true);
		}

		int nameMergeScore = getAttemptedNameMerge(name1, name2);
		if (nameMergeScore > 0)
		{
			LOG.info("Found low NameMerge Distance for name1 and name2");
			return new DedupResult("Found low  NameMerge Distance", 2, true);
		}

		// todo take into account order
		// form common sub-list of tokens
		name1Tokens.retainAll(name2Tokens);
		name1Tokens.removeAll(venueToks);

		name1Tokens.removeAll(commonWordsInEventTitles);
		name1Tokens.removeAll(Arrays.asList(englishPrepositions));
		name1Tokens = removeDollarWordAndNonAlphaFromList(name1Tokens);
		// todo : to use full string measure
		// boundary case: too many words => just do counts
		float commonPortion = (float) name1Tokens.size() / (float) name2Tokens.size();
		if (commonPortion > 0.8 || name1Tokens.size() >= 4)
		{ // after typical
			// title words
			// are revomed 4
			// looks OK
			LOG.info("Accepted since substantial common part");
			return new DedupResult("Accepted since substantial common part", Math.max((int) (commonPortion * 5.0), 2),
				true);
		}
		// boundary case: no overlap
		if (name1Tokens.size() < 1)
		{
			LOG.info("Rejected since nothing in common");
			return new DedupResult("Rejected since nothing in common", 0, false);
		}
		// get from list of tokens back to words to get search expression
		String entityExpression = name1Tokens.toString().replace('[', ' ').replace(']', ' ').replace(',', ' ')
			.replace("  ", " ").trim();
		/*
		 * // now try name merge reduced strings String entityExpression1 = name1TokensC.toString().replace('[',
		 * ' ').replace(']', ' ').replace(',', ' ') .replace("  ", " ").trim(); String entityExpression2 =
		 * name2Tokens.toString().replace('[', ' ').replace(']', ' ').replace(',', ' ') .replace("  ", " ").trim();
		 * 
		 * nameMergeScore = getAttemptedNameMerge(entityExpression1, entityExpression2); if (nameMergeScore>0){
		 * LOG.info("Found low NameMerge Distance for REDUCED name1 and name2"); return new
		 * DedupResult("Found low  NameMerge Distance REDUCED", 2, true);
		 * 
		 * }
		 */

		// Before doing web mining, make sure overlap between titles is NOT a
		// set of common english words (use the vocabulary)
		// if all words are common, then NOT an entity
		if (name1Tokens.size() < 2)
		{
			boolean bCommonEnglishWord = false;
			for (String word : name1Tokens)
			{
	//			if (stopList.isCommonWord(word) /*&& mostFrequent1000Words.isMostFrequent1000Word(word)*/)
	//				bCommonEnglishWord = true;
			}

			if (bCommonEnglishWord)
			{
				LOG.info("Rejected common entity: common word = " + entityExpression);
				return new DedupResult("Rejected since common entity is common English word = " + entityExpression, 0,
					false);
			}
		}
		// accept common expression
		LOG.info("Formed common entity = " + entityExpression);
		reason.append("Formed common entity = ").append(entityExpression).append("\n");
		// now go to the web / bing api with this common expression
		List<HitBase> searchResult = webSearch.runSearch(entityExpression);
		float entityScore = 0f;
		if (searchResult != null)
		{
			int count = 0;
			for (HitBase item : searchResult)
			{
				String lookup = item.getTitle();
				LOG.info("Bing hit title = '" + lookup + "'");
				reason.append("Bing hit title = '").append(lookup).append("'\n");
				if (count > 4)
					break;
				count++;
				// if occurrence is not capitalized then rejected, do not take
				// into account in score
				if (!isCapitalized(lookup))
				{
					LOG.info("Rejected hit title since not capitalized");
					reason.append("Rejected hit title since not capitalized\n");
					continue;
				}

				/*
				 * if (lookup.indexOf('-')>0 ){ lookup = lookup.split("-")[0]; }
				 */
				// now compute overlap between what found on the web for hit's
				// title and the common expression between events
				List<String> lookupTokens = tokenizeAndStem(lookup);
				lookupTokens.retainAll(stemList(name1Tokens));
				if (lookupTokens.size() >= name1Tokens.size())
					// increment score if found hit title is acceptable
					entityScore += 1.0;
				else
				{
					LOG.info("Found hit title " + lookupTokens + " does not cover comonality expr = " + name1Tokens);
					entityScore += 0.25;

				}

			}
		}
		return new DedupResult(reason.toString(), (int) entityScore, entityScore > 1.0);
	}

	public DedupResult areNamesSemanticallyCloseInWebSearchSpace(String name1, String name2, Float thresh, boolean bStem)
	{

		if (thresh == null || thresh == 0f)
		{
			thresh = 0.8f;
		}

		// normalize gender
		name1 = normalizeGenderAndOtherAttributes(name1);
		name2 = normalizeGenderAndOtherAttributes(name2);

		StringBuffer reason = new StringBuffer();

		boolean bSportsOrOrchestra = !succeededMenWomenSportsRule(name1, name2);
		if (bSportsOrOrchestra)
			return new DedupResult("Sports rule: different teams or teams of different venues", 0, false);

		bSportsOrOrchestra = !succeededSpecialGroupsSymphoniesRule(name1, name2);
		if (bSportsOrOrchestra)
			return new DedupResult("SpecialGroupsSymphoniesRule: different circus/band", 0, false);

		LOG.info("\nComputing similarity between name = '" + name1 + "' and name = '" + name2 + "'");

		List<String> name1Tokens = TextProcessor.fastTokenize(name1.toLowerCase(), true);
		List<String> name2Tokens = TextProcessor.fastTokenize(name2.toLowerCase(), true);
		Boolean bSameAttrib = verifyEventAttributesPost(name1Tokens, name2Tokens)
			&& verifyEventAttributesPre(name1Tokens, name2Tokens);
		if (!bSameAttrib)
		{
			LOG.info("similar events but different attributes");
			return new DedupResult("similar events but different attributes", 0, false);
		}

		List<HitBase> searchResult1 = webSearch.runSearch(name1);
		List<HitBase> searchResult2 = webSearch.runSearch(name2);
		int score = 0;
		if (searchResult1 != null && searchResult2 != null)
		{
			for (HitBase item1 : searchResult1)
			{
				if (item1.getUrl().indexOf("myspace") > -1 || item1.getUrl().indexOf("wiki") > -1)
					continue;
				for (HitBase item2 : searchResult2)
				{
					String lookup1 = item1.getTitle().replace("Facebook", "").replace("LinkedIn", "")
						.replace("MySpace", "");
					String lookup2 = item2.getTitle().replace("Facebook", "").replace("LinkedIn", "")
						.replace("MySpace", "");
					double d = 0;
					if (bStem)
						d = stringDistanceMeasurer.measureStringDistance(lookup1, lookup2);
					else
						d = stringDistanceMeasurer.measureStringDistanceNoStemming(lookup1, lookup2);
					if (d > thresh) // 0.8)
					{

						reason.append("Found common search result title for group names '").append(lookup1).append(" < > ").append(lookup2).append(" sim = ").append(d).append("\n");
						LOG.info(("Found common search result title for group names '" + lookup1 + " < > " + lookup2
							+ " sim = " + d));
						score++;
					}

				}
			}
		}

		Boolean bothSidesSuccess = applyBothSidesRule(name1, name2);
		if (!bothSidesSuccess)
		{
			score = 1;
			reason.append("Failed common words test for sports");
		}
		if (score > 0)
		{
			Boolean bDifferentGroup = bDifferentGroupOneSubnameOfAnother(name1, name2);
			if (bDifferentGroup)
			{
				score = 1;
				reason.append("Failed common words test for sports");
			}
		}
		return new DedupResult(reason.toString(), score, score > 1);
	}
}
