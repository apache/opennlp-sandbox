package opennlp.tools.apps.review_builder;

import java.util.List;

public class ReviewObj {
	
		long bpid;
		long pid;
		float rating;
		String pros;
	    String cons;
		String url;
		String title;
		String review;
		String keywordsName;
		float score;
		String[] origSentences;
		String[] featurePhrases;
		
		List<String> originalizedSentences ; //obtained from sentences;
		List<String> sentimentPhrases ; //obtained from sentences;
		
		public ReviewObj(long bpid, long pid, float rating, String pros,
				String cons, String url, String title, String review,
				float score) {
			super();
			this.bpid = bpid;
			this.pid = pid;
			this.rating = rating;
			this.pros = pros;
			this.cons = cons;
			this.url = url;
			this.title = title;
			this.review = review;
			this.score = score;
		}
		
		
		public List<String> getSentimentPhrases() {
			return sentimentPhrases;
		}


		public void setSentimentPhrases(List<String> sentimentPhrases) {
			this.sentimentPhrases = sentimentPhrases;
		}


		public ReviewObj() {
		}
		
		public String[] getOrigSentences() {
			return origSentences;
		}
		public void setOrigSentences(String[] sentences) {
			this.origSentences = sentences;
		}
		public List<String> getOriginalizedSentences() {
			return originalizedSentences;
		}


		public void setOriginalizedSentences(List<String> originalizedSentences) {
			this.originalizedSentences = originalizedSentences;
		}


		public String[] getFeaturePhrases() {
			return featurePhrases;
		}
		public void setFeaturePhrases(String[] featurePhrases) {
			this.featurePhrases = featurePhrases;
		}
		public long getBpid() {
			return bpid;
		}
		public void setBpid(long bpid) {
			this.bpid = bpid;
		}
		public long getPid() {
			return pid;
		}
		public void setPid(long pid) {
			this.pid = pid;
		}
		public float getRating() {
			return rating;
		}
		public void setRating(float rating) {
			this.rating = rating;
		}
		public String getPros() {
			return pros;
		}
		public void setPros(String pros) {
			this.pros = pros;
		}
		public String getCons() {
			return cons;
		}
		public void setCons(String cons) {
			this.cons = cons;
		}
		public String getUrl() {
			return url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public String getReview() {
			return review;
		}
		public void setReview(String review) {
			this.review = review;
		}
		public float getScore() {
			return score;
		}
		public void setScore(float score) {
			this.score = score;
		}
		public String getKeywordsName() {
			
			return this.keywordsName;
		}
		public void setKeywordsName(String kw) {
			
			keywordsName=kw;
		}
			
}
