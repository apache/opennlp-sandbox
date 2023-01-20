package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.commons.lang.StringUtils;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.exception.FacebookException;
import com.restfb.types.Event;
import com.restfb.types.Page;


public class FBOpenGraphSearchManager {

	public List<String[]> profiles = null;
	protected FacebookClient mFBClient;
	protected PageFetcher pageFetcher = new PageFetcher();
	protected static final int NUM_TRIES = 5;
	protected static final long WAIT_BTW_TRIES=1000; //milliseconds between re-tries
	
		
	public FBOpenGraphSearchManager(){
		profiles = ProfileReaderWriter.readProfiles("C:\\nc\\features\\analytics\\dealanalyzer\\sweetjack-localcoupon-may12012tooct302012.csv");
		
	}
	
		
	public void setFacebookClient(FacebookClient c){
		this.mFBClient=c;
	}
	
	public List<Event> getFBEventsByName(String event)
	{
	    List<Event> events = new ArrayList<Event>();
	    
	    for(int i=0; i < NUM_TRIES; i++)
	    {
    	    try
    	    {
        	    Connection<Event> publicSearch =
        	            mFBClient.fetchConnection("search", Event.class,
        	              Parameter.with("q", event), Parameter.with("type", "event"),Parameter.with("limit", 100));
        	    System.out.println("Searching FB events for " + event);
        	    events= publicSearch.getData();
        	    break;
    	    }
    	    catch(FacebookException e)
    	    {
    	    	System.out.println("FBError "+e);
    	        try
                {
                    Thread.sleep(WAIT_BTW_TRIES);
                }
                catch (InterruptedException e1)
                {
                	System.out.println("Error "+e1);
                }
    	    }
	    }
	    return events;
	}
	
	public Long getFBPageLikes(String merchant)
	{
        List<Page> groups = new ArrayList<Page>();
        
        for(int i=0; i < NUM_TRIES; i++)
        {
            try
            {
                Connection<Page> publicSearch =
                        mFBClient.fetchConnection("search", Page.class,
                          Parameter.with("q", merchant), Parameter.with("type", "page"),Parameter.with("limit", 100));
                System.out.println("Searching FB Pages for " + merchant);
                groups= publicSearch.getData();
                break;
            }
            catch(FacebookException e)
            {
            	System.out.println("FBError "+e);
                try
                {
                    Thread.sleep(WAIT_BTW_TRIES);
                }
                catch (InterruptedException e1)
                {
                	System.out.println("Error "+e1);
                }
            }
        }
        
        for (Page p: groups){
        	if (p!=null && p.getLikes()!=null && p.getLikes()>0) 
        		return p.getLikes();
        }
        
        //stats fwb">235</span>
        
        for (Page p: groups){
        	if (p.getId()==null)
        		continue;
        	String content = pageFetcher.fetchOrigHTML("http://www.facebook.com/"+p.getId());
        
        	String likes = StringUtils.substringBetween(content, "stats fwb\">", "<" );
        	if (likes==null)
        		continue;
        	Integer nLikes =0;
        	try {
        	nLikes = Integer.parseInt(likes);
        	} catch (Exception e){
        		
        	}
        	if (nLikes>0){
        		return (long)nLikes;
        	}
        	
        }
        
        
        return null;
	}
	

    // 
    
    public static void main(String[] args){
    	FBOpenGraphSearchManager man = new FBOpenGraphSearchManager ();
    	man.setFacebookClient(new DefaultFacebookClient());
       	
    	
    	long res = man.getFBPageLikes("chain saw");
    	System.out.println(res);
    	    	
    }
}
