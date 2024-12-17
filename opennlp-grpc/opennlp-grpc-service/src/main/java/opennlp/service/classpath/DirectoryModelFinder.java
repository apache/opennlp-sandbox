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

package opennlp.service.classpath;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import opennlp.tools.models.AbstractClassPathModelFinder;
import opennlp.tools.models.ClassPathModelFinder;

/**
 * The {@code DirectoryModelFinder} class is responsible for finding model files in a given directory
 * on the classpath.
 *
 * <p>This class allows searching for models based on wildcard patterns, either in plain directory structures
 * or within JAR files. The search can be performed recursively depending on the specified configuration.
 *
 * <p><b>Usage:</b>
 * <ul>
 *   <li>Provide the prefix for models to be found in JAR files using the {@code jarModelPrefix} parameter.</li>
 *   <li>Specify the directory to search and whether to enable recursive scanning.</li>
 *   <li>The class supports resolving both direct file matches and entries within JAR archives.</li>
 * </ul>
 *
 * @see AbstractClassPathModelFinder
 * @see ClassPathModelFinder
 */
public class DirectoryModelFinder extends AbstractClassPathModelFinder implements ClassPathModelFinder {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DirectoryModelFinder.class);

  private final Path directory;
  private final boolean recursive;
  private final Pattern jarPattern;
  private Pattern filePattern;
  private String prevFilePattern;

  /**
   * Constructs a new {@code DirectoryModelFinder} instance.
   *
   * @param jarModelPrefix the prefix for identifying model files in JAR archives; may be {@code null}.
   *                       If it is {@code null}, {@link ClassPathModelFinder#OPENNLP_MODEL_JAR_PREFIX} is used.
   * @param directory      the root directory to scan for model files; must not be {@code null}.
   * @param recursive      {@code true} if the search should include subdirectories, {@code false} otherwise.
   * @throws NullPointerException if {@code directory} is {@code null}.
   */
  public DirectoryModelFinder(String jarModelPrefix, Path directory, boolean recursive) {
    super(jarModelPrefix == null ? OPENNLP_MODEL_JAR_PREFIX : jarModelPrefix);
    Objects.requireNonNull(directory, "Given directory must not be NULL");
    this.directory = directory;
    this.recursive = recursive;
    this.jarPattern = Pattern.compile(asRegex("*" + getJarModelPrefix()));

  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Object getContext() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
    if (wildcardPattern == null) {
      return Collections.emptyList();
    }

    final boolean isWindows = isWindows();
    final List<URL> cp = getDirectoryContent();
    final List<URI> cpu = new ArrayList<>();

    final String filePatternString = asRegex("*" + wildcardPattern);
    if(!filePatternString.equals(prevFilePattern)) {
      this.filePattern = Pattern.compile(filePatternString);
      this.prevFilePattern = filePatternString;
    }

    for (URL url : cp) {

      if (matchesPattern(url, jarPattern)) {
        try {
          for (URI u : getURIsFromJar(url, isWindows)) {
            if (matchesPattern(u.toURL(), this.filePattern)) {
              cpu.add(u);
            }
          }
        } catch (IOException e) {
          logger.warn("Cannot read content of {}.", url, e);
        }
      }
    }

    return cpu;
  }

  private List<URL> getDirectoryContent() {
    final List<URL> fileList = new ArrayList<>();
    try (Stream<Path> files = Files.walk(directory, recursive ? Integer.MAX_VALUE : 1)) {
      files.filter(Files::isRegularFile).forEach(path -> {
        try {
          fileList.add(path.toUri().toURL());
        } catch (MalformedURLException ignored) {

        }
      });
    } catch (IOException e) {
      logger.warn(e.getLocalizedMessage(), e);
    }
    return fileList;
  }

  /**
   * Escapes a {@code wildcard} expressions for usage as a Java regular expression.
   *
   * @param wildcard A valid expression. It must not be {@code null}.
   * @return The escaped regex.
   */
  private String asRegex(String wildcard) {
    return wildcard.replace(".", "\\.").replace("*", ".*").replace("?", ".");
  }

  private boolean matchesPattern(URL url, Pattern pattern) {
    return pattern.matcher(url.getFile()).matches();
  }

  private List<URI> getURIsFromJar(URL fileUrl, boolean isWindows) throws IOException {
    final List<URI> uris = new ArrayList<>();
    final URL jarUrl = new URL(JAR + ":" + (isWindows ? fileUrl.toString().replace("\\", "/") : fileUrl.toString()) + "!/");
    final JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
    try (JarFile jarFile = jarConnection.getJarFile()) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          final URL entryUrl = new URL(jarUrl + entry.getName());
          try {
            uris.add(entryUrl.toURI());
          } catch (URISyntaxException ignored) {
            //if we cannot convert to URI here, we ignore that entry.
          }
        }
      }
    }

    return uris;
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("win");
  }

}
