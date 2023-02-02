package opennlp.tools.parse_thicket;

public class WordWordInterSentenceRelationArc {
	
	
		Pair<Integer, Integer> codeFrom;
		Pair<Integer, Integer> codeTo;
		String lemmaFrom;
		String lemmaTo;
		ArcType arcType;
		
		public Pair<Integer, Integer> getCodeFrom() {
			return codeFrom;
		}

		public void setCodeFrom(Pair<Integer, Integer> codeFrom) {
			this.codeFrom = codeFrom;
		}

		public Pair<Integer, Integer> getCodeTo() {
			return codeTo;
		}

		public void setCodeTo(Pair<Integer, Integer> codeTo) {
			this.codeTo = codeTo;
		}

		public String getLemmaFrom() {
			return lemmaFrom;
		}

		public void setLemmaFrom(String lemmaFrom) {
			this.lemmaFrom = lemmaFrom;
		}

		public String getLemmaTo() {
			return lemmaTo;
		}

		public void setLemmaTo(String lemmaTo) {
			this.lemmaTo = lemmaTo;
		}

		public ArcType getArcType() {
			return arcType;
		}

		public void setArcType(ArcType arcType) {
			this.arcType = arcType;
		}

		public WordWordInterSentenceRelationArc(
				Pair<Integer, Integer> codeFrom, Pair<Integer, Integer> codeTo,
				String lemmaFrom, String lemmaTo, ArcType arcType) {
			super();
			this.codeFrom = codeFrom;
			this.codeTo = codeTo;
			this.lemmaFrom = lemmaFrom;
			this.lemmaTo = lemmaTo;
			this.arcType = arcType;
		}
	
		public String toString(){
			return arcType.toString()+"&<sent="+codeFrom.getFirst()+"-word="+codeFrom.getSecond()+".."+lemmaFrom+"> ===> "+
					"<sent="+codeTo.getFirst()+"-word="+codeTo.getSecond()+".."+lemmaTo+">";
		}

}
