package opennlp.tools.similarity.apps;

import java.util.List;

import junit.framework.TestCase;

public class SearchResultsProcessorTest extends TestCase{
	SearchResultsProcessor proc = new SearchResultsProcessor();
	
	
	public void testSearchOrder(){
		List<HitBase> res = proc.runSearch("How can I pay tax on my income abroad"); 
		
		// we verify that top answers have high similarity score
		System.out.println(res);
		HitBase first = res.get(0);
		assertTrue( first.getGenerWithQueryScore()>3.0);
		//assertTrue(first.getTitle().indexOf("Foreign")>-1 && first.getTitle().indexOf("earned")>-1);
		
		HitBase second = res.get(1);
		assertTrue( second.getGenerWithQueryScore()>1.9);
		//assertTrue(second.getTitle().indexOf("living abroad")>-1);
				
	}
	
	public void testSearchOrder2(){
		List<HitBase> res = proc.runSearch(
	   "Can I estimate what my income tax would be by using my last pay"); 
		
		System.out.println(res);
		HitBase first = res.get(0);
		assertTrue( first.getGenerWithQueryScore()>1.9);
		
		HitBase second = res.get(1);
		assertTrue( second.getGenerWithQueryScore()>1.9);
				
	}
}
