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

package opennlp.tools.disambiguator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.commons.Internal;

/**
 * Defines the parameters for the <a href="https://aclanthology.org/P10-4014.pdf">
 *   IMS (It Makes Sense)</a> approach, as well as the
 * directories containing the files used
 *
 * @see WSDParameters
 */
public class WSDDefaultParameters extends WSDParameters {

  public static final String WINDOW_SIZE_PARAM = "WindowSize";
  public static final String NGRAM_PARAM = "NGram";
  public static final String LANG_CODE = "LangCode";
  public static final String SENSE_SOURCE_PARAM = "SenseSource";
  public static final String TRAINING_DIR_PARAM = "TrainingDirectory";

  /**
   * The default window size is 3.
   */
  public static final int WINDOW_SIZE_DEFAULT = 3;

  /**
   * The default ngram width is 2.
   */
  public static final int NGRAM_DEFAULT = 2;

  /**
   * The default ISO language code is 'en'.
   */
  public static final String LANG_CODE_DEFAULT = "en";

  /**
   * The default SenseSource is 'WORDNET'.
   */
  public static final SenseSource SOURCE_DEFAULT = SenseSource.WORDNET;

  private final Map<String, Object> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * No-arg constructor to create a basic {@link WSDDefaultParameters} instance.
   */
  @Internal
  WSDDefaultParameters() {
  }

  /**
   * Key-value based constructor to apply a {@link Map} based configuration initialization.
   */
  public WSDDefaultParameters(Map<String,Object> map) {
    parameters.putAll(map);
  }

  /**
   * {@link InputStream} based constructor that reads in {@link WSDDefaultParameters}.
   *
   * @param in The {@link InputStream} to a kay-value based file that defines {@link WSDParameters}.
   * @throws IOException Thrown if IO errors occurred.
   */
  public WSDDefaultParameters(InputStream in) throws IOException {
    final Properties properties = new Properties();
    properties.load(in);

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      parameters.put((String) entry.getKey(), entry.getValue());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean areValid() {
    return true;
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, String value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String key, String value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, int value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String key, int value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, double value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String key, double value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, boolean value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link WSDParameters} instance.
   */
  public void putIfAbsent(String key, boolean value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String namespace, String key, String value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String key, String value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String namespace, String key, int value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String key, int value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String namespace, String key, double value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String key, double value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String namespace, String key, boolean value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link WSDParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link WSDParameters} instance.
   */
  public void put(String key, boolean value) {
    put(null, key, value);
  }

  /**
   * Obtains a training parameter value.
   * <p>
   * Note:
   * {@link java.lang.ClassCastException} can be thrown if the value is not {@code String}
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public String getStringParameter(String key, String defaultValue) {
    return getStringParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * Note:
   * {@link java.lang.ClassCastException} can be thrown if the value is not {@link String}
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public String getStringParameter(String namespace, String key, String defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      return (String)value;
    }
  }

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Integer training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public int getIntParameter(String key, int defaultValue) {
    return getIntParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Integer training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public int getIntParameter(String namespace, String key, int defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Integer) value;
      }
      catch (ClassCastException e) {
        return Integer.parseInt((String)value);
      }
    }
  }

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Double training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public double getDoubleParameter(String key, double defaultValue) {
    return getDoubleParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Double training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public double getDoubleParameter(String namespace, String key, double defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Double) value;
      }
      catch (ClassCastException e) {
        return Double.parseDouble((String)value);
      }
    }
  }

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Boolean training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    return getBooleanParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Boolean training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public boolean getBooleanParameter(String namespace, String key, boolean defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Boolean) value;
      }
      catch (ClassCastException e) {
        return Boolean.parseBoolean((String)value);
      }
    }
  }

  /**
   * @return Retrieves a new {@link WSDDefaultParameters instance} initialized with default values.
   */
  public static WSDDefaultParameters defaultParams() {
    WSDDefaultParameters wsdParams = new WSDDefaultParameters();
    wsdParams.put(WSDDefaultParameters.LANG_CODE, LANG_CODE_DEFAULT);
    wsdParams.put(WSDDefaultParameters.WINDOW_SIZE_PARAM, WINDOW_SIZE_DEFAULT);
    wsdParams.put(WSDDefaultParameters.NGRAM_PARAM, NGRAM_DEFAULT);
    wsdParams.put(WSDDefaultParameters.SENSE_SOURCE_PARAM, SOURCE_DEFAULT.name());
    return wsdParams;
  }

  /**
   * @param params The parameters to additionally apply into the new {@link WSDDefaultParameters instance}.
   *
   * @return Retrieves a new {@link WSDDefaultParameters instance} initialized with given parameter values.
   */
  public static WSDDefaultParameters setParams(String[] params) {
    WSDDefaultParameters wsdParams = new WSDDefaultParameters();
    wsdParams.put(WSDDefaultParameters.LANG_CODE, LANG_CODE_DEFAULT);
    wsdParams.put(WSDDefaultParameters.SENSE_SOURCE_PARAM, SOURCE_DEFAULT.name());
    wsdParams.put(WSDDefaultParameters.WINDOW_SIZE_PARAM ,
            null != CmdLineUtil.getIntParameter("-" +
                    WSDDefaultParameters.WINDOW_SIZE_PARAM.toLowerCase() , params) ?
                    CmdLineUtil.getIntParameter("-" + WSDDefaultParameters.WINDOW_SIZE_PARAM.toLowerCase() , params) :
                    WINDOW_SIZE_DEFAULT);
    wsdParams.put(WSDDefaultParameters.NGRAM_PARAM ,
            null != CmdLineUtil.getIntParameter("-" +
                    WSDDefaultParameters.NGRAM_PARAM.toLowerCase() , params) ?
                    CmdLineUtil.getIntParameter("-" + WSDDefaultParameters.NGRAM_PARAM.toLowerCase() , params) :
                    NGRAM_DEFAULT);

    return wsdParams;
  }

  /**
   * @param namespace The namespace used as prefix or {@code null}.
   *                  If {@code null} the {@code key} is left unchanged.
   * @param key The identifying key to process.
   *
   * @return Retrieves a prefixed key in the specified {@code namespace}.
   *         If no {@code namespace} was specified the returned String is equal to {@code key}.
   */
  static String getKey(String namespace, String key) {
    if (namespace == null) {
      return key;
    }
    else {
      return namespace + "." + key;
    }
  }
}
