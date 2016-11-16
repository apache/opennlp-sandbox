package opennlp.tools.parse_thicket.request_response_recognizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.similarity.apps.BingQueryRunner;

import org.apache.commons.io.FileUtils;

public class YahooAnswersTrainingSetCreator {
	protected List<File> queuePos = new ArrayList<File>(), queueNeg = new ArrayList<File>();
	public static String origFilesDir = "/Users/bgalitsky/Downloads/NewCategoryIdentification/text";
	//private BingQueryRunner searcher = new BingQueryRunner();
	protected void addFilesPos(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				addFilesPos(f);
				System.out.println(f.getName());
			}
		} else {
			queuePos.add(file);
		}
	}
	
	protected void addFilesNeg(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				addFilesNeg(f);
				System.out.println(f.getName());
			}
		} else {
			queueNeg.add(file);
		}
	}
	
	public void formNegTrainingSet(String posPath , String negPath){
		 if (!new File(negPath).exists())
			 new File(negPath).mkdir();
		 
		addFilesPos(new File(posPath));
		for(int i=0; i< queuePos.size()-1; i+=2){ //take two files at a time
			File f1 = queuePos.get(i), f2 = queuePos.get(i+1);
			String content1 = null, content2 = null;
            try {
	            content1 = FileUtils.readFileToString(f1);
	            content2 = FileUtils.readFileToString(f2);
            } catch (IOException e) {
	            e.printStackTrace();
            }
			String[] portions1 = content1.split("\n\n");
			String[] portions2 = content2.split("\n\n");

			portions1 = splitIntoRR(portions1, content1);
			portions2 = splitIntoRR(portions2, content2);
			if (portions1==null || portions2==null)
				continue;
			// do cross-breeding
			try {
	            FileUtils.writeStringToFile(new File(negPath+"/" + f1.getName()+".txt"),
	            		portions1[0] + "\n\n" + portions2[1] );
	            FileUtils.writeStringToFile(new File(negPath+"/" + f2.getName()+".txt"),
	            		portions2[0] + "\n\n" + portions1[1] );
            } catch (IOException e) {
	            e.printStackTrace();
            }
		}
		
		
	}
	private String[] splitIntoRR(String[] portions, String content) {
		if (portions.length<2 ){
			portions = content.replace("?","#_#").split("#_#");
		}
		if (portions.length<2 ){
			portions = content.split("\n");
		}
		if (portions.length<2)
			return null;
		if (portions.length>2){
			String q= "", a = "";
			boolean bQ = true;
			for(int p=0; p<portions.length; p++){
				if ( bQ )
					q+=portions[p]+" \n";
				else
					a +=portions[p]+" \n";
				
				if (portions[p].endsWith("?")){
					bQ=false;
				}

			}
			if (!bQ) {
				portions = new String[2];
				portions[0] = q;
				portions[1] = a;
			} else
				return null;
		}
		
		return portions;
    }
	
	public static void main(String[] args){
		String dir = YahooAnswersTrainingSetCreator.origFilesDir;
		new YahooAnswersTrainingSetCreator().formNegTrainingSet(dir, dir.replace("/text", "/neg_text"));
	}
}
