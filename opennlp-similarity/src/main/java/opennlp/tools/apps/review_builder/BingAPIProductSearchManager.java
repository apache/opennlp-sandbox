package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;

import org.apache.commons.lang.StringUtils;

public class BingAPIProductSearchManager {
	BingQueryRunner search = new BingQueryRunner();

	public List<HitBase> findProductByName(String name, int count){
		List<HitBase> foundFBPages = search.runSearch("site:amazon.com"+" "+name + " reviews"
				, 10);
		List<HitBase> results = new ArrayList<HitBase>();
		int ct=0;
		for(HitBase h: foundFBPages){
			if (ct>=count) break; ct++; 
			String title = h.getTitle().toLowerCase();
			if (h.getUrl().indexOf("amazon.com")<0)
				continue;
			String[] merchantWords = name.toLowerCase().split(" ");
			int overlapCount=0;
/*			for(String commonWord:merchantWords){
				if (title.indexOf(commonWord+" ")>-1 || title.indexOf(" "+commonWord)>-1){
					overlapCount++;
					System.out.println(" found word "+ commonWord + " in title = "+title);
				}
			}
			float coverage = (float)overlapCount/(float) (merchantWords.length);
			if ((coverage>0.4 || (coverage>0.5f && merchantWords.length <4 )))
*/				results.add(h);
		}
		return results;
	}
	
	public List<HitBase> findProductByNameNoReview(String name, int count){
		List<HitBase> foundFBPages = search.runSearch(name, count);
		List<HitBase> results = new ArrayList<HitBase>();
		int ct=0;
		for(HitBase h: foundFBPages){
			if (ct>=count) break; ct++; 
			String title = h.getTitle().toLowerCase();
			String[] merchantWords = name.toLowerCase().split(" ");
			int overlapCount=0;
			for(String commonWord:merchantWords){
				if (title.indexOf(commonWord+" ")>-1 || title.indexOf(" "+commonWord)>-1){
					overlapCount++;
					System.out.println(" found word "+ commonWord + " in title = "+title);
				}
			}
			float coverage = (float)overlapCount/(float) (merchantWords.length);
			if ((coverage>0.4 || (coverage>0.5f && merchantWords.length <4 )))
				results.add(h);
		}
		return results;
	}

	

	public static void main(String[] args){
		BingAPIProductSearchManager man = new BingAPIProductSearchManager ();
		List<HitBase> res = man.findProductByName("chain saw", 5);
		System.out.println(res);  	
	}
}
