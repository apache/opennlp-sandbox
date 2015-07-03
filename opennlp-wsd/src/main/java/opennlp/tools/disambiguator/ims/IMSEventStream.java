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

package opennlp.tools.disambiguator.ims;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

public class IMSEventStream extends AbstractEventStream<WTDIMS> {

  private IMSContextGenerator cg;

  public IMSEventStream(ObjectStream<WTDIMS> samples) {
    super(samples);
  }

  @Override
  protected Iterator<Event> createEvents(WTDIMS sample) {
    List<Event> events = new ArrayList<Event>();

    int sense = sample.getSense();

    String[] context = cg.getContext(sample);

    Event ev = new Event(sense + "", context);

    events.add(ev);

    return events.iterator();
  }

}
