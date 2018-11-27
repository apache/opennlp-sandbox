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

package opennlp.tools.parse_thicket;

import java.util.Comparator;

/**
 * Generic pair class for holding two objects. Often used as return object.
 * 
 * @author Albert-Jan de Vries
 * 
 * @param <T1>
 * @param <T2>
 */
public class Pair<T1, T2> {
  private T1 first;

  private T2 second;

  public Pair() {

  }

  public Pair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
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
  
  public class PairComparable implements Comparator<Pair<T1, T2>> {
    // @Override
    public int compare(Pair o1, Pair o2) {
      int b = -2;
      if ( o1.second instanceof Float && o2.second instanceof Float){
        
        b =  (((Float)o1.second > (Float)o2.second) ? -1
          : (((Float)o1.second == (Float)o2.second) ? 0 : 1));
      }
      return b;
    }
  }
  public String toString(){
	  return this.first.toString()+" "+this.second.toString();
  }
  
}

