package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.Triple;

public class ReviewBuilderRunner {

	private List<Triple> input = new ArrayList<Triple>(); 

	public ReviewBuilderRunner(){

		/*	input.add( new Pair<String, Integer>("chief architect portable mobile tv", 204973051));

	input.add( new Pair<String, Integer>("lg plasma tv", 215734562));
	input.add( new Pair<String, Integer>("magnavox lcd hdtv", 215415652));
	input.add( new Pair<String, Integer>("yamaha aventage home theater receiver", 215742271));
	input.add( new Pair<String, Integer>("panasonic 24inch lcd tv", 215742233));
	input.add( new Pair<String, Integer>("otterbox barnes and noble nook commuter case", 215572161));
	input.add( new Pair<String, Integer>("sony kdl32ex340 led tv", 215743925));
	input.add( new Pair<String, Integer>("alpine waterfall tabletop fountain lighting", 215135546));
    input.add( new Pair<String, Integer>("ihome rechargeable speaker system", 215363231 ));
	input.add( new Pair<String, Integer>("ion slide film scanner", 212088884));

		 input.add( new Pair<String, Integer>("mens dr martens shoes black nappa", 210813142));
		 input.add( new Pair<String, Integer>("calvin klein seamless thong panty", 201984853));
		 input.add( new Pair<String, Integer>("mens clarks shoes wallabee beeswax leather", 210808477));
		//? input.add( new Pair<String, Integer>("mens sperry topsider shoes", 210809238));
		 input.add( new Pair<String, Integer>("mens giorgio brutini shoes italian calf", 210809508));

		input.add( new Pair<String, Integer>("halo portable backup battery", 1640825398));
input.add( new Pair<String, Integer>("kenwood pkgmp18 cd receiver  coaxial speakers",1642712915));
input.add( new Pair<String, Integer>("element ultraslim hdtv",1643167865));
input.add( new Pair<String, Integer>("westinghouse  dled hdtv black",1641930013));
input.add( new Pair<String, Integer>("boss audio receiver speaker package system",1643532459));
input.add( new Pair<String, Integer>("kenwood  cd receiver coaxial speakers bundle",1646566070));
input.add( new Pair<String, Integer>("element electronics lcd tv black ",1637163018));
input.add( new Pair<String, Integer>("stunt copter rechargeable battery pack",1636937811));
input.add( new Pair<String, Integer>("element led ultraslim hdtv  soundbar",1637572596));
input.add( new Pair<String, Integer>("boss  receiver speaker package system bundle",1646566067));
input.add( new Pair<String, Integer>("coby  hd tv",1638746307));
input.add( new Pair<String, Integer>("vizio  diag led smart hdtv",1660162001));
input.add( new Pair<String, Integer>("sony dock for ipad ipod and iphone",1646826284));
input.add( new Pair<String, Integer>("vizio  led  ultraslim hdtv",1642018249));
input.add( new Pair<String, Integer>("lcd kula tv multimedia player",1640265845));

input.add(new Pair<String, Integer>("liz and co alex tall leather boots",1630836375));
input.add( new Pair<String, Integer>("total girl silvia sequin moccasin", 1630828314));
input.add( new Pair<String, Integer>("new england patriots new era nfl sport sideline knit", 1588531904));
input.add( new Pair<String, Integer>("betseyville sequin backpack", 1630825375));
input.add( new Pair<String, Integer>("the north face womens osito jacket mojito", 1639791775));
input.add( new Pair<String, Integer>("misty harbor raincoat trench removable liner", 903542613));
input.add(new Pair<String, Integer>("ae womens camo jacket ", 1229070780));
input.add(new Pair<String, Integer>("indianapolis colts sideline knit", 1588531896));
input.add(new Pair<String, Integer>("b o c korah boot", 1622401738));
input.add(new Pair<String, Integer>("adidas mens speed cut track suit", 920744865));
input.add(new Pair<String, Integer>("liz and co lulu zipper boots", 1630836380));
input.add(new Pair<String, Integer>("black navy  lightweight oxford shoes", 906123996));
input.add(new Pair<String, Integer>("liz and co farley tall boots", 1639960280));
input.add(new Pair<String, Integer>("call it spring karpin  pullon boots", 1629938981));
input.add(new Pair<String, Integer>("ugg australia bailey bow boots", 1594029054));
input.add(new Pair<String, Integer>("dream chasers  jacket", 1631247949));
input.add(new Pair<String, Integer>("guess military  tiewaist coat", 1629993909));
input.add(new Pair<String, Integer>("madden girl allstaar womens zip boots", 1581506993));
input.add(new Pair<String, Integer>("michael womens shoes", 1590598743));
input.add(new Pair<String, Integer>("sonoma life style suede midcalf boots women", 1617302927));

		input.add(new Pair<String, Integer>("absolute pnf300 power noise filterground loop isolator with adjustable controls", 1521965454));
		input.add(new Pair<String, Integer>("sennheiser ie8 stereo earbuds", 211969101));
		input.add(new Pair<String, Integer>("sanus vlmf109 motorized full motion mount for tvs 37 60 up to 110 lbs", 214893385));
		input.add(new Pair<String, Integer>("s2fmcy003 earset stereo earbud binaural open miniphone black", 214972916));
		input.add(new Pair<String, Integer>("boconi bags and leather bryant safari bag carry on luggage brown", 1646568995));
		input.add(new Pair<String, Integer>("diesel derik pant jyt mens pajama gray", 1645725530));
		input.add(new Pair<String, Integer>("sole society gina sandal", 1633021283));
		input.add(new Pair<String, Integer>("toms bimini stitchout slipon women", 1633012540));
		input.add(new Pair<String, Integer>("the north face womens p r tka 100 microvelour glacier 14 zip tnf blackjk3 medium", 1618022193));
		input.add(new Pair<String, Integer>("robert graham manuel dress shirt mens long sleeve button up blue", 1631119485));

		input.add(new Pair<String, Integer>("b o c leesa", 1584193288));
			input.add(new Pair<String, Integer>("blair stirrup pants", 1525621516));
			input.add(new Pair<String, Integer>("donna karan shirtdress", 1463793963));
			input.add(new Pair<String, Integer>("columbia sportswear terminal tackle shirt", 1661238030));
			input.add(new Pair<String, Integer>("carters jersey pajamas", 1573999243));
			input.add(new Pair<String, Integer>("vince camuto dena", 1626272001));
			input.add(new Pair<String, Integer>("pistil hudson knit hats", 1660874149));
			input.add(new Pair<String, Integer>("naturalizer trinity wide shaft womens zip", 1569191459));
			input.add(new Pair<String, Integer>("bare traps chelby womens sandals", 1513387756));
			input.add(new Pair<String, Integer>("overland storage hard drive 1 tb hotswap", 212107374));
			input.add(new Pair<String, Integer>("humminbird indash depth finder", 1616650484));
			input.add(new Pair<String, Integer>("grepsr800 gre dig scanner", 215723895));
			input.add(new Pair<String, Integer>("humminbird kayak transducer", 215392426));
			input.add(new Pair<String, Integer>("garmin nuvi suction cup mount ", 215728710));
			input.add(new Pair<String, Integer>("crosley radio black", 215662289));

		    input.add(new Triple<String, Integer, String >("avaya ip telephone", 1440488008, "lucent phone system"));
			input.add(new Triple<String, Integer, String>("clarks trolley womens shoes", 1581854074, "clark womens shoes"));
			input.add(new Triple<String, Integer, String>("mens evans shoes imperial deer", 210808400, "lb evans slippers"));
			input.add(new Triple<String, Integer, String>("ugg classic bow shorty gloves", 1665094898, "leather gloves women"));
			input.add(new Triple<String, Integer, String>("jumping beans man tee baby", 1667155332, "jumping beans clothing"));
			input.add(new Triple<String, Integer, String>("asics mens shoes", 1630208773, "asics mens running shoes"));
			input.add(new Triple<String, Integer, String>("oakley hoodie mens fleece", 1656661466, "hoodies for men"));
			input.add(new Triple<String, Integer, String>("usb sound control digital voice recorder", 1654662662, "digital voice recorder with usb"));
			input.add(new Triple<String, Integer, String>("motorola bluetooth headset", 215376254, "motorola oasis bluetooth headset"));
			input.add(new Triple<String, Integer, String>("sony sound bar home theater system", 215450833, "sony sound bar"));
			input.add(new Triple<String, Integer, String>("jvc full hd everio camcorder", 1664479999, "jvc everio camcorder"));
		 */
		
		 input.add(new Triple<String, Integer, String>("dr martens beckett laceup boots", 1651452641, "doc martin shoes"));
		 input.add(new Triple<String, Integer, String>("pioneer cd changer",204654672, "pioneer cd player"));
		 input.add(new Triple<String, Integer, String>("tablet handler strap and desk mount", 1634326303, "tablet holder"));
		 input.add(new Triple<String, Integer, String>("sockwell loden womens overthecalf socks", 1644572708, "compression stockings, support stockings"));
		 input.add(new Triple<String, Integer, String>("nike eclipse womens shoes", 1657807048, "nike eclipse ii women s shoe"));
		 input.add(new Triple<String, Integer, String>("cherokee workwear womens scrub pant black stall",211643295, "cherokee workwear scrubs"));
		 input.add(new Triple<String, Integer, String>("columbia sportswear jacket ", 1667381935, "columbia omni heat"));
		 input.add(new Triple<String, Integer, String>("adidas adipure jacket", 1040124787, "adidas track jacket"));
		 input.add(new Triple<String, Integer, String>("clarks may orchid womens shoes", 1585805688, "clarks loafers"));
		 input.add(new Triple<String, Integer, String>("levis pants empire blue", 1670283141, "skinny jeans for guys"));
		 input.add(new Triple<String, Integer, String>("nike jordan black cat tee", 1653598764, "jordan black cat"));
		 input.add(new Triple<String, Integer, String>("obermeyer womens kassandra down coat", 1670629180, "down winter coats"));
/*
		 input.add(new Triple<String, Integer, String>("paramax  surround sound", 835422569, "paramax im3"));
		 input.add(new Triple<String, Integer, String>("mia quincy wedge", 1285886230, "mia quincy wedge"));
		 input.add(new Triple<String, Integer, String>("able planet headphones", 1648522886, "able planet nc210g"));
		 input.add(new Triple<String, Integer, String>("samsung replacement lamp", 695793593, "lamp code bp96"));
		 input.add(new Triple<String, Integer, String>("paul green emerson boot castagno", 1313967918, "paul green emerson boot"));
		 input.add(new Triple<String, Integer, String>("bandolino caresse boots", 1448643623, "bandolino caresse boots"));
		 input.add(new Triple<String, Integer, String>("nine west modiley", 1365998968, "nine west modiley"));
		 input.add(new Triple<String, Integer, String>("converse chuck taylor  bisay", 1555900934, "turquoise chuck taylors"));
		 input.add(new Triple<String, Integer, String>("gentle souls bay leaf flats", 1436175162, "gentle souls bay leaf"));
		 input.add(new Triple<String, Integer, String>("sauce hockey  back hat", 1644440355, "sauce hockey discount code"));
		 input.add(new Triple<String, Integer, String>("aravon farren oxford shoes", 1644573438, "aravon wef07sh"));
	*/	 input.add(new Triple<String, Integer, String>("kooba crosby hobo handbags", 1326503038, "kooba crosby"));
		 input.add(new Triple<String, Integer, String>("bcbgmaxazria sheath dress", 1313949777, "bcbgmaxazria illusion bodice ruched sheath dress"));
		 input.add(new Triple<String, Integer, String>("billabong boardshorts trunks", 1316823074, "la siesta boardshorts"));
		 input.add(new Triple<String, Integer, String>("mootsies tootsies boot", 1503727310, "mootsies tootsies draker"));
		 input.add(new Triple<String, Integer, String>("nine west bootie", 1503730060, "nine west drina"));
		 input.add(new Triple<String, Integer, String>("playtex support cotton ", 1331026244, "playtex t723"));
		 input.add(new Triple<String, Integer, String>("fossil morgan satchel taupe", 1355165745, "fossil morgan satchel"));
		 input.add(new Triple<String, Integer, String>("katonah womens boots brown", 1420057844, "boc katonah boots"));
		 input.add(new Triple<String, Integer, String>("boot cut jeans supernova", 1363356262, "levis 527 supernova"));
		 input.add(new Triple<String, Integer, String>("steve madden buckie boot", 1313965918, "steve madden buckie boot"));
		 input.add(new Triple<String, Integer, String>("charlies horse tshirt", 1428490587, "charlie s horse shirt"));
		 input.add(new Triple<String, Integer, String>("igloo little playmate ice chest", 205421625, "igloo little playmate"));
		 input.add(new Triple<String, Integer, String>("mark nason boot", 1313951044, "mark nason rudd"));



	}

	public static void main(String[] args){
		//ProductFinderInAWebPage init = new ProductFinderInAWebPage("C:/workspace/relevanceEngine/src/test/resources");
		ReviewBuilderRunner r = new ReviewBuilderRunner();
		WebPageReviewExtractor extractor = new WebPageReviewExtractor("C:/workspace/relevanceEngine/src/test/resources");
		for(Triple query_ID : r.input ){
			String query = (String) query_ID.getFirst();
			List<String> res = extractor.formReviewsForAProduct(query);

			ProfileReaderWriter.writeReportListStr(res, "formedReviewSentences"+ query +".csv");
		}



	}
}