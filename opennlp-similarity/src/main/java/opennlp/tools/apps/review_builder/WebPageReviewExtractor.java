package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.apps.WebPageExtractor;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.StringUtils;

public class WebPageReviewExtractor extends WebPageExtractor {
	
	private final BingAPIProductSearchManager prodman = new BingAPIProductSearchManager();
	private final SentenceOriginalizer orig;
		
	public WebPageReviewExtractor(String resourceDir) {
		orig = new SentenceOriginalizer(resourceDir);
	}

	public String[] removeDuplicates(String[] hits)
	{
		StringDistanceMeasurer meas = new StringDistanceMeasurer();

		List<Integer> idsToRemove = new ArrayList<>();
		List<String> hitsDedup = new ArrayList<>();
		try {
			for (int i = 0; i < hits.length; i++)
				for (int j = i + 1; j < hits.length; j++)
				{
					String title1 = hits[i];
					String title2 = hits[j];
					if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
						continue;
					if (meas.measureStringDistance(title1, title2) > 0.7)
					{
						idsToRemove.add(j); // dupes found, later list member to
											// be deleted
					}
				}
			for (int i = 0; i < hits.length; i++)
				if (!idsToRemove.contains(i))
					hitsDedup.add(hits[i]);
			if (hitsDedup.size() < hits.length) {
				System.out.println("Removed duplicates from relevant search results, including "
					+ hits[idsToRemove.get(0)]);
			}
		}
		catch (Exception e) {
			System.out.println("Problem removing duplicates from relevant images");
		}

		return hitsDedup.toArray(new String[0]);

	}

	public ReviewObj extractSentencesWithPotentialReviewPhrases(String url) {
		ReviewObj reviewObj = new ReviewObj();
		int maxSentsFromPage= 20;
		List<String[]> results = new ArrayList<String[]>();

		String downloadedPage = pageFetcher.fetchPage(url, 20000);
		if (downloadedPage == null || downloadedPage.length() < 100)
		{
			return null;
		}

		String pageOrigHTML = pageFetcher.fetchOrigHTML(url);

		List<String> productFeaturesList = new ArrayList<String> ();
		String[] productFeatures = StringUtils.substringsBetween(pageOrigHTML, "<li>", "</li>" );
		if (productFeatures!=null){
			for(String item: productFeatures ){
				if (item.contains("class") || item.contains("www.") || item.contains("href"))
					continue;
				item = item.replace("<span>","").replace("</span>","").replace("<b>","").replace("</b>","");
				if (item.length()>80 && MinedSentenceProcessor.acceptableMinedSentence(item)==null){
					// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
					// System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+item);
					continue;
				}
				productFeaturesList .add(item);
			}
		}
		
		productFeaturesList = cleanProductFeatures(productFeaturesList);
		
		String startArea = StringUtils.substringBetween(pageOrigHTML, "reviewHistoPop", "t of 5 stars");
		String item =  StringUtils.substringBetween(startArea, "title=\"","ou" );
		if (item==null){//title="4.0 out of 5 stars" ><span>4.0 out of 5 stars</span>
			int index = pageOrigHTML.indexOf("of 5 stars\"");
			startArea = StringUtils.substringBetween(pageOrigHTML, "of 5 stars\"", "of 5 stars");
			item =  StringUtils.substringBetween(startArea, "<span>","ou" );
		}

		// if found, process
		if (item!=null){
			try {
				float rating = Float.parseFloat(item);
				reviewObj.setRating(rating);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		//productFeaturesList .add(item);

		downloadedPage= downloadedPage.replace("     ", "&");
		downloadedPage = downloadedPage.replaceAll("(?:&)+", "#");
		String[] sents = downloadedPage.split("#");
		List<TextChunk> sentsList = new ArrayList<TextChunk>();
		for(String s: sents){
			s = s.trim().replace("  ", ". ").replace("..", ".").replace(". . .", " ")
					.replace(": ", ". ").replace("- ", ". ").
					replace (". .",".").trim();
			sentsList.add(new TextChunk(s, s.length()));
		}

		sentsList.sort(new TextChunkComparable());
		String[] longestSents = new String[maxSentsFromPage];
		int j=0;														// -1 removed
		for(int i=sentsList.size()-1 -maxSentsFromPage; i< sentsList.size()&& j<longestSents.length; i++){
			longestSents[j] = sentsList.get(i).text;
			j++;
		}

		sents = cleanListOfSents(longestSents);
		sents = removeDuplicates(sents);
		sents = verifyEnforceStartsUpperCase(sents);

		reviewObj.setFeaturePhrases(productFeaturesList.toArray(new String[0]));
		reviewObj.setOrigSentences(sents);

		return reviewObj;
	}

	private String[] verifyEnforceStartsUpperCase(String[] sents) {
		for(int i=0; i<sents.length; i++){
			String s = sents[i];
			s = StringUtils.trim(s);
			String sFirstChar = s.substring(0, 1);
			if (!sFirstChar.toUpperCase().equals(sFirstChar)){
				s = sFirstChar.toUpperCase()+s.substring(1);
			}
			sents[i] = s;
		}
			return sents;
	}

	private List<String> cleanProductFeatures(List<String> productFeaturesList) {
		List<String> results = new ArrayList<>();
		for(String feature: productFeaturesList){
			if (feature.startsWith("Unlimited Free") || feature.startsWith("View Larger") || feature.startsWith("View Larger") || feature.indexOf("shipping")>0)
				continue;
			results.add(feature);
		}
		return results;
	}

	protected String[] cleanListOfSents(String[] longestSents)
	{
		float minFragmentLength = 40, minFragmentLengthSpace=4;

		List<String> sentsClean = new ArrayList<>();
		for (String sentenceOrMultSent : longestSents) {
			if (MinedSentenceProcessor.acceptableMinedSentence(sentenceOrMultSent)==null){
				// System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+sentenceOrMultSent);
				continue;
			}
			// aaa. hhh hhh.  kkk . kkk ll hhh. lll kkk n.
			int numOfDots = sentenceOrMultSent.replace('.','&').split("&").length;
			float avgSentenceLengthInTextPortion = (float)sentenceOrMultSent.length() /(float) numOfDots;
			if ( avgSentenceLengthInTextPortion<minFragmentLength)
				continue;
			// o oo o ooo o o o ooo oo ooo o o oo
			numOfDots = sentenceOrMultSent.replace(' ','&').split("&").length;
			avgSentenceLengthInTextPortion = (float)sentenceOrMultSent.length() /(float) numOfDots;
			if ( avgSentenceLengthInTextPortion<minFragmentLengthSpace)
				continue;

			List<String> furtherSplit = TextProcessor.splitToSentences(sentenceOrMultSent);
			
			// forced split by ',' somewhere in the middle of sentence
			// disused - Feb 26 13
			//furtherSplit = furtherMakeSentencesShorter(furtherSplit);
			furtherSplit.remove(furtherSplit.size()-1);
			for(String s : furtherSplit){
				if (s.indexOf('|')>-1)
					continue;
				s = s.replace("<em>"," ").replace("</em>"," ");
				s = Utils.convertToASCII(s);
				sentsClean.add(s);
			}
		}

		return sentsClean.toArray(new String[0]);
	}

	private List<String> furtherMakeSentencesShorter(List<String> furtherSplit) {
		int MIN_LENGTH_TO_SPLIT = 80;
		List<String> results = new ArrayList<>();
		for(String sent: furtherSplit) {
			sent = startWithCapitalSent(sent);
			int len = sent.length(); 
			if (len <MIN_LENGTH_TO_SPLIT)
				results.add(sent);
			else {
				try {
					int commaIndex = StringUtils.indexOf(sent, ',');
					int lastCommaIndex = StringUtils.lastIndexOf(sent, ',');
					int splitIndex = -1;
					if (Math.abs(commaIndex- len/2) > Math.abs(lastCommaIndex- len/2))
						splitIndex = commaIndex;
					else
						splitIndex = lastCommaIndex;
					if (splitIndex<0)
						results.add(sent);
					else {
						String sent1 = sent.substring(0, splitIndex)+". ";
						String sent2 = startWithCapitalSent(sent.substring(splitIndex+1));
						results.add(sent1); results.add(sent2);
					}
				} catch (Exception e) {
					results.add(sent);
					e.printStackTrace();
				}

			}
		}
		return results;
	}

	private String startWithCapitalSent(String sent) {
		String firstChar = sent.substring(0,1);
		String remainder = sent.substring(1);
		
		return firstChar.toUpperCase()+remainder;
	}

	public List<String> formReviewsForAProduct(String name /*long bpid, String keywordsName*/){
		ReviewObj reviewObjTotal = null;
		try {
			List<HitBase> pagesForAProduct = prodman.findProductByName(name, 1);
			reviewObjTotal = null; 

			for(HitBase p: pagesForAProduct){
				ReviewObj reviewObj = 
						extractSentencesWithPotentialReviewPhrases(p.getUrl());
				// init with first element
				if (reviewObjTotal  == null)
					reviewObjTotal = reviewObj;
				if (reviewObj==null)
					continue;
				String[] afterOriginalization = orig.convert(reviewObj.getOrigSentences(), p.getTitle(), reviewObj.getKeywordsName());
				reviewObj.setOriginalizedSentences(Arrays.asList(afterOriginalization));
				reviewObj.setSentimentPhrases(orig.formedPhrases);

				List<String> buf = reviewObjTotal.getSentimentPhrases();
				if (orig.formedPhrases!=null && orig.formedPhrases.size()>0){
					buf.addAll(orig.formedPhrases);
					reviewObjTotal.setSentimentPhrases(buf);
				}

		/*		buf = reviewObjTotal.getOriginalizedSentences();
				if (buf!=null && afterOriginalization!=null && afterOriginalization.length>0){
					List<String> b1 = Arrays.asList(afterOriginalization);
					List<String> b2 = new ArrayList<String>();
					b2.addAll(buf);
					b2.addAll(new ArrayList<String>(b1));
					reviewObjTotal.setOriginalizedSentences(b2);
				}
*/
			}
			if (reviewObjTotal==null) return new ArrayList<>();
			
			List<String> textReviews = buildManyReviewTexts(reviewObjTotal);

			
		/*	String textReview = buildText(reviewObjTotal);
			try {
				if (textReview!=null && textReview.length()>60)
					ser.saveReviewsToDB(textReview, bpid, pagesForAProduct.get(0).getUrl(), pagesForAProduct.get(0).getTitle(),
							reviewObjTotal.getSentimentPhrases().toString(), reviewObjTotal.getRating());
			} catch (Exception e) {
				System.out.println("Database write failed");
			}
			*/
			
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return reviewObjTotal.getOriginalizedSentences();
	}

	private String buildText(ReviewObj reviewObj) {

		String[] features = reviewObj.getFeaturePhrases();
		List<String> sentences =reviewObj.getOriginalizedSentences();
		StringBuffer buf = new StringBuffer();
		int count = 0;
		for(String sent:sentences){
			if (sent!=null)
				buf.append(sent).append(" ");
			if (count%2==0 && count<features.length)
				if (features[count]!=null){
					buf.append(features[count]);
					if (!(features[count].endsWith("!") ||features[count].endsWith("?")||features[count].endsWith("?") 
							||features[count].endsWith(".\"") ))
						buf.append(". ");
				}

			if (count%5==0)
				buf.append("\n");
			count++;
		}
		return buf.toString();
	}
	
	private List<String> buildManyReviewTexts(ReviewObj reviewObj) {

		String[] features = reviewObj.getFeaturePhrases();
		List<String> sentences =reviewObj.getOriginalizedSentences();
		
		// first count how many sentences
				int NUM_SENTS_IN_REVIEW = 7;
				int count=0;
				for(String sent:sentences){
					if (sent!=null)
						count++;
				}
		int nReviews = count/NUM_SENTS_IN_REVIEW;
		if (nReviews<1)
			nReviews=1;
		StringBuffer[] bufs = new StringBuffer[nReviews];
		for(int i=0; i<bufs.length; i++){
			bufs[i] = new StringBuffer();
		}
				
		count = 0;
		int currentRevIndex = 0;
		for(String sent:sentences){
			if (sent!=null)
				bufs[currentRevIndex].append(sent).append(" ");
			if (count%2==0 && count<features.length)
				if (features[count]!=null){
					bufs[currentRevIndex].append(features[count]);
					if (!(features[count].endsWith("!") ||features[count].endsWith("?")||features[count].endsWith("?") 
							||features[count].endsWith(".\"") ))
						bufs[currentRevIndex].append(". ");
				}

			try {
				if (bufs[currentRevIndex].toString().split(".").length>4)
					bufs[currentRevIndex].append("\n");
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			count++;
			currentRevIndex++;
			if (currentRevIndex>=nReviews)
				currentRevIndex=0;	
		}
		
		List<String> results = new ArrayList<String>();
		for(StringBuffer b:bufs){
			String sent = b.toString().replace("!.","!").replace("?.","?");
			results.add(sent);
		}
		return results;
	}

	public static void main(String[] args){
		String resourceDir = "C:/stanford-corenlp/src/test/resources/";
		ParserChunker2MatcherProcessor proc = ParserChunker2MatcherProcessor.getInstance(resourceDir); 
			
		//ProductFinderInAWebPage init = new ProductFinderInAWebPage("C:/workspace/relevanceEngine/src/test/resources");

		WebPageReviewExtractor extractor = new WebPageReviewExtractor(resourceDir);
		String res1[] = extractor.verifyEnforceStartsUpperCase(new String[]{ "hhhh !", "Klyn mng hghj ."});
				
		List<String> res = extractor.formReviewsForAProduct(//"McCulloch 16-Inch 3.5 HP Electric Chain Saw");
				//	"WORX Electric JawSaw with Extension Handle");
				//	"Panasonic 2-Line Digital Cordless System", 215200345l);
				//	"Sport Silver Dial Women", 215475290);
				//"Rectangle Area Rug", 213885290);
				//		"40VA Replacement Transformer", 213085391);
				//		"PSYLLIUM POWDER Food", 213185391);
				//		"Leighton Toilet Tank", 213285391);
				//"Samsung Knack U310 Flip Phone", 214495493);
				//"Panasonic Cordless Phone 2 handsets", 214870820);
				//"Winegard TV Antenna Pre-Amplifier", 211924499);
				//"Atlona AT-HD-V18 HDMI Distribution Amplifier", 215162612);
				//"airport express base station", 211462827);
				//"denon  Network Streaming A/V Home Theater receiver", 209565926);
				//"sherwood receiver 400 watts stereo", 211286714);
				//"multizone music distribution system", 205333526);
				//"niles zr4", 215104912);
				//"alpine waterproof marine cd receiver", 215167695);
				//"sherwood channel receiver dolby", 215116818);
				//"sherwood lcd tv widescreen hdtv", 215481917);
				//"multiroom music distribution system", 205333526);
				//		"fusion ms compact stereo", 215649463); 
				//"pyle pro speaker", 213265125);
				// "apple iphone 4g",  213265325);
				//"sherwood high performance receiver", 215394729);
				//"sony camera housing", 211960592);
				//"sony xl2100", 1135329203);
				//"sony 18 megapixel-digital-camera", 215743208);
				//"sony m470 microcassette tape recorder", 205828052);
				//"sony monitor terminal expansion board", 213244217);
				//"sony cybershot digital-camera", 215743207);
				//"sony interchangeable lens handycam camcorder", 215153503);
				//"canon powershot digital camera", 214754207);
				//"brother ink pageyield yellow", 204743189);
				// ?? "garmin 2200 gps navigator", 215167480);
				"halo portable backup battery");

		ProfileReaderWriter.writeReportListStr(res, "formedReviewSentences4.csv");


		/*		
			res=	extractor. extractSentencesWithPotentialReviewPhrases(//"http://www.sitbetter.com/view/chair/ofm-500-l/ofm--high-back-leather-office-chair/");
		//"http://www.amazon.com/OFM-High-Back-Leather-Integral-Headrest/dp/B002SIW1E0/ref=sr_1_1?ie=UTF8&qid=1353370254&sr=8-1&keywords=OFM-High-Back-Leather-Integral-Headrest");
		//"http://www.amazon.com/Oregon-511AX-Chain-Grinder-Sharpener/dp/B0000AX0CY/ref=sr_1_4?s=industrial&ie=UTF8&qid=1353373435&sr=1-4&keywords=chain+saws");
			//			"http://www.amazon.com/Bearing-UCP204-12-Housing-Mounted-Bearings/dp/B002BBIYWM/ref=sr_1_1?s=industrial&ie=UTF8&qid=1353373786&sr=1-1&keywords=pillow+block+bearing");
			"http://www.amazon.com/ShelterLogic-20--Feet-Auto-Shelter/dp/B001OFNK8O/ref=sr_1_1?s=lawn-garden&ie=UTF8&qid=1353376677&sr=1-1&keywords=shelterlogic+62680+autoshelter+portable+garage+carport");			
						System.out.println(res);
		 */			

	}
}
