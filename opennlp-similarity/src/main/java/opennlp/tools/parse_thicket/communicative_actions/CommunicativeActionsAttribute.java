package opennlp.tools.parse_thicket.communicative_actions;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;


public class CommunicativeActionsAttribute implements IGeneralizer<Integer[]>{

	public List<Integer[]> generalize(Object intArr1ob, Object intArr2ob) {
		Integer[] arr1 = (Integer[])intArr1ob, arr2 = (Integer[])intArr2ob;
		Integer[] result = new Integer[arr2.length];
		for(int i=0; i< arr2.length; i++ ){
			if (arr1[i].equals(arr2[i]))
				result[i] = arr1[i];
			else if ((arr1[i]<0 && arr2[i]>0) || (arr1[i]>0 && arr2[i]<0)){
				result[i]=0;
			} else if (arr1[i]==0)
				result[i]=arr2[i];
			else if (arr2[i]==0)
				result[i]=arr1[i];
		}
		List<Integer[]> results = new ArrayList<Integer[]>();
		results.add(result);
		return results;
	}

}
