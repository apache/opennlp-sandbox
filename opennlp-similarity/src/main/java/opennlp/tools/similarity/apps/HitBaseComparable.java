package opennlp.tools.similarity.apps;

import java.util.Comparator;

public class HitBaseComparable implements Comparator<HitBase>{
	//@Override
	public int compare(HitBase o1, HitBase o2) {
		return (o1.getGenerWithQueryScore()>o2.getGenerWithQueryScore() ? -1 : (o1==o2 ? 0 : 1));
	}
}