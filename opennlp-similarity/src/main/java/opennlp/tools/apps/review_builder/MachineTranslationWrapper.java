package opennlp.tools.apps.review_builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MachineTranslationWrapper  {
	private String translatorURL = "http://mymemory.translated.net/api/get?q=";
	
	public String translate(String sentence, String lang2lang){
		if (sentence==null)
			return null;
		String request = translatorURL + sentence.replace(' ','+') + "&langpair="+lang2lang;//"en|es";
		JSONArray arr=null, prodArr = null, searchURLviewArr = null;
		try {
			URL urlC = new URL(request);
			URLConnection connection = urlC.openConnection();

			String line;
			String result = "";
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			int count = 0;
			while ((line = reader.readLine()) != null)
			{
				result+=line;
				count++;
			}
			JSONObject rootObject = new JSONObject(result);
			JSONObject  findObject = rootObject.getJSONObject("responseData");
			String transl = findObject.getString("translatedText");
			try {
				transl = URLDecoder.decode(transl, "UTF-8");
			} catch (Exception e) {
				
			}
			
			return transl;
			
		} catch (MalformedURLException e) {
			
			e.printStackTrace();
			return null;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;			
		} catch (IOException e) {
			e.printStackTrace();
			return null;			
		}	
		
	}
	
	public String rePhrase(String sentence){
		System.out.println("orig = "+ sentence);
		String transl = translate(sentence, "en|es");
		System.out.println("tranls = "+transl);
		String inverseTransl = translate(transl, "es|en");
		if (!(inverseTransl.indexOf("NO QUERY SPECIFIED")>-1) && !(inverseTransl.indexOf("INVALID LANGUAGE")>-1) && !(inverseTransl.indexOf("MYMEMORY WARNING")>-1))
			return inverseTransl;
		else 
			return sentence;
	}
	
	
	
	public static void main(String[] args){
		MachineTranslationWrapper rePhraser = new MachineTranslationWrapper();
		
		System.out.println(rePhraser.translate("I went to the nearest bookstore to buy a book written by my friend and his aunt", "en|ru"));
		
		System.out.println(rePhraser.rePhrase("I went to the nearest bookstore to buy a book written by my friend and his aunt"));

	}
		
}
