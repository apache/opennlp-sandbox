package opennlp.tools.enron_email_recognizer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class EmailTrainingSetFormer {
	static String dataDir = "/Users/bgalitsky/Downloads/",
			//enron_with_categories/",
			fileListFile = "cats4_11-17.txt",
			destinationDir = "/Users/bgalitsky/Documents/ENRON/data11_17/";

	//enron_with_categories/5/70665.cats:4,10,1
	public static void  createPosTrainingSet(){
		try {
			List<String> lines = FileUtils.readLines(new File(dataDir+fileListFile));
			for(String l: lines){
				Integer endOfFname = l.indexOf('.'),
						startOfFname = l.lastIndexOf('/');
				String filenameOld =dataDir+ l.substring(0, endOfFname)+".txt";

				String content = normalize(new File(filenameOld));

				String filenameNew = destinationDir  + l.substring(startOfFname+1, endOfFname)+".txt";
				//FileUtils.copyFile(new File(filenameOld), new File(filenameNew));
				FileUtils.writeStringToFile(new File(filenameNew), content);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String origFolder = "maildir_ENRON_EMAILS", newFolder = "data11_17";

	public static String normalize(File f){
		String content="";
		try {
			content = FileUtils.readFileToString(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] lines = content.split("\n");
		StringBuffer buf = new StringBuffer();
		for(String l: lines){
			boolean bAccept = true;
			for(String h: EmailNormalizer.headers){
				if (l.startsWith(h)){
					bAccept = false;
				}
			}
			for(String h: EmailNormalizer.prohibitedStrings){
				if (l.indexOf(h)>0){
					bAccept = false;
				}
			}
			if (bAccept)
				buf.append(l+"\n");
		}
		return buf.toString();
	}

	public static void main(String[] args){
		EmailTrainingSetFormer.createPosTrainingSet();
	}
}
