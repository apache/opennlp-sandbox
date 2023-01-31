package opennlp.tools.parse_thicket;

import java.util.List;

public interface IGeneralizer<T> {
	/* All objects such as words, ParseTreeNodes, Phrases, Communicative actions etc. are subject to 
	 * generalization, so should implement this interface
	 * 
	 * In this project Everything is subject to generalization, and returns a list of generic objects
	 */
   public List<T> generalize(Object o1, Object o2);
}
