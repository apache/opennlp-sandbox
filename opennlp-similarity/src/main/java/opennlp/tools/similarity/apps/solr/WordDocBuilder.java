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
package opennlp.tools.similarity.apps.solr;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.billylieurance.azuresearch.AzureSearchImageResult;
import net.billylieurance.azuresearch.AzureSearchResultSet;

import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.CTFootnotes;
import org.docx4j.wml.CTFtnEdn;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.HitBase;

public class WordDocBuilder{
	protected static final String IMG_REL_PATH = "images/";
	protected final BingQueryRunner imageSearcher = new BingQueryRunner();
	protected String absPath;
	
	public WordDocBuilder(){
	absPath = new File(".").getAbsolutePath();
	absPath = absPath.substring(0, absPath.length()-1);
	}
	
	public String buildWordDoc(List<HitBase> content, String title){
		
		String outputDocFilename =  absPath+"/written/"+ title.replace(' ','_').replace('\"', ' ').trim()+ ".docx";
		
		WordprocessingMLPackage wordMLPackage;
		try {
			wordMLPackage = WordprocessingMLPackage.createPackage();
			wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Title", title);
			for(HitBase para: content){
				
				wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Subtitle",
						para.getTitle());
				String paraText = para.getFragments().toString().replace("[", "").replace("]", "").replace(" | ", "")
						.replace(".,", ".").replace(".\"", "\"").replace(". .", ".")
						.replace(",.", ".");
				wordMLPackage.getMainDocumentPart().addParagraphOfText(paraText);
				
				addImageByImageTitleToPackage(wordMLPackage, para.getTitle());
			}
			
			//File file = new File("C:/ma/personal/argCamp.png");
	        //byte[] bytes = convertImageToByteArray(file);
	        //addImageToPackage(wordMLPackage, bytes);
	        
			wordMLPackage.save(new File(outputDocFilename));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return outputDocFilename;
	}
	
	private void addImageByImageTitleToPackage(
			WordprocessingMLPackage wordMLPackage, String title) {
		AzureSearchResultSet<AzureSearchImageResult> res = imageSearcher.runImageSearch(title);
		for (AzureSearchImageResult anr : res){
			String url = anr.getMediaUrl();
			addImageByURLToPackage( wordMLPackage, url);
			return;
		}
		
	}

	private void addImageByURLToPackage(WordprocessingMLPackage wordMLPackage,
            String url){
		String destinationFile = url.replace("http://", "").replace("/", "_");
		saveImageFromTheWeb(url, absPath+IMG_REL_PATH+destinationFile);
		File file = new File(absPath+destinationFile);
		try {
			byte[] bytes = convertImageToByteArray(file);
			addImageToPackage(wordMLPackage, bytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
     *  Docx4j contains a utility method to create an image part from an array of
     *  bytes and then adds it to the given package. In order to be able to add this
     *  image to a paragraph, we have to convert it into an inline object. For this
     *  there is also a method, which takes a filename hint, an alt-text, two ids
     *  and an indication on whether it should be embedded or linked to.
     *  One id is for the drawing object non-visual properties of the document, and
     *  the second id is for the non-visual drawing properties of the picture itself.
     *  Finally, we add this inline object to the paragraph and the paragraph to the
     *  main document of the package.
     *
     *  @param wordMLPackage The package we want to add the image to
     *  @param bytes         The bytes of the image
     *  @throws Exception    Sadly the createImageInline method throws an Exception
     *                       (and not a more specific exception type)
     *                       
     *                       
     */
    protected static void addImageToPackage(WordprocessingMLPackage wordMLPackage,
                            byte[] bytes) throws Exception {
			BinaryPartAbstractImage imagePart =
					BinaryPartAbstractImage.createImagePart(wordMLPackage, bytes);

			int docPrId = 1;
			int cNvPrId = 2;
					Inline inline = imagePart.createImageInline("Filename hint",
							"Alternative text", docPrId, cNvPrId, false);

			P paragraph = addInlineImageToParagraph(inline);
			wordMLPackage.getMainDocumentPart().addObject(paragraph);
    }
 
    /**
     *  We create an object factory and use it to create a paragraph and a run.
     *  Then we add the run to the paragraph. Next we create a drawing and
     *  add it to the run. Finally, we add the inline object to the drawing and
     *  return the paragraph.
     *
     * @param   inline The inline object containing the image.
     * @return  the paragraph containing the image
     */
    private static P addInlineImageToParagraph(Inline inline) {
        // Now add the in-line image to a paragraph
    		org.docx4j.wml.ObjectFactory factory = new org.docx4j.wml.ObjectFactory();
        P paragraph = factory.createP();
        R run = factory.createR();
        paragraph.getContent().add(run);
        Drawing drawing = factory.createDrawing();
        run.getContent().add(drawing);
        drawing.getAnchorOrInline().add(inline);
        return paragraph;
    }
    
    private static CTFootnotes createFootnote(P paragraph){
    	org.docx4j.wml.ObjectFactory factory = new org.docx4j.wml.ObjectFactory();
    	CTFootnotes fn = factory.createCTFootnotes();
    	fn.setParent(paragraph);
    	
    	//STFtnEdn sTFtnEdn = factory.createSTFtnEdn();
    	CTFtnEdn fe = factory.createCTFtnEdn();
    	fe.setParent(paragraph);
    	return fn;
    }
 
    /**
     * Convert the image from the file into an array of bytes.
     *
     * @param file  the image file to be converted
     * @return      the byte array containing the bytes from the image
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected static byte[] convertImageToByteArray(File file) throws FileNotFoundException, IOException {
        try (InputStream is = new FileInputStream(file)) {
					long length = file.length();
					// You cannot create an array using a long, it needs to be an int.
					if (length > Integer.MAX_VALUE) {
							System.out.println("File too large!!");
					}
					byte[] bytes = new byte[(int)length];
					int offset = 0;
					int numRead;
					while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
							offset += numRead;
					}
					// Ensure all the bytes have been read
					if (offset < bytes.length) {
							System.out.println("Could not completely read file "
													+file.getName());
					}
					return bytes;
				}
    }

    public static void saveImageFromTheWeb(String imageUrl, String destinationFile) {
			final File f = new File(destinationFile);
			if (!f.exists()) {
				try {
					f.createNewFile();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			try (InputStream is = new URI(imageUrl).toURL().openStream();
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
    
    public static void main(String[] args){
    	WordDocBuilder b = new WordDocBuilder();
    	List<HitBase> content = new ArrayList<>();
    	for(int i = 0; i<10; i++){
    		HitBase h = new HitBase();
    		h.setTitle("albert einstein "+i);
    		List<Fragment> frs = new ArrayList<>();
    		frs.add(new Fragment(" content "+i, 0));
    		h.setFragments(frs);
    		content.add(h);
    	}
    	
    	b.buildWordDoc(content, "mytitle");
    }
}
