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

package org.apache.opennlp.wikinews_importer;

import info.bliki.htmlcleaner.ContentToken;
import info.bliki.htmlcleaner.TagNode;
import info.bliki.wiki.filter.ITextConverter;
import info.bliki.wiki.filter.WPList;
import info.bliki.wiki.filter.WPTable;
import info.bliki.wiki.model.Configuration;
import info.bliki.wiki.model.IWikiModel;
import info.bliki.wiki.model.ImageFormat;
import info.bliki.wiki.model.WikiModel;
import info.bliki.wiki.tags.WPATag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parse mediawiki markup to strip the formatting info and extract a simple text
 * version suitable for NLP along with header, paragraph and link position
 * annotations.
 * 
 * Use the {@code #convert(String)} and {@code #getWikiLinks()} methods.
 * 
 * Due to the constraints imposed by the {@code ITextConverter} /
 * {@code WikiModel} API, this class is not thread safe: only one instance
 * should be run by thread.
 */
public class AnnotatingMarkupParser implements ITextConverter {

    public static final String HREF_ATTR_KEY = "href";

    public static final String WIKILINK_TITLE_ATTR_KEY = "title";

    public static final String WIKILINK_TARGET_ATTR_KEY = "href";

    public static final String WIKIOBJECT_ATTR_KEY = "wikiobject";

    public static final Set<String> PARAGRAPH_TAGS = new HashSet<String>(
            Arrays.asList("p"));

    public static final Set<String> HEADING_TAGS = new HashSet<String>(
            Arrays.asList("h1", "h2", "h3", "h4", "h5", "h6"));

    public static final Pattern INTERWIKI_PATTERN = Pattern.compile("http://[\\w-]+\\.wikipedia\\.org/wiki/.*");

    protected final List<Annotation> wikilinks = new ArrayList<Annotation>();

    protected final List<Annotation> headers = new ArrayList<Annotation>();

    protected final List<Annotation> paragraphs = new ArrayList<Annotation>();

    protected String languageCode = "en";

    protected final WikiModel model;

    protected String redirect;

    protected String text;

    protected static final Pattern REDIRECT_PATTERN = Pattern.compile("^#REDIRECT \\[\\[([^\\]]*)\\]\\]");

    public AnnotatingMarkupParser() {
        model = makeWikiModel(languageCode);
    }

    public AnnotatingMarkupParser(String languageCode) {
        this.languageCode = languageCode;
        model = makeWikiModel(languageCode);
    }

    public WikiModel makeWikiModel(String languageCode) {
        return new WikiModel(String.format(
                "http:/%s.wikipedia.org/wiki/${image}", languageCode),
                String.format("http://%s.wikipedia.org/wiki/${title}",
                        languageCode)) {
            @Override
            public String getRawWikiContent(String namespace,
                    String articleName, Map<String, String> templateParameters) {
                // disable template support
                // TODO: we need to readd template support at least for dates
                return "";
            }
        };
    }


    public void nodesToText(List<? extends Object> nodes, Appendable buffer,
            IWikiModel model) throws IOException {
        CountingAppendable countingBuffer;
        if (buffer instanceof CountingAppendable) {
            countingBuffer = (CountingAppendable) buffer;
        } else {
            // wrap
            countingBuffer = new CountingAppendable(buffer);
        }

        if (nodes != null && !nodes.isEmpty()) {
            try {
                int level = model.incrementRecursionLevel();
                if (level > Configuration.RENDERER_RECURSION_LIMIT) {
                    countingBuffer.append("Error - recursion limit exceeded"
                            + " rendering tags in PlainTextConverter#nodesToText().");
                    return;
                }
                for (Object node : nodes) {
                    if (node instanceof WPATag) {
                        // extract wikilink annotations
                        WPATag tag = (WPATag) node;
                        String wikilinkLabel = (String) tag.getAttributes().get(
                                WIKILINK_TITLE_ATTR_KEY);
                        String wikilinkTarget = (String) tag.getAttributes().get(
                                WIKILINK_TARGET_ATTR_KEY);
                        if (wikilinkLabel != null) {
                            int colonIdx = -1; // wikilinkLabel.indexOf(':');
                            if (colonIdx == -1) {
                                // do not serialize non-topic wiki-links such as
                                // translation links missing from the
                                // INTERWIKI_LINK map
                                int start = countingBuffer.currentPosition;
                                tag.getBodyString(countingBuffer);
                                int end = countingBuffer.currentPosition;
                                if (!wikilinkTarget.startsWith("#")) {
                                  // TODO: wikilink label is not important,since that is the covered text?
                                    wikilinks.add(new Annotation(start, end, wikilinkLabel, wikilinkTarget));
                                }
                            }
                        } else {
                            tag.getBodyString(countingBuffer);
                        }

                    } else if (node instanceof ContentToken) {
                        ContentToken contentToken = (ContentToken) node;
                        countingBuffer.append(contentToken.getContent());
                    } else if (node instanceof List) {
                    } else if (node instanceof WPList) {
                    } else if (node instanceof WPTable) {
                        // ignore lists and tables since they most of the time
                        // do not hold grammatically correct
                        // interesting sentences that are representative of the
                        // language.
                    } else if (node instanceof TagNode) {
                        TagNode tagNode = (TagNode) node;
                        Map<String, String> attributes = tagNode.getAttributes();
                        Map<String, Object> oAttributes = tagNode.getObjectAttributes();
                        boolean hasSpecialHandling = false;
                        String tagName = tagNode.getName();
                        int tagBegin = countingBuffer.currentPosition;
                        
                        if ("ref".equals(tagName)) {
                            // ignore the references since they do not hold
                            // interesting text content
                            hasSpecialHandling = true;
                        } else if (oAttributes != null
                                && oAttributes.get(WIKIOBJECT_ATTR_KEY) instanceof ImageFormat) {
                            // the caption of images often holds well formed
                            // sentences with links to entities
                            hasSpecialHandling = true;
                            ImageFormat iformat = (ImageFormat) oAttributes.get(WIKIOBJECT_ATTR_KEY);
                            imageNodeToText(tagNode, iformat, countingBuffer,
                                    model);
                        }
                        if (!hasSpecialHandling) {
                            nodesToText(tagNode.getChildren(), countingBuffer,
                                    model);
                        }
                        if (PARAGRAPH_TAGS.contains(tagName)) {
                            paragraphs.add(new Annotation(tagBegin,
                                    countingBuffer.currentPosition,
                                    "paragraph", tagName));
                            countingBuffer.append("\n\n");
                        } else if (HEADING_TAGS.contains(tagName)) {
                            headers.add(new Annotation(tagBegin,
                                countingBuffer.currentPosition, "heading",
                                    tagName));
                            countingBuffer.append("\n\n");
                        } else if ("a".equals(tagName)) {
                          String href = attributes.get(HREF_ATTR_KEY);
                          
                          // TODO: How to get covered text here? Is not needed anyway right?!
                          wikilinks.add(new Annotation(tagBegin, countingBuffer.currentPosition,
                              "", href));
                        }
                          
                    }
                }
            } finally {
                model.decrementRecursionLevel();
            }
        }
    }

    public void imageNodeToText(TagNode tagNode, ImageFormat imageFormat,
            Appendable buffer, IWikiModel model) throws IOException {
//        nodesToText(tagNode.getChildren(), buffer, model);
    }

    public boolean noLinks() {
        return true;
    }

    public List<Annotation> getWikiLinkAnnotations() {
        return wikilinks;
    }

    public List<Annotation> getHeaderAnnotations() {
        return headers;
    }

    public List<Annotation> getParagraphAnnotations() {
        return paragraphs;
    }

    public List<String> getParagraphs() {
        List<String> texts = new ArrayList<String>();
        for (Annotation p : paragraphs) {
            texts.add(text.substring(p.begin, p.end));
        }
        return texts;
    }

    public List<String> getHeaders() {
        List<String> texts = new ArrayList<String>();
        for (Annotation h : headers) {
            texts.add(text.substring(h.begin, h.end));
        }
        return texts;
    }

    public String getRedirect() {
        return redirect;
    }

    public class CountingAppendable implements Appendable {

        public int currentPosition = 0;

        final protected Appendable wrappedBuffer;

        public CountingAppendable(Appendable wrappedBuffer) {
            this.wrappedBuffer = wrappedBuffer;
        }

        public Appendable append(CharSequence charSeq) throws IOException {
            currentPosition += charSeq.length();
            return wrappedBuffer.append(charSeq);
        }

        public Appendable append(char aChar) throws IOException {
            currentPosition += 1;
            return wrappedBuffer.append(aChar);
        }

        public Appendable append(CharSequence charSeq, int start, int end)
                throws IOException {
            currentPosition += end - start;
            return wrappedBuffer.append(charSeq, start, end);
        }

    }

}
