package opennlp.tools.apps.contentgen.multithreaded;

import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;


public class Fragment {
	
		public String resultText;      // result
		public double score;
		public String fragment; // original
		public String sourceURL;

		Fragment(String text, double score) {
			this.resultText = text;
			this.score = score;
		}
		
			
		public String getResultText() {
			return resultText;
		}

		public void setResultText(String resultText) {
			this.resultText = resultText;
		}



		public double getScore() {
			return score;
		}



		public void setScore(double score) {
			this.score = score;
		}



		public String getFragment() {
			return fragment;
		}



		public void setFragment(String fragment) {
			this.fragment = fragment;
		}

		

		public String getSourceURL() {
			return sourceURL;
		}


		public void setSourceURL(String sourceURL) {
			this.sourceURL = sourceURL;
		}


		public String toString(){
			return this.resultText;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Fragment fragment = (Fragment) o;

			if (resultText == null && fragment.resultText == null) {
				return true;
			} else if ((resultText == null && fragment.resultText != null) || (resultText != null && fragment.resultText == null)) {
				return false;
			}

			StringDistanceMeasurer sdm = new StringDistanceMeasurer();
			return sdm.measureStringDistance(resultText, fragment.resultText) > 0.8;
		}

		@Override
		public int hashCode() {
			return resultText != null ? resultText.hashCode() : 0;
		}
}
