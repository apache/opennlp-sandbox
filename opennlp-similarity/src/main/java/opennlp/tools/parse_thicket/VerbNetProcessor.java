package opennlp.tools.parse_thicket;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.jverbnet.data.Frame;
import edu.mit.jverbnet.data.Frame.FrameBuilder;
import edu.mit.jverbnet.data.FrameType;
import edu.mit.jverbnet.data.IFrame;
import edu.mit.jverbnet.data.IMember;
import edu.mit.jverbnet.data.IThematicRole;
import edu.mit.jverbnet.data.IVerbClass;
import edu.mit.jverbnet.data.IWordnetKey;
import edu.mit.jverbnet.data.VerbClass;
import edu.mit.jverbnet.index.IVerbIndex;
import edu.mit.jverbnet.index.VerbIndex;

public class VerbNetProcessor implements IGeneralizer<Map<String, List<String>>> {

	static VerbNetProcessor instance;
	static private String pathToVerbnet = null; //new File( "." ).getCanonicalPath()+"/src/test/resources";
	public static VerbNetProcessor getInstance(String resourceDir) {
		if (resourceDir==null)
			try {
				resourceDir = new File( "." ).getCanonicalPath()+"/src/test/resources";
			} catch (IOException e) {
				e.printStackTrace();
			}
		pathToVerbnet = resourceDir + "/new_vn";
		if (instance == null)
			instance = new VerbNetProcessor();

		return instance;
	}	

	IVerbIndex index = null;

	private VerbNetProcessor() {

		try {
			URL url = new URL ("file", null , pathToVerbnet ) ;
			index = new VerbIndex ( url ) ;

			index . open () ;

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public IVerbClass getVerbNetForAVerb_____New(String verb){
		Iterator<IVerbClass> iter = index.iterator();
		while(iter.hasNext()){
			IVerbClass v = iter.next();

			if (v.getID().startsWith(verb))
				return v;
		}
		iter = index.iterator();
		while(iter.hasNext()){
			IVerbClass v = iter.next();
			if (!v.getMembers().isEmpty()){
				for(IMember m: v.getMembers()) {
					if (m.getName().equals(verb)){
						return v;
					}
				}
			}
		}
		return null;
	}



	public IVerbClass getVerbNetForAVerb(String verb){
		Iterator<IVerbClass> iter = index.iterator();
		while(iter.hasNext()){
			IVerbClass v = iter.next();

			if (v.getID().startsWith(verb))
				return v;

			if (!v.getMembers().isEmpty() && v.getMembers().get(0).getName().equals(verb)){
				return v;
			}
		}
		return null;
	}

	public List<Map<String, List<String>>> generalize(Object o1, Object o2) {
		IVerbClass v1, v2;
		if ((o1 instanceof String) && (o2 instanceof String)){
			v1 = getVerbNetForAVerb((String) o1);
			v2 = getVerbNetForAVerb((String) o2);		
			return generalize(v1, v2);
		} else

			v1 = (IVerbClass)o1;
		v2 = (IVerbClass)o2;
		List<Map<String, List<String>>> resList = new ArrayList<Map<String, List<String>>>();

		if (v1 ==null || v2==null) // not found
			return  resList;

		// lists for results
		List<String> roles = new ArrayList<String>();

		List<IThematicRole> roles1 = v1.getThematicRoles(), roles2 = v2.getThematicRoles();
		Map<String, List<String>> results = new HashMap<String, List<String>>();

		for(int i=0; i< roles1.size()&& i< roles2.size(); i++){
			if (roles1.get(i).getType().equals(roles2.get(i).getType())){
				roles.add(roles1.get(i).getType().toString());
			} else 
				roles.add("*");
		}

		List<IFrame> frames1 = v1.getFrames(), frames2 = v2.getFrames();
		List<String> patterns1 = new ArrayList<String>(), patterns2 = new ArrayList<String>();
		for(int i=0; i< frames1.size(); i++){
			patterns1.add(frames1.get(i).getPrimaryType().getID());
		}
		for(int i=0; i< frames2.size(); i++){
			patterns2.add(frames2.get(i).getPrimaryType().getID());
		}
		patterns2.retainAll(patterns1);
		results.put("phrStr", patterns2) ; 

		List<String> patternsWord1 = new ArrayList<String>(), patternsWord2 = new ArrayList<String>();
		for(int i=0; i< frames1.size(); i++){
			try {
				patternsWord1.add(frames1.get(i).getSecondaryType().getID());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for(int i=0; i< frames2.size(); i++){
			try {
				if (frames2.get(i).getSecondaryType()!=null && frames2.get(i).getSecondaryType().getID()!=null)
					patternsWord2.add(frames2.get(i).getSecondaryType().getID());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		patternsWord2.retainAll(patternsWord1);
		results.put("phrDescr", patternsWord2) ; 

		results.put("roles", roles);

		resList.add(results);
		return resList;
	}

	// takes a verb and forms its verbnet parameters 
	// abandon  (leave-51.2 leave-51.2 ) (NP V NP.source ) (Transitivebasically, locative preposition drop of "from" ) (
	public StringBuilder buildTreeRepresentationForTreeKernelLearning(String verb){
		StringBuilder sb = new StringBuilder(1000);
		IVerbClass v;
		v = getVerbNetForAVerb(verb);
		if (v==null) // for some reason this verb is not in the vocabulary
			return null;
		sb.append(verb + "  (" );
		List<IThematicRole> roles = v.getThematicRoles();

		for(int i=0; i< roles.size(); i++){
			sb.append(roles.get(i).getVerbClass().getID().replace(".", "")+" ");
		}
		sb.append( ") (" );

		List<IFrame> frames = v.getFrames();
		for(int i=0; i< frames.size(); i++){
			sb.append(//" ("+
		frames.get(i).getPrimaryType().getID().replace(".", "-")+" ");
		}
		sb.append( ") (" );
		for(int i=0; i< frames.size(); i++){
			sb.append(frames.get(i).getSecondaryType().getID().
					replace(".", "").replace(",", " ").replace("\"", "-").replace("/", "-").replace("(","").replace(")","")+" ");
		}
		sb.append( ") " );

		if (v.getParent()!=null && v.getParent().getThematicRoles()!=null){
			sb.append( "(" );
			for(int i=0; i<v.getParent().getThematicRoles().size(); i++){
				sb.append(v.getParent().getThematicRoles().get(i).getType()+" ");
			}
			sb.append( ")" );
		}
		return sb;
	}

	public void testIndex () throws Exception {
		Iterator<IVerbClass> iter = index.iterator();
		while(iter.hasNext()){
			IVerbClass v = iter.next();
			System.out.println(v.getID() + " +> " + v.getFrames().get(0).getVerbClass().getID() + "  \n ===> " + v.getMembers().get(0).getName()  );
			List<IThematicRole> roles = v.getThematicRoles();
			for (IThematicRole r: roles){
				System.out.println(r.getType());
			}

			List<IFrame> frames = v.getFrames();
			for(IFrame f: frames){
				try {
					System.out.println(f.getPrimaryType().getID() + " => " + f.getXTag() + " >> "+ f.getSecondaryType().getID() +  " : " + f.getExamples().get(0));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		IVerbClass verb0 =  index.getVerb("hit-18.1");
		// look up a verb class and print out some info
		IVerbClass verb = index . getRootVerb ("hit-18.1") ;
		IMember member = verb . getMembers () . get (0) ;
		Set < IWordnetKey > keys = member . getWordnetTypes () . keySet () ;
		IFrame frame = verb . getFrames () . get (0) ;
		FrameType type = frame . getPrimaryType () ;
		String example = frame . getExamples () . get (0) ;
		System . out . println ("id: " + verb . getID () ) ;
		System . out . println (" first wordnet keys : " + keys ) ;
		System . out . println (" first frame type : " + type . getID () ) ;
		System . out . println (" first example : " + example ) ;
	}

	public static void main(String[] args){
		String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/test/resources";
		VerbNetProcessor proc = VerbNetProcessor.getInstance(resourceDir);
		/*	try {
				proc.testIndex();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 */	

		System.out.println(proc.buildTreeRepresentationForTreeKernelLearning("abandon"));
		System.out.println(proc.buildTreeRepresentationForTreeKernelLearning("earn"));
		
		List res = proc.generalize("marry", "engage");
		System.out.println (res);

		res = proc.generalize("assume", "alert");
		System.out.println (res);

		res = proc.generalize("alert", "warn");
		System.out.println (res);
	}




}
