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
package opennlp.tools;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.slf4j.LoggerFactory;

public class ProfileReaderWriter {

	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static List<String[]> readProfiles(String filename) {
		return readProfiles(filename, ',');
	}
	
	public static List<String[]> readProfiles(String filename, char delimiter) {
		List<String[]> profiles = null;
		try (CSVReader reader = new CSVReader(new FileReader(filename), delimiter))	{
			profiles = reader.readAll();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
		return profiles;
	}

	public static void writeReportArr(String[][] allLines, String reportName) {
		writeReport(Arrays.asList(allLines), reportName);
	}

	public static void writeReport(List<String[]> allLines, String reportName) {
		try (CSVWriter writer = new CSVWriter(new PrintWriter(reportName))) {
			writer.writeAll(allLines);
			writer.flush();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}

	public static void writeReport(List<String[]> allLines, String reportName, char delimiter) {
		try (CSVWriter writer = new CSVWriter(new PrintWriter(reportName), delimiter)) {
			writer.writeAll(allLines);
			writer.flush();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}
	
	public static void appendReport(List<String[]> allLines, String reportName, char delimiter) {
		List<String[]> previous;
		try {
			previous = readProfiles(reportName);
			allLines.addAll(previous);
		} catch (Exception e) {
			LOG.error("Creating file {}: {}", reportName, e.getLocalizedMessage(), e);
		}
		
		try (CSVWriter writer = new CSVWriter(new PrintWriter(reportName), delimiter)) {
			writer.writeAll(allLines);
			writer.flush();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}

	public static void writeReportListStr(List<String> res, String string) {

	}

	public static void main(String[] args){
		List<String[]> allLines = new ArrayList<>();
		allLines.add(new String[] {"aa " , "  bb", "ccc" });
		ProfileReaderWriter.writeReport( allLines, "reportName.txt", ' ');
	}

}