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

package opennlp.tools.coref;

import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;

/**
 * A specialized {@link DiscourseElement} representing an entity in a {@link DiscourseModel}.
 *
 * @see DiscourseElement
 * @see DiscourseModel
 */
public class DiscourseEntity extends DiscourseElement {

  private String category = null;
  private GenderEnum gender;
  private double genderProb;
  private NumberEnum number;
  private double numberProb;

  /**
   * Instantiates a {@link DiscourseEntity} based on the specified mention and its specified gender and
   * number properties.
   *
   * @param mention The first mention of this entity.
   * @param gender The {@link GenderEnum gender} of this entity.
   * @param genderProb The probability that the specified gender is correct, in the range from {@code [0.0, ..., 1.0]}.
   * @param number The {@link NumberEnum number} for this entity.
   * @param numberProb The probability that the specified number is correct, in the range from {@code [0.0, ..., 1.0]}.
   */
  public DiscourseEntity(MentionContext mention, GenderEnum gender, double genderProb,
                         NumberEnum number, double numberProb) {
    super(mention);
    this.gender = gender;
    this.genderProb = genderProb;
    this.number = number;
    this.numberProb = numberProb;
  }

  /**
   * Instantiates a {@link DiscourseEntity} with unknown {@link GenderEnum gender}
   * and {@link NumberEnum number} properties.
   *
   * @param mention The first mention of this entity.
   */
  public DiscourseEntity(MentionContext mention) {
    this(mention, GenderEnum.UNKNOWN, 0.0d, NumberEnum.UNKNOWN, 0.0d);
  }

  /**
   * Returns the semantic category of this entity.
   * This field is used to associated named-entity categories with an entity.
   *
   * @return the semantic category of this entity.
   */
  public String getCategory() {
    return (category);
  }

  /**
   * Specifies the semantic category of this entity.
   *
   * @param cat The semantic category of the entity.
   */
  public void setCategory(String cat) {
    category = cat;
  }

  /**
   * @return Retrieves the {@link GenderEnum gender} associated with this entity.
   */
  public GenderEnum getGender() {
    return gender;
  }

  /**
   * @return Retrieves the probability for the {@link GenderEnum gender} associated with this entity.
   */
  public double getGenderProbability() {
    return genderProb;
  }

  /**
   * @return Retrieves the {@link NumberEnum number} associated with this entity.
   */
  public NumberEnum getNumber() {
    return number;
  }

  /**
   * @return Retrieves the probability for the {@link NumberEnum number} associated with this entity.
   */
  public double getNumberProbability() {
    return numberProb;
  }

  /**
   * Specifies the {@link GenderEnum gender} of this entity.
   *
   * @param gender The gender.
   */
  public void setGender(GenderEnum gender) {
    this.gender = gender;
  }

  /**
   * Specifies the probability of the {@link GenderEnum gender} of this entity.
   *
   * @param p the probability of the gender, in the range from {@code [0.0, ..., 1.0]}.
   */
  public void setGenderProbability(double p) {
    genderProb = p;
  }

  /**
   * Specifies the {@link NumberEnum number} of this entity.
   *
   * @param number The {@link NumberEnum number}.
   */
  public void setNumber(NumberEnum number) {
    this.number = number;
  }

  /**
   * Specifies the probability of the {@link NumberEnum number} of this entity.
   *
   * @param p the probability of the number, in the range from {@code [0.0, ..., 1.0]}.
   */
  public void setNumberProbability(double p) {
    numberProb = p;
  }
}
