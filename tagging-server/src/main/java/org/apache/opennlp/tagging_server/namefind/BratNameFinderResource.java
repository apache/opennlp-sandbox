package org.apache.opennlp.tagging_server.namefind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.tagging_server.ServiceUtil;
import org.osgi.framework.ServiceReference;

@Path("/bratner")
public class BratNameFinderResource {

  public static class NameAnn {
    public int[][] offsets;
    public String[] texts;
    public String type;
  }

  private static int findNextNonWhitespaceChar(CharSequence s, int beginOffset, int endOffset) {
    
    for (int i = beginOffset; i < endOffset; i++) {
       if (!Character.isSpaceChar(s.charAt(i))) {
         return i;
       }
    }
    
    return -1;
  }
  
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, NameAnn> findNames(@QueryParam("model") String modelName, String text) {
    
    ServiceReference nerService = ServiceUtil
        .getModelServiceReference(RawTextNameFinderFactory.class, modelName);
    
    try {

      RawTextNameFinderFactory nameFinderFactory = ServiceUtil.getService(
          nerService, RawTextNameFinderFactory.class);

      SentenceDetector sentDetect = nameFinderFactory.createSentenceDetector();
      Tokenizer tokenizer = nameFinderFactory.createTokenizer();
      TokenNameFinder nameFinders[] = nameFinderFactory.createNameFinders();

      Span sentenceSpans[] = sentDetect.sentPosDetect(text);

      Map<String, NameAnn> map = new HashMap<String, NameAnn>();

      int indexCounter = 0;

      for (int i = 0; i < sentenceSpans.length; i++) {
        // offset of sentence gets lost here!
        Span tokenSpans[] = tokenizer.tokenizePos(sentenceSpans[i]
            .getCoveredText(text).toString());

        String tokens[] = Span.spansToStrings(tokenSpans, text);
        
        for (TokenNameFinder nameFinder : nameFinders) {
          Span names[] = nameFinder.find(tokens);

          for (Span name : names) {
            int beginOffset = tokenSpans[name.getStart()].getStart()
                + sentenceSpans[i].getStart();
            int endOffset = tokenSpans[name.getEnd() - 1].getEnd()
                + sentenceSpans[i].getStart();

            // create a list of new line indexes
            List<Integer> newLineIndexes = new ArrayList<Integer>();
            
            // TODO: Code needs to handle case that there are multiple new lines in a row
            
            boolean inNewLineSequence = false;
            for (int ci = beginOffset; ci < endOffset; ci++) {
              if (text.charAt(ci) == '\n' || text.charAt(ci) == '\r') {
                if (!inNewLineSequence) {
                  newLineIndexes.add(ci);
                }
                inNewLineSequence = true;
              }
              else {
                inNewLineSequence = false;
              }
            }
            
            List<String> textSegments = new ArrayList<String>();
            List<int[]> spanSegments = new ArrayList<int[]>();
            
            int segmentBegin = beginOffset;
            
            for (int newLineOffset : newLineIndexes) {
              // create segment from begin to offset
              textSegments.add(text.substring(segmentBegin, newLineOffset));
              spanSegments.add(new int[]{segmentBegin, newLineOffset});
              
              segmentBegin = findNextNonWhitespaceChar(text, newLineOffset + 1, endOffset);
              
              if (segmentBegin == -1) {
                break;
              }
            }
            
            // create left over segment
            if (segmentBegin != -1) {
              textSegments.add(text.substring(segmentBegin, endOffset));
              spanSegments.add(new int[]{segmentBegin, endOffset});
            }
            
            NameAnn ann = new NameAnn();
            ann.texts = textSegments.toArray(new String[textSegments.size()]);
            ann.offsets = spanSegments.toArray(new int[spanSegments.size()][]);
            ann.type = name.getType();
            
            map.put(Integer.toString(indexCounter++), ann);
          }
        }
      }
      
      return map;
    
    } finally {
      ServiceUtil.releaseService(nerService);
    }
  }
}
