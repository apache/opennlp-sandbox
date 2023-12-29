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
package opennlp.tools.doc_classifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

/*
 * This utility gets 'training_corpus' as input and creates a new version of training_corpus with verified files.
 * Verified => classified by existing training set as only belonging to its target category, no other categories, not empty.
 */
public class DocClassifierTrainingSetMultilingualExtender {
	private static final String LANG_TEMPL = "l_a_n_g";

	public static String projectHome = new File(".").getAbsolutePath().replace("contentinspection/.", "");
	public static String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/main/resources";
	final DocClassifier classifier;
	private String sourceDir = null, destinationDir = null;
	//interwiki-fr"><a href="http://fr.wikipedia.org/wiki/Niveau_d%27%C3%A9nergie" title="Niveau d&#39;énergie – French" lang="fr" 
	private static final String[][] MULTILINGUAL_TOKENS = new String[][]{{"interwiki-fr\"><a href=\"", "lang=\"fr\""},
		{"interwiki-es\"><a href=\"", "lang=\"es\""}, {"interwiki-de\"><a href=\"", "lang=\"de\""} };
	
	private static final String[] LANGS = new String[]{ "fr", "es", "de"};

	protected final ArrayList<File> queue = new ArrayList<>();

	public DocClassifierTrainingSetMultilingualExtender(String resource) {

		classifier = new DocClassifier("", new JSONObject());

	}
	private final int FRAGMENT_LENGTH = 500;

	protected void addFiles(File file) {

		try {
			if (!file.exists()) {
				System.out.println(file + " does not exist.");
			}
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					try {
						addFiles(f);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				queue.add(file);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public List<String> extractEntriesFromSpecial_Export(String filename){
		List<String> filteredEntries = new ArrayList<>();
		String content=null;
		try {
			content = FileUtils.readFileToString(new File(filename), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] entries = StringUtils.substringsBetween(content, "[[", "]]");
		for(String e: entries){
			if (e.startsWith("Kategorie") || e.startsWith("Category") || e.startsWith("d:") || e.startsWith("User") 
					||e.startsWith("Portal")  )
				continue;
			if (e.indexOf(':')>-1)
				continue;
			
			if (e.contains(":"))
				continue;
			int endofEntry = e.indexOf('|');
			if (endofEntry>-1) e = e.substring(0, endofEntry);
			filteredEntries.add(e);
		}
		
		filteredEntries = new ArrayList<> (new HashSet<>(filteredEntries));
		return filteredEntries;
	}

	public void processDirectory(String fileName) throws IOException {
		addFiles(new File(fileName));
	//	FileUtils.deleteDirectory(new File(destinationDir));
	//	FileUtils.forceMkdir(new File(destinationDir));
		

		for (File f : queue) {
			String content;
			try {// should be wiki page
				//if (f.getName().toString().toLowerCase().indexOf(" wiki")<0 && 
						
			//	if (		f.getAbsolutePath().indexOf("wiki-new")<0)
			//		continue;
				// should not be a page already derived by a link
				if (f.getName().toLowerCase().contains(".html_"))
					continue;
				
				System.out.println("processing "+f.getName());
				content = FileUtils.readFileToString(f, "utf-8");
				int langIndex =0;
				for(String[] begEnd: MULTILINGUAL_TOKENS){
					String urlDirty = StringUtils.substringBetween(content, begEnd[0], begEnd[1]);
					String url = StringUtils.substringBefore(urlDirty, "\"");

					if (url!=null){
						if (!url.startsWith("http:"))
						    url = "https:"+url;
						
						String[] parts  = url.split("/");
						String multilingualName = parts[parts.length-1];
						String destFileName = f.getAbsolutePath().replace(sourceDir, destinationDir).replace(" - Wikipedia, the free encyclopedia.html", "-wiki")+"."+ LANGS[langIndex]+"."
								+"_"+multilingualName+".html";
						if (!new File(destFileName).exists()){
							saveDocFromTheWeb(url, destFileName);
							System.out.println(f.getName()+ " => "+destFileName);
						}
					} else {
						System.out.println("Unable to extract multilingual urls for'" + LANGS[langIndex] +"' from file "+ f.getCanonicalPath());
					}
					langIndex++;
				}
			} catch (Exception ee) {
				ee.printStackTrace();
			}
		}

		queue.clear();
	}

	private void copyURLToFile(URL url, File file) {
		try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				 FileOutputStream fos = new FileOutputStream(file.getAbsolutePath()) ) {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void crawlWikiOnTopic( String filename, String lang, String destinationDir){
		List<String> entries = extractEntriesFromSpecial_Export(filename);
		for(String e: entries){
			String wikiUrlsTemplate = "https://" + LANG_TEMPL + ".wikipedia.org/wiki/";
			String url  = wikiUrlsTemplate.replace(LANG_TEMPL, lang) + e;
			saveDocFromTheWeb(url, destinationDir+e.replace(' ', '_')+".html"); 
		}
	}
	
	public static void saveDocFromTheWeb(String docUrl, String destinationFile) {
		if (!new File(destinationFile).exists()) {
			try {
				new File(destinationFile).createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e.getLocalizedMessage(), e);
			}
		}

		try (InputStream is = new URI(docUrl).toURL().openStream();
				 OutputStream os = new FileOutputStream(destinationFile)) {

			byte[] b = new byte[2048];
			int length;

			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.err
			.println("Verifier accepts two arguments: [0] - input 'training_corpus' folder, "
					+ "[1] - output 'training_corpus' folder . "
					+ "All paths should include category name as a part of full path string, such as '/computing/' " );
			System.exit(0);
		}

		DocClassifierTrainingSetMultilingualExtender runner = new DocClassifierTrainingSetMultilingualExtender(null);
		
		if (args.length==2) {
			runner.sourceDir = args[0]; runner.destinationDir = args[1];
			runner.sourceDir =
					"/Users/borisgalitsky/Documents/svm_tk_july2015/milkyway/training_corpus_multilingual_verif";
			runner.destinationDir =
					"/Users/borisgalitsky/Documents/new_corpus/milkyway/training_corpus_new_multilingual_refined";

			try {
				runner.processDirectory( runner.sourceDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {  
			runner.crawlWikiOnTopic("/Users/borisgalitsky/Downloads/Wikipedia-20150730124756.xml",
					//Wikipedia-20150730053619.xml",
					////Wikipedia-20150730044602.xml",
					//Wikipedia-20150729103933.xml",
					//Wikipedia-20150729103933.xml",
					// "Wikipedia-20150728193126.xml",
					//Wikipedia-20150728183128.xml",
					"en", 
					"/Users/borisgalitsky/Documents/merged_svm_tk/milkyway/training_corpus_new_multilingual/business/wiki/wiki-new/");
		}

	}
}
