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

package opennlp.tools.similarity.apps.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * This class responsible to save data to files as well as read out! It is
 * capable to handle text and binary files.
 */
public class FileHandler {

  private static Logger LOG = Logger
      .getLogger("opennlp.tools.similarity.apps.utils.FileHandler");

  public void writeToTextFile(String data, String filepath, boolean append)
      throws IOException {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filepath, append));
      out.write(data + "\n");
      out.close();
    } catch (IOException e) {
      LOG.error(e);
      e.printStackTrace();
    }
  }

  /**
   * Writes data from an arrayList<String> to a text-file where each line of the
   * text represented by an element in the list.
   * 
   * @param list
   * @param filePath
   * @param append
   * @throws Exception
   */
  public void writeToTextFile(ArrayList<String> list, String filePath,
      boolean append) throws Exception {
    FileWriter outFile = null;
    Iterator<String> it = list.iterator();
    if (!append) {
      outFile = new FileWriter(filePath);
      PrintWriter out = new PrintWriter(outFile);
      while (it.hasNext()) {
        out.println((String) it.next());
      }
      outFile.close();
    } else {
      int tmp = 0;
      while (it.hasNext()) {
        if (tmp == 0) {
          appendtofile("\n" + (String) it.next(), filePath);
        } else {
          appendtofile((String) it.next(), filePath);
        }
        tmp++;
      }
    }
  }

  public void writeObjectToFile(Object obj, String filepath, boolean append) {
    if (!isFileOrDirectoryExists(getDirPathfromFullPath(filepath))) {
      createFolder(getDirPathfromFullPath(filepath));
    }
    ObjectOutputStream outputStream = null;
    try {
      outputStream = new ObjectOutputStream(new FileOutputStream(filepath));
      outputStream.writeObject(obj);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  public Object readObjectfromFile(String filePath) {
    ObjectInputStream inputStream = null;
    try {
      // Construct the ObjectInputStream object
      inputStream = new ObjectInputStream(new FileInputStream(filePath));
      Object obj = null;
      while ((obj = inputStream.readObject()) != null) {
        return obj;
      }
    } catch (EOFException ex) { // This exception will be caught when EOF is
                                // reached
      LOG.error("End of file reached.", ex);
    } catch (ClassNotFoundException ex) {
      LOG.error(ex);
    } catch (FileNotFoundException ex) {
      LOG.error(ex);
    } catch (IOException ex) {
      LOG.error(ex);
    } finally {
      // Close the ObjectInputStream
      try {
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (IOException ex) {
        LOG.error(ex);
      }
    }
    return null;
  }

  /**
   * Creates a byte array from any object.
   * 
   * I wanted to use it when I write out object to files! (This is not in use
   * right now, I may move it into other class)
   * 
   * @param obj
   * @return
   * @throws java.io.IOException
   */
  public byte[] getBytes(Object obj) throws java.io.IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(obj);
    oos.flush();
    oos.close();
    bos.close();
    byte[] data = bos.toByteArray();
    return data;
  }

  /**
   * Fetches all content from a text file, and return it as a String.
   * 
   * @return
   */
  public String readFromTextFile(String filePath) {
    StringBuilder contents = new StringBuilder();
    // ...checks on aFile are edited
    File aFile = new File(filePath);

    try {
      // use buffering, reading one line at a time
      // FileReader always assumes default encoding is OK!
      // TODO be sure that the default encoding is OK!!!!! Otherwise
      // change it

      BufferedReader input = new BufferedReader(new FileReader(aFile));
      try {
        String line = null; // not declared within while loop
        /*
         * readLine is a bit quirky : it returns the content of a line MINUS the
         * newline. it returns null only for the END of the stream. it returns
         * an empty String if two newlines appear in a row.
         */
        while ((line = input.readLine()) != null) {
          contents.append(line);
          contents.append(System.getProperty("line.separator"));
        }
      } finally {
        input.close();
      }
    } catch (IOException ex) {
      LOG.error("fileName: " + filePath, ex);
    }
    return contents.toString();
  }

  /**
   * Reads text file line-wise each line will be an element in the resulting
   * list
   * 
   * @param filePath
   * @return
   */
  public List<String> readLinesFromTextFile(String filePath) {
    List<String> lines = new ArrayList<String>();
    // ...checks on aFile are edited
    File aFile = new File(filePath);
    try {
      // use buffering, reading one line at a time
      // FileReader always assumes default encoding is OK!
      // TODO be sure that the default encoding is OK!!!!! Otherwise
      // change it

      BufferedReader input = new BufferedReader(new FileReader(aFile));
      try {
        String line = null; // not declared within while loop
        /*
         * readLine is a bit quirky : it returns the content of a line MINUS the
         * newline. it returns null only for the END of the stream. it returns
         * an empty String if two newlines appear in a row.
         */
        while ((line = input.readLine()) != null) {
          lines.add(line);
        }
      } finally {
        input.close();
      }
    } catch (IOException ex) {
      LOG.error(ex);
    }
    return lines;
  }

  private void appendtofile(String data, String filePath) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filePath, true));
      out.write(data + "\n");
      out.close();
    } catch (IOException e) {
    }
  }

  public void createFolder(String path) {
    if (!isFileOrDirectoryExists(path)) {
      File file = new File(path);
      try {
        file.mkdirs();
      } catch (Exception e) {
        LOG.error("Directory already exists or the file-system is read only", e);
      }
    }
  }

  public boolean isFileOrDirectoryExists(String path) {
    File file = new File(path);
    boolean exists = file.exists();
    return exists;
  }

  /**
   * Separates the directory-path from a full file-path
   * 
   * @param filePath
   * @return
   */
  private String getDirPathfromFullPath(String filePath) {
    String dirPath = "";
    if (filePath != null) {
      if (filePath != "" && filePath.contains("\\"))
        dirPath = filePath.substring(0, filePath.lastIndexOf("\\"));
    }
    return dirPath;
  }

  /**
   * Returns the file-names of the files in a folder (not paths only names) (Not
   * recursive)
   * 
   * @param dirPath
   * @return
   */
  public ArrayList<String> getFileNamesInFolder(String dirPath) {
    ArrayList<String> fileNames = new ArrayList<String>();

    File folder = new File(dirPath);
    File[] listOfFiles = folder.listFiles();

    for (int i = 0; i < listOfFiles.length; i++) {
      if (listOfFiles[i].isFile()) {
        fileNames.add(listOfFiles[i].getName());
      } else if (listOfFiles[i].isDirectory()) {
        // TODO if I want to use it recursive I should handle this case
      }
    }
    return fileNames;
  }

  public void deleteAllfilesinDir(String dirName) {
    ArrayList<String> fileNameList = getFileNamesInFolder(dirName);
    if (fileNameList != null) {
      for (int i = 0; i < fileNameList.size(); i++) {
        try {
          deleteFile(dirName + fileNameList.get(i));
        } catch (IllegalArgumentException e) {
          LOG.error("No way to delete file: " + dirName + fileNameList.get(i),
              e);
        }
      }
    }
  }

  public void deleteFile(String filePath) throws IllegalArgumentException {
    // A File object to represent the filename
    File f = new File(filePath);
    // Make sure the file or directory exists and isn't write protected
    if (!f.exists())
      throw new IllegalArgumentException("Delete: no such file or directory: "
          + filePath);

    if (!f.canWrite())
      throw new IllegalArgumentException("Delete: write protected: " + filePath);
    // If it is a directory, make sure it is empty
    if (f.isDirectory()) {
      String[] files = f.list();
      if (files.length > 0)
        throw new IllegalArgumentException("Delete: directory not empty: "
            + filePath);
    }
    // Attempt to delete it
    boolean success = f.delete();
    if (!success)
      throw new IllegalArgumentException("Delete: deletion failed");
  }

  public boolean deleteDirectory(File path) {
    if (path.exists()) {
      File[] files = path.listFiles();
      for (int i = 0; i < files.length; i++) {
        if (files[i].isDirectory()) {
          deleteDirectory(files[i]);
        } else {
          files[i].delete();
        }
      }
    }
    return (path.delete());
  }

  /**
   * Returns the absolute-file-paths of the files in a directory (not recursive)
   * 
   * @param dirPath
   * @return
   */
  public ArrayList<String> getFilePathsInFolder(String dirPath) {
    ArrayList<String> filePaths = new ArrayList<String>();

    File folder = new File(dirPath);
    File[] listOfFiles = folder.listFiles();
    if (listOfFiles == null)
      return null;
    for (int i = 0; i < listOfFiles.length; i++) {
      if (listOfFiles[i].isFile()) {
        filePaths.add(listOfFiles[i].getAbsolutePath());
      } else if (listOfFiles[i].isDirectory()) {
        // TODO if I want to use it recursive I should handle this case
      }
    }
    return filePaths;
  }

  /**
   * Returns the number of individual files in a directory (Not ercursive)
   * 
   * @param dirPath
   * @return
   */
  public int getFileNumInFolder(String dirPath) {
    int num = 0;
    try {
      num = getFileNamesInFolder(dirPath).size();
    } catch (Exception e) {
      num = 0;
    }
    return num;
  }

}
