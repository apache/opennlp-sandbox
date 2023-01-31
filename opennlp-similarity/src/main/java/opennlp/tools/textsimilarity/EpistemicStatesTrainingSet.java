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
package opennlp.tools.textsimilarity;

import java.util.HashMap;

public class EpistemicStatesTrainingSet {

  static public HashMap<String, String> class_setOfSentences = new HashMap<String, String>();

  static {
    class_setOfSentences
        .put(
            "beginner",
            "I'm fairly new to real cameras. "
                + "I am not a pro photographer. I am not a professional. "
                + "I have played around with friends digital cameras but never owned one myself. "
                + "First time buyer. I am a novice. Which camera is the most fool proof. I am a newbie. I am a beginner_xyz in cameras. I am just starting.");

    class_setOfSentences
        .put(
            "normal user",
            "I am not looking to make money with photos. "
                + "Need a camera for family use .	The camera will be used mainly for taking pictures of kids and family. ");

    class_setOfSentences.put("pro or semi pro user",
        "I am not looking for an entry level, more like semi-pro. "
            + "I am looking for an affordable professional camera. "
            + "looking for something professional. "
            + "I've shot a lot of film underwater using camera.");

    class_setOfSentences
        .put(
            "potential buyer",
            "I now want to get one of my own. "
                + "I need a camera that can handle conditions. "
                + "Which camera should I buy? "
                + "I would really like to get a camera with an optical viewfinder. "
                + "Need a camera for family use. "
                + "what camera would you recommend? "
                + "what camera should i buy? "
                + "I am looking for a camera that can serve a dual purpose. "
                + "Which camera is the most fool proof. "
                + "I am looking for a new camera to take with me to concerts. "
                + "I am looking for an affordable professional camera. "
                + "I want to buy a camera with features. "
                + "I am looking for a smaller camera. "
                + "what kind of camera should be purchased for the lab?  "
                + "I am looking to buy a mega zoom digital camera. "
                + "I was looking at a specific camera "
                + "what's the best compact camera? "
                + "I've been looking for a digital camera for my daughter. "
                + "I want a ultra zoom compact camera. "
                + "I need a new camera. "
                + "I am looking for a camera to take with me on vacation. "
                + "I still could not figure out what i should buy. ");
    /*
     * I need a camera for Alaska trip I am looking for small camera for the
     * night time I'm looking to upgrade to something better. I need a
     * replacement I am looking for one with better zoom and quality.
     */

    // upgrade_xyz - required in matching expr; otherwise fail
    class_setOfSentences
        .put(
            "experienced buyer",
            "I have read a lot of reviews but still have some questions on what camera is right for me. "
                + "I'm looking to upgrade_xyz to something better. "
                + "I need a replacement. I need a new camera!");

    class_setOfSentences
        .put(
            "open minded buyer",
            "I've been looking at some Canon models but am open to others. "
                + "I am open to all options just want a good quality camera. "
                + "I just cannot decide with all those cameras out there. "
                + "It comes down a few different canons. "
                + "There is just so many to choose from that I dont know what to pick. "
                + "what is the best compact camera? "
                + "i still could not figure out what i should buy. "
                + "I dont have brands that I like in particular. ");

    class_setOfSentences.put("user with one brand in mind",
        "No brand in particular but I have read that Canon makes good cameras. "
            + "I want to buy xyz camera. " + "Canon is my favorite brand. ");

    class_setOfSentences.put("already have a short list",
        "I am only looking at Nikon and Canon, maybe Sony. "
            + "I have narrowed my choice between these three cameras. "
            + "I am debating between these two. "
            + "Leaning toward Canon, Nikon, Sony but suggestions are welcome. "
            + "I'm looking at the camera and camera. "
            + "I have narrowed down my choices of camera. ");
  }

  public EpistemicStatesTrainingSet() {
  }

}
