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
package opennlp.tools.jsmlearning;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TreeKernelRunner {
	private void runEXE(String[] command, String runPath){
		Runtime r = Runtime.getRuntime();
		Process mStartProcess = null;
		try {
			mStartProcess = r.exec( command, null, new File(runPath));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		StreamLogger outputGobbler = new StreamLogger(mStartProcess.getInputStream());
		outputGobbler.start();

		try {
			int returnCode = mStartProcess.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void runLearner(String dir, String learning_file, String  model_file)
	{
		dir = dir.replace('/', '\\');
		
		if (!dir.endsWith("\\"))
				dir+="\\";
		String[] runString = new String[]{dir+"svm_learn.exe","-t", "5", dir+learning_file,  dir+model_file};
		runEXE(runString, dir);
	}
	
	
	//svm_classify example_file model_file predictions_file
	public void runClassifier(String dir, String example_file, String  model_file, String predictions_file)
	{
		dir = dir.replace('/', '\\');
		
		if (!dir.endsWith("\\"))
				dir+="\\";
		String[] runString = new String[]{dir+"svm_classify.exe", dir+example_file,  dir+model_file, dir+predictions_file};
		runEXE(runString, dir);
	}

	class StreamLogger extends Thread{

		private InputStream mInputStream;

		public StreamLogger(InputStream is) {
			this.mInputStream = is;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(mInputStream);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					System.out.println(line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

	}
	
	public static void main(String[] args){
		TreeKernelRunner runner = new TreeKernelRunner();
		runner.runLearner("C:\\stanford-corenlp\\tree_kernel\\", "training.txt", "arg0.model1.txt");
		runner.runClassifier("C:\\stanford-corenlp\\tree_kernel\\", "arg0.test", "arg0.model1.txt", "arg0.output1.txt");
	}
}

	/*
exec:

public Process exec(String command, String envp[], File dir) 



   @param      command   a specified system command.
   @param      envp      array of strings, each element of which 
                         has environment variable settings in format
                         <i>name</i>=<i>value</i>.
   @param      dir       the working directory of the subprocess, or
                         <tt>null</tt> if the subprocess should inherit
                         the working directory of the current process.

                         Ð’ Ð´Ð¸Ñ�Ñ‚Ñ€Ð¸Ð±ÑƒÑ‚Ð¸Ð²Ðµ Ð´Ð²Ð° exe-Ñ„Ð°Ð¹Ð»Ð°: svm_learn.exe Ð¸ svm_classify.exe.

1.   svm_learn.exe Ð±ÐµÑ€ÐµÑ‚ Ñ„Ð°Ð¹Ð» Ñ� Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ð°Ð¼Ð¸, Ð¾Ð±Ñ€Ð°Ð±Ð°Ñ‚Ñ‹Ð²Ð°ÐµÑ‚ ÐµÐ³Ð¾, Ñ�Ñ‚Ñ€Ð¾Ð¸Ñ‚ Ñ„Ð°Ð¹Ð» model Ð¼ Ð¿Ñ€Ð°Ð²Ð¸Ð»Ð°Ð¼Ð¸ Ð¾Ð±ÑƒÑ‡ÐµÐ½Ð¸Ðµ.

ÐŸÑ€Ð¸Ð¼ÐµÑ€Ñ‹ Ð·Ð°Ð¿ÑƒÑ�ÐºÐ°: 
svm_learn -t 5 learning_file model_file - Ñ�Ñ‚Ð¾ Ñ�Ð°Ð¼Ñ‹Ð¹ Ð¿Ñ€Ð¾Ñ�Ñ‚Ð¾Ð¹ Ð²Ð°Ñ€Ð¸Ð°Ð½Ñ‚ Ð·Ð°Ð¿ÑƒÑ�ÐºÐ°, SubSetTreeKernel (Ð´Ð¾Ð¿ÑƒÑ�ÐºÐ°ÑŽÑ‚Ñ�Ñ� Ñ€Ð°Ð·Ñ€Ñ‹Ð²Ñ‹ Ð¿Ñ€Ð¸ Ð¾Ð±Ñ…Ð¾Ð´Ðµ Ð´ÐµÑ€ÐµÐ²ÑŒÐµÐ²)

svm_learn -t 5 -D 0 learning_file model_file - Ð´Ñ€ÑƒÐ³Ð¾Ð¹ Ð²Ð°Ñ€Ð¸Ð°Ð½Ñ‚ Ñ�Ð´Ñ€Ð°, SubTreeKernel

ÐŸÑ€Ð¸Ð¼ÐµÑ€ Ñ„Ð°Ð¹Ð»Ð° Ð»ÐµÐ¶Ð¸Ñ‚ Ð½Ð° ÐµÐ³Ð¾ Ñ�Ñ‚Ñ€Ð°Ð½Ð¸Ñ‡ÐºÐµ. Ð¢Ð°Ð¼ Ð¶Ðµ Ð¾Ð¿Ð¸Ñ�Ð°Ð½Ð¸Ðµ Ð¿Ð°Ñ€Ð°Ð¼ÐµÑ‚Ñ€Ð¾Ð².

2. svm_classify.exe Ð±ÐµÑ€ÐµÑ‚ Ñ„Ð°Ð¹Ð» Ñ� Ñ‚ÐµÑ�Ñ‚Ð¾Ð²Ñ‹Ð¼Ð¸ Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ð°Ð¼Ð¸, Ñ„Ð°Ð¹Ð» Ñ� Ð¼Ð¾Ð´ÐµÐ»ÑŒÑŽ, Ð¿Ð¾Ñ�Ñ‚Ñ€Ð¾ÐµÐ½Ð½Ñ‹Ð¹ svm_learn, Ð¸ Ð·Ð°Ð¿Ð¸Ñ�Ñ‹Ð²Ð°ÐµÑ‚ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ñ‹ Ð¾Ð±ÑƒÑ‡ÐµÐ½Ð¸Ñ� Ð² Ñ„Ð°Ð¹Ð» predictions_file.

Ð—Ð°Ð¿ÑƒÑ�Ðº:     svm_classify example_file model_file predictions_file

Ð¤Ð°Ð¹Ð» Ð¸Ð¼ÐµÐµÑ‚ Ñ‚Ð¾Ñ‚ Ð¶Ðµ Ñ„Ð¾Ñ€Ð¼Ð°Ñ‚, Ñ‡Ñ‚Ð¾ Ð¸ Ð²Ñ…Ð¾Ð´Ð½Ñ‹Ðµ Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ñ‹. ÐžÐ±Ñ€Ð°Ð·ÐµÑ† Ð»ÐµÐ¶Ð¸Ñ‚ Ð² Ð°Ñ€Ñ…Ð¸Ð²Ðµ Ð½Ð° Ñ�Ñ‚Ñ€Ð°Ð½Ð¸Ñ‡ÐºÐµ ÐœÐ¾Ñ�ÐºÐ¸Ñ‚Ñ‚Ð¸. 
ÐœÐ¾Ð¶Ð½Ð¾ Ñ�Ñ€Ð°Ð·Ñƒ Ð¶Ðµ ÑƒÐºÐ°Ð·Ñ‹Ð²Ð°Ñ‚ÑŒ, Ðº ÐºÐ°ÐºÐ¾Ð¼Ñƒ ÐºÐ»Ð°Ñ�Ñ�Ñƒ Ð¾Ñ‚Ð½Ð¾Ñ�Ð¸Ñ‚Ñ�Ñ� Ð¿Ñ€Ð¸Ð¼ÐµÑ€ (1 Ð¸Ð»Ð¸ -1 Ð² Ð½Ð°Ñ‡Ð°Ð»Ðµ Ñ�Ñ‚Ñ€Ð¾ÐºÐ¸). Ð’ Ñ�Ñ‚Ð¾Ð¼ Ñ�Ð»ÑƒÑ‡Ð°Ðµ Ñ‚Ð¾Ñ‡Ð½Ð¾Ñ�Ñ‚ÑŒ Ð¸ Ð¿Ð¾Ð»Ð½Ð¾Ñ‚Ð° Ð¾Ñ†ÐµÐ½Ð¸Ð²Ð°ÑŽÑ‚Ñ�Ñ� Ð°Ð²Ñ‚Ð¾Ð¼Ð°Ñ‚Ð¸Ñ‡ÐµÑ�ÐºÐ¸. Ð˜Ð»Ð¸ Ñ�Ñ‚Ð°Ð²Ð¸Ñ‚ÑŒ Ñ‚Ð°Ð¼ 0.
	 */