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

import java.util.Iterator;
import java.util.List;

import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.JaxbXmlPart;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.relationships.Relationships;
import org.docx4j.wml.Comments;
import org.docx4j.wml.ObjectFactory;

//import util.RelationshipName;

public class Comment {

    private WordprocessingMLPackage wordMlPackage;
    private boolean relSet = false;

    public Comment(WordprocessingMLPackage wordMLPack) {
        this.wordMlPackage = wordMLPack;
        setCommentRel();
    }

    private void setCommentRel() {
        if (!commentRelSet()) {
            CommentsPart cp;
            try {
                cp = new CommentsPart();
                // Part must have minimal contents
                org.docx4j.wml.ObjectFactory wmlObjectFactory = new ObjectFactory();
                wordMlPackage.getMainDocumentPart().addTargetPart(cp);
            } catch (InvalidFormatException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean commentRelSet() {
        Relationship relShip;
        boolean relSet = false;
        if (!relSet) {
            JaxbXmlPart<Relationships> jxpRelShips = wordMlPackage
                    .getMainDocumentPart().getOwningRelationshipPart();
            Relationships pk = jxpRelShips.getJaxbElement();

            List<Relationship> mc = pk.getRelationship();

            Iterator<Relationship> it = mc.iterator();
       /*     while (it.hasNext() && !relSet) {
                relShip = it.next();
                if (relShip.getValue().equalsIgnoreCase(
                        RelationshipName.commentIdentifier)) {
                    relSet = true;
                }
            }*/
        }
        return relSet;
    }
    
    public static void main(String[] args) throws Exception {

        // Create a package
        WordprocessingMLPackage wmlPack = new WordprocessingMLPackage();

        // Create main document part
        MainDocumentPart wordDocumentPart = new MainDocumentPart();      
        
        // Create main document part content
        org.docx4j.wml.ObjectFactory factory = Context.getWmlObjectFactory();
        org.docx4j.wml.Body  body = factory.createBody();      
        org.docx4j.wml.Document wmlDocumentEl = factory.createDocument();
        
        wmlDocumentEl.setBody(body);
        wordDocumentPart.setJaxbElement(wmlDocumentEl);
        wmlPack.addTargetPart(wordDocumentPart);
        
        CommentsPart cp = new CommentsPart();
        // Part must have minimal contents
        Comments comments = factory.createComments();
        cp.setJaxbElement(comments);
        
        wordDocumentPart.addTargetPart(cp);
        
        // Now you can add comments to your comments part,
        // and comment refs in your main document part   

        wmlPack.save(new java.io.File(System.getProperty("user.dir")+ "/out-m.docx"));       
      }
    
}
