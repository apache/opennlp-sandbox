package opennlp.tools.parse_thicket;

import java.util.Comparator;


public class Triple<T1, T2, T3> {
		  private T1 first;

		  private T2 second;
		  
		  private T3 third;

		  public Triple() {

		  }

		  public T1 getFirst() {
		    return first;
		  }

		  public void setFirst(T1 first) {
		    this.first = first;
		  }

		  public T2 getSecond() {
		    return second;
		  }

		  public void setSecond(T2 second) {
		    this.second = second;
		  }

		public Triple(T1 first, T2 second, T3 third) {
			super();
			this.first = first;
			this.second = second;
			this.third = third;
		}

		public T3 getThird() {
			return third;
		}

		public void setThird(T3 third) {
			this.third = third;
		}
		  
		  
		}