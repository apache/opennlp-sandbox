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

import org.apache.mahout.classifier.sgd.PassiveAggressive;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.vectorizer.encoders.StaticWordValueEncoder;

public class SimpleTest {

  public static void main(String[] args) {

    // Prepare data in vector format ...
    
    // The basic idea is that you create a vector, typically a RandomAccessSparseVector,
    // and then you use various feature encoders to progressively add features to that vector.
    // The size of the vector should be large enough to avoid feature collisions as features are hashed.
    
    // NOTE: Looks like we need to store the cardinality of the vector in the model ?!
    
    StaticWordValueEncoder encoder = new StaticWordValueEncoder("word-encoder");
    
    RandomAccessSparseVector vector1 = new RandomAccessSparseVector(3);
    vector1.set(0, 1);
    vector1.set(1, 0);
    vector1.set(2, 1);
    
//    encoder.addToVector("f1", vector1);
//    encoder.addToVector("f", vector1);

    RandomAccessSparseVector vector2 = new RandomAccessSparseVector(3);
    
    vector2.set(0, 0);
    vector2.set(1, 1);
    vector2.set(2, 1);
    
//    encoder.addToVector("f2", vector2);
//    encoder.addToVector("f", vector2);

    // do the training
    PassiveAggressive pa = new PassiveAggressive(2, 3);
    pa.train(0, vector1);
    pa.train(1, vector2);
    
    RandomAccessSparseVector vector = new RandomAccessSparseVector(pa.numFeatures());
    vector.set(0, 1);
    vector.set(1, 0);
    vector.set(2, 1);

    Vector result = pa.classifyFull(vector);
    
    System.out.println(result);
  }
}
