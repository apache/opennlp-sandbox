package opennlp.tools.apps.contentgen.multithreaded;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;

public class BingWebQueryRunnerThread extends BingQueryRunner implements Runnable{
	
	private String query;
	private List<HitBase> results= new ArrayList<HitBase>();
	public BingWebQueryRunnerThread(String Query){
		super();
		this.query=Query;
	}
	public void run(){
		results=runSearch(query);
		fireMyEvent(new MyEvent(this));
	}
	public List<HitBase> getResults() {
		return results;
	}
	
	public String getQuery() {
		return query;
	}
	
	// Create the listener list
    protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();
    // This methods allows classes to register for MyEvents 

    public void addMyEventListener(MyEventListener listener) {
        listenerList.add(MyEventListener.class, listener);
    }
    // This methods allows classes to unregister for MyEvents

    public void removeMyEventListener(MyEventListener listener) {
        listenerList.remove(MyEventListener.class, listener);
    }

    void fireMyEvent(MyEvent evt) {
        Object[] listeners = listenerList.getListenerList();
        // Each listener occupies two elements - the first is the listener class
        // and the second is the listener instance
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == MyEventListener.class) {
                ((MyEventListener) listeners[i + 1]).MyEvent(evt);
            }
        }
    }
	

}
