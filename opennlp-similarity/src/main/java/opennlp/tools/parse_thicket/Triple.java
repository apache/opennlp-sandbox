/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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