package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.apps.relevanceVocabs.PhraseProcessor;
import opennlp.tools.apps.relevanceVocabs.SentimentVocab;
import opennlp.tools.apps.relevanceVocabs.SynonymListFilter;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class SentenceOriginalizer {
	private String[] sents; 
	private SentenceBeingOriginalized[] sentenceBeingOriginalized;
	public List<String> formedPhrases = new ArrayList<>();

	private final MachineTranslationWrapper rePhraser = new MachineTranslationWrapper();
	private final SentimentVocab sVocab = SentimentVocab.getInstance();
	PhraseProcessor pProc = new PhraseProcessor();
	SynonymListFilter filter = null;
	private List<String> verbsShouldStayNoSubstition = Arrays.asList(new String[]{
			"might", "can", "power", "bonk", "screw", "victimization", "victimize", "victimised", "victimized", "victimise",
			"hump", "sluttish", "wanton"
	});

	public SentenceOriginalizer(String[] ss){
		sentenceBeingOriginalized = new SentenceBeingOriginalized[ss.length];
		for(int i= 0; i< ss.length; i++){
			//sentenceBeingOriginalized[i] = new  SentenceBeingOriginalized()
		}
	}

	public SentenceOriginalizer(String dir){
		filter = new  SynonymListFilter(dir);
	};

	public String[] getSents() {
		return sents;
	}

	public void setSents(String[] sents) {
		this.sents = sents;
	}

	

	private void substituteProsCons(){
		for(int i = 0; i< sents.length; i++){
			if (sents[i]==null)
				continue;

			sents[i] = sents[i].replace("...", " ").replace("..", " ");

			if (sents[i].startsWith("Pros")){
				sents[i]="";
				sents[i+1] = "I liked that "+ sents[i+1];
			}

			if (sents[i].startsWith("Cons")){
				sents[i]="";
				sents[i+1] = "What I did not like was that "+ sents[i+1];
			}
		}
	}

	private void insertProductNameForRefs(String prodName){
		prodName = prodName.toLowerCase();
		prodName = StringUtils.trim(prodName);
		
		for(int i = 0; i< sents.length; i++){
			if (sents[i]==null)
				continue;
			String snt = sents[i];
			String line  = snt.replace(" it ", " "+prodName+" ");
			if (line.equals(snt)){
				line = snt.replace(" this ", " "+prodName+" ");
			}

			sents[i]=line;
		}
	}
	
	private void insertProductNameForRefsFullNameKeywords(String prodName, String keywordsName){
		prodName = StringUtils.trim(prodName.toLowerCase());
				
		for(int i = 0; i< sents.length; i++){
			Random rand = new Random();
			double flag = rand.nextDouble();
			String prodNameCurr = null;
			if (flag>0.4)
				prodNameCurr = prodName;
				else
					prodNameCurr = keywordsName;
					
			if (sents[i]==null)
				continue;
			String snt = sents[i];
			String line  = snt.replace(" it ", " "+prodNameCurr+" ");
			if (line.equals(snt)){
				line = snt.replace(" this ", " "+prodNameCurr+" ");
			}

			sents[i]=line;
		}
	}

	private void turnTenseToPast(){
		for(int i = 0; i< sents.length; i++){
			if (sents[i]==null)
				continue;
			sents[i] = sents[i].replace("to do ", "to d_o_ ");
			sents[i]=sents[i].replace(" is ", " was ").replace(" done ", " was done ").replace(" are ", " were ")
					.replace(" do ", " did ").replace(" yes, ", " true, ");
			sents[i]=sents[i].replace("somebody ", "one ").replace("would like", "would want").replace("I am", "users are");
			sents[i]=sents[i].replace("my wife", "my spouse").replace("I would definitely buy ", "I wouldn't hesitate to buy ")
					.replace("I haven't tried ", "I did not actually have a chance to try ");
			sents[i]=sents[i].replace("they arrived ", "they were shipped to my residence ").replace(" ive ", " I have ")
					.replace("We have ", "I have already tried and written a review on ");
			
			sents[i] = sents[i].replace( "to d_o_ ", "to do ");
	
			if (sents[i].startsWith("We "))
				sents[i] = sents[i].replace("We ", "I know they ");
			if (sents[i].startsWith("You "))
				sents[i] = sents[i].replace("You ","I believe one can ");
			
			if (sents[i].startsWith("Well "))
				sents[i] = sents[i].replace("Well ","I would state that ");

		}
	}

	private void turnCounterFactual(){
		for(int i = 0; i< sents.length; i++){
			if (sents[i]==null)
				continue;
			sents[i]=sents[i].replace("however ", "b1ut1 ").replace("but ", "however ")
					.replace("b1ut1 ", "but ").replace("I say", "I repeat").
					replace("same way", "same manner").replace(" you ", " somebody ").replace(" can ", " might ");

		}
	}

	public void substituteSynonymVerbs(){
		for(int i = 0; i< sents.length; i++){
			String line = sents[i];
			List<List<ParseTreeChunk>> ps = pProc.getPhrasesOfAllTypes(line);
			if (ps==null || ps.size()<2)
				continue;

			List<ParseTreeChunk> vps = ps.get(1);

			extractNounPhrasesWithSentiments(ps.get(0));

			line = substituteSentimentSynonyms(line, ps);

			if (vps==null)
				continue;
			boolean bVerbRule = false;
			if (vps.size()==1)
				line = rePhraser.rePhrase(line);
			else {
				if (vps.size()>1)

					for (ParseTreeChunk v: vps){
						String verbLemma = v.getLemmas().get(0);
						String newVerb = filter.getSynonym(verbLemma);
						if (newVerb!=null && newVerb.length()>3 && verbLemma.length()>3 // both old and new words should be above 3
								&& !newVerb.endsWith("ness") // empirical rule
								&& !verbsShouldStayNoSubstition.contains(verbLemma) &&
								!verbsShouldStayNoSubstition.contains(newVerb)	){
							line = line.replace(verbLemma+" ", newVerb+" "); 	
							line = line.replace(" "+verbLemma, " "+newVerb); 
							System.out.println("Synonym for verb substitution: "+verbLemma + "->"+newVerb);
							bVerbRule = true;
						}
					}
				if (!bVerbRule && vps.size()==2 && Math.random()>0.8) // no other means of originalization worked, so do inverse translation
					line = rePhraser.rePhrase(line);
			}
			sents[i]=line;

		}
	}


	private String substituteSentimentSynonyms(String line,
			List<List<ParseTreeChunk>> ps) {
		List<ParseTreeChunk> nounPhrases = ps.get(0);
		if (nounPhrases.size()<1)
			return line;

		for(ParseTreeChunk ch: nounPhrases){
			List<String> lemmas = ch.getLemmas();
			for(String oldSentim:lemmas){
				if ( sVocab.isSentimentWord(oldSentim.toLowerCase())) {
					String newSentim = filter.getSynonym(oldSentim);
					if (newSentim!=null && newSentim.length()>3 && !verbsShouldStayNoSubstition.contains(newSentim)
							&& !verbsShouldStayNoSubstition.contains(oldSentim)){
						line = line.replace(oldSentim+" ", newSentim+" "); 	
						line = line.replace(" "+oldSentim, " "+newSentim);
						System.out.println("Synonym for sentiment substitution: "+oldSentim + "->"+newSentim);
					}
				}
			}
		}

		return line;
	}

	private void extractNounPhrasesWithSentiments(List<ParseTreeChunk> list) {
		List<String> phrasesWithSentiments = new ArrayList<String>();
		for(ParseTreeChunk ch: list){
			List<String> lemmas = ch.getLemmas();
			for(String l:lemmas){
				if ( sVocab.isSentimentWord(l.toLowerCase())) {
					phrasesWithSentiments.add(lemmas.toString());
				}
			}
		}
		formedPhrases.addAll(phrasesWithSentiments);
	}

	public String[] convert(String[] sents, String name, String keywordsName){
		name = name.replace("Amazon.com:" , "").replace("Amazon.com" , "").replace("..." , " ")
				.replace("Customer Reviews: ", "");

		this.sents = sents;
		try {
			substituteProsCons();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			//insertProductNameForRefs(name);
			insertProductNameForRefsFullNameKeywords(name, keywordsName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			turnTenseToPast();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			turnCounterFactual();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			substituteSynonymVerbs();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// remove dupes
		this.formedPhrases = new ArrayList<>(new HashSet<>(this.formedPhrases));

		return sents;

	}

	public static void main(String[] args){
		//ProductFinderInAWebPage init = new ProductFinderInAWebPage("C:/workspace/productsearchfe/src/test/resources");
		SentenceOriginalizer orig = new SentenceOriginalizer("src/test/resources");
		String[] sents = new String[] {
				"Leave the bulky stabilization rig at home and take smooth handheld videos from any angle thanks to Optical SteadyShot image stabilization with Active Mode."
				//"Other then that, it works well, and the chain stops instantly when you let go of the trigger, or push the safety bar."	
		};
		String[] res = orig.convert(sents, "VIP Product", "vv propro");
		System.out.println(Arrays.asList(res));
	}

}

/*
 * 1.	Some Amazon specific text keeps showing up so we might want to put a filter on recurring phrases such as:
1.	Unlimited Free Two-Day Shipping
2.	View Larger
3.	What's in the box
2.	Period/stop added to punctuation marks: 
1.	!.
2.	?.
3.	:.
4.	.". 
5.	-.
3.	Saw some HTML formatting occasionally, such as <em></em>
4.	Redundancy with choice phrases appearing multiple times in a single review
5.	Specific issue with words being added at the end of the letter "s," creating nonsensical words:
1.	It mispronouncesulphur virtually every caller'sulphur name in waysulphur that..
2.	In fact, it'southward a rare feature that I recollect southwardhould be commonplace in any southwardurround receiver.
6.	Adding -iness to make nonsensical words: mightinessiness, powerinessiness

 */



/*
 * After using a gasoline powered chain saw for many years had to stop using because of dust and fumes made my copd worse this electric saw is great has surprising amount of power without the gas fumes..
Nice chainsaw, works great, well built.
The instant-stop chain is very safe, but a bit abrupt when releasing the trigger.
I wish there were a half-way release that turned off the motor but did not engage the instant stop break.
Pros .
inexpensive compared to gas chainsaws, lightweight, cuts with good power, will do most anything that a gas chainsaw will do. like the automatic chain oiler and easy tension adjustment.
Cons .
If you are cutting larger branches and trees, a gas is better.
However this will work on 8-10" size very well.
Bought this McCulloch electric chainsaw to replace an old Craftsman electric chain saw. (the Craftsman got ran over by a car).
Compared to my old Craftsman electric chain saw, the McCulloch seems to be wonderful.
The first test was to cut a 16" diameter oak branch, cut thru it like hot butter.
The "no tools needed" chain tensioner seems to be a good design..
Is a good saw, however it came with the handle that wraps abound the left side of the saw was broken.
The box looked good, but the saw itself was damaged.
However, because I had a lot of tree damage in my yard, and more storms coming, I made due with it.
Other then take, it works well, and the chain stops instantly when you let go of the trigger, or push the safety bar.
stump w/ this E-saw.
It keeps doing a super job.
In terms of a replacement chain, make sure to get the Oregon S-54 (S is style of cutter, 54 means 54 links).
The MC literature suggests use of a S-55, but it is TOO Long and will soon wind up in the trash can.
ALSO, the MC factory installed gasket for the lube oil, between the saw and chain bar is total trash.
When changing out the chain, pull the bar off, pull out and throw away the MC factory gasket, clean the bar and apply a piece of electrical tape, using a knife to cut out a pathway for oil to the bar.
Will lube perfectly now!
This is the second electric McCilloch 16" chain saw that I have owned and it is even better and more powerful than the first.
I still use a gas chain saw out in the woods on my property but I usually do just enough cutting with it to get the logs on a trailer so I can take them bach to my shed to cut them up and save the sawdust for on my garden and flower beds as mulch.
This electric is lighter and more powerful than my gas saw and makes short work of even 14" well-seasoned oak and poppel logs with a minimum of effort.
I highly recommend this sae for anyone who has an electric outlet close enough to their cutting station.
Bought this McCulloch electric chainsaw to replace an old Craftsman electric chain saw. (the Craftsman got ran over by a car).
Compared to my old Craftsman electric chain saw, the McCulloch seems to be wonderful.
The first test was to cut a 16" diameter oak branch, cut thru it like hot butter.
The "no tools needed" chain tensioner seems to be a good design (design seems to be similar to that used by other manufacturers).
Assuming. this thing keeps cutting/running the same way in the long term, then we have a winner. (note. all the electric chain saws come with cheap looking chains with cutting blades spaced very widely apart along the chain.
To be ready for the bigger cutting jobs I sprung for a new $18 Oregon s-54 16" chain.).
Update .
Having used both gas and electric chain saws for more years than I care to remember, this little beauty is far more than I'd hoped for.
Yes, it requires a cord to function and, without a handy "Current Bush", serves no useful purpose, but for trimming trees or cutting up firewood in a yard it beats H*** out of fighting the frustration when a gas saw refuses to start or remain running.
I have another 14" electric MuCulloch along with a 16" gas Homelite and consider this to be a combination of the best qualities of both the others, the convenience of the small electric and the greater cutting ability of the gas powered Homelite.
This little beauty appears to have as much power as the gas saw without the hassle of mixing fuel and the ongoing maintenence associated with it and cuts far faster than it's small electric brother.
If I was forced to have a single chainsaw, in my present position(Pleanty of fire wood handy, just in need of cutting to the proper dimensions), this baby would be may choice without any douts or reservations.
Ordered the Mcculloch 16inch electric chain saw to do some serious pruning of trees around the house which had severe frost damage.
Although an electric chain saw, it cut through trees up to eight inches like a hot knife through butter.
Not once did i have problems in two days of cutting.
The big pros I noticed while using is realtively lightweight for a chainsaw and you can hold in one hand to use.
Once you release the power switch, the chainsaw chain immediately stops!.
This is a good thing as it keeps body parts attached.
One nifty thing about this chainsaw is the chain tightener is outstanding once you figure how it works.
No tools, just move the knobs and tighten, couldn't be easier and definitely beats hunting down a wrench to tighten.
Only con is being electric, you have to watch the power cord.
Very easy to hit extension cord if not careful.
But it wakes you up when you are tired from your yard work.
Let a good buddy borrow it and he was also impressed with the ease of use.
Outstanding for jobs around you house, two thumbs up!
The McCulloch3516F chainsaw puts an end to my problem of gas engines that don't start when I really need them to.
I have been cutting out maple branches this summer from trees with verticillium wilt . branches up to 8 inches are no problem at all.
This saw has an impressive safety feature. a chain brake that stops the saw instantly as soon as the trigger is released or the safety guard is pushed forward.
I mean instantly. there is a loud clunk as the brake engages and the chain stops dead.
This takes some getting used to, as the brake engages if you wiggle your finger while running the chainsaw, causing the chain to start and stop.
There is no concept of "revving" the chain.
It also means there is no "idle" speed for the chain.
It is on or off.
And that is safe.
You can also consider it a safety feature that the chain has fewer cutting teeth than my gas powered saw chains.
I don't know the relative operating RPMs .
if they are about the same, this saw seems to cut a little slower, and fewer teeth would do that.
This makes the saw less aggressive and less likely to pull out of your control.
I like that.
As I say, the cutting ability is well in excess of the 8" branches I've been dealing with.
The oil fill is conveniently located so that you don't have to tip the saw to fill it, although a small funnel is helpful.
Overall, I am very happy with this chainsaw.
The saw works very well, overall.
I have some minor complaints:.
1.
The chain drive gear cover requires a Phillips screwdriver to get the cover off.
This is just dumb !.
There's no good reason why it shouldn't have a thumbscrew similar to, but smaller than the chain tensioner thumbscrew.
As someone pointed out, the chain gear area regularly gets clogged with oily sawdust that needs to be cleaned out.
I can't figure out a good excuse for this design mistake.
2 .
The "instant chain stop" feature woks well, but the remaining motor drivetrain makes a loud howling screech until the motor actually stops.
Makes me think there might be something wrong with the drivetrain.
The saw seems to work well, though.
Time will tell.
3 .
The oil filler neck is titled to the side, not vertical to the saw when placed on level ground.
This makes viewing the oil stream going in and the rising oil level unnecessarily difficult.
This is another obvious design mistake.
4 .
This is my first chainsaw, but it seems the bar oil reservoir is ridiculously small !.
I have to refill it every 10 minutes of use.
After reading other reviews for this model I immediately threw out the stock chain without ever using it and replaced it with an Oregon model S52 chain (dual chains is model ST52).
Note that it fits fine although it is advertized as a 14 inch chain and this saw is advertized to be 16 inches.
Go figure..
Also, after reading about the risk of burning up the motor due to using a too lightweight extension cord, I bought a "US Wire 65100 12/3 100-Foot SJTW Orange Heavy Duty Extension Cord".
It's heavy, alright !
 */
