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

package opennlp.tools.parse_thicket.kernel_interface;

import java.io.File;
import java.io.IOException;
import java.util.Stack;

//import org.apache.commons.io.FileUtils;

public class BracesProcessor {
	private static final int MIN_BRACES_CNT = 5;

	private static final char L_PAREN    = '(';
	private static final char R_PAREN    = ')';
	private static final char L_BRACE    = '{';
	private static final char R_BRACE    = '}';
	private static final char L_BRACKET  = '[';
	private static final char R_BRACKET  = ']';
	private  Stack<Character> stackIncremental = new Stack<Character>();
	private int count = 0;
	private Boolean balancedSoFar = true;

	public Boolean getBalancedBracesResult(){
		if (balancedSoFar)
			return (stackIncremental.isEmpty() && count> MIN_BRACES_CNT);
		else 
			return false;
	}

	public void analyzeBalancedBracesAddPortionIncremental(String s) {


		for (int i = 0; i < s.length(); i++) {

			if      (s.charAt(i) == L_PAREN)   {
				stackIncremental.push(L_PAREN);
				count++;

			}

			else if (s.charAt(i) == L_BRACE)   {
				stackIncremental.push(L_BRACE);
				count++;
			}

			else if (s.charAt(i) == L_BRACKET){
				stackIncremental.push(L_BRACKET);
				count++;
			}

			else if (s.charAt(i) == R_PAREN) {
				if (stackIncremental.isEmpty())        balancedSoFar = false;
				if (stackIncremental.pop() != L_PAREN) balancedSoFar = false;
			}

			else if (s.charAt(i) == R_BRACE) {
				if (stackIncremental.isEmpty())        balancedSoFar = false;
				if (stackIncremental.pop() != L_BRACE) balancedSoFar = false;
			}

			else if (s.charAt(i) == R_BRACKET) {
				if (stackIncremental.isEmpty())        balancedSoFar = false;
				if (stackIncremental.pop() != L_BRACKET) balancedSoFar = false;
			}

			// ignore all other characters

		}

	}

	public static boolean isBalanced(String s) {
		int count = 0;
		Stack<Character> stack = new Stack<Character>();
		for (int i = 0; i < s.length(); i++) {

			if      (s.charAt(i) == L_PAREN)   {
				stack.push(L_PAREN);
				count++;

			}

			else if (s.charAt(i) == L_BRACE)   {
				stack.push(L_BRACE);
				count++;
			}

			else if (s.charAt(i) == L_BRACKET){
				stack.push(L_BRACKET);
				count++;
			}

			else if (s.charAt(i) == R_PAREN) {
				if (stack.isEmpty())        return false;
				if (stack.pop() != L_PAREN) return false;
			}

			else if (s.charAt(i) == R_BRACE) {
				if (stack.isEmpty())        return false;
				if (stack.pop() != L_BRACE) return false;
			}

			else if (s.charAt(i) == R_BRACKET) {
				if (stack.isEmpty())        return false;
				if (stack.pop() != L_BRACKET) return false;
			}

			// ignore all other characters

		}
		return (stack.isEmpty());
	}

	public static boolean checkParentesis(String str)
	{
		if (str.isEmpty())
			return true;

		Stack<Character> stack = new Stack<Character>();
		for (int i = 0; i < str.length(); i++)
		{
			char current = str.charAt(i);
			if (current == '{' || current == '(' || current == '[')
			{
				stack.push(current);
			}


			if (current == '}' || current == ')' || current == ']')
			{
				if (stack.isEmpty())
					return false;

				char last = stack.peek();
				if (current == '}' && (last == '{' || current == ')')
						&& last == '(' || (current == ']'
						&& last == '['))
					stack.pop();
				else 
					return false;
			}

		}

		return stack.isEmpty();
	}

	public static boolean isParenthesisMatch(String str) {
		Stack<Character> stack = new Stack<Character>();

		char c;
		for(int i=0; i < str.length(); i++) {
			c = str.charAt(i);

			if(c == '{')
				return false;

			if(c == '(')
				stack.push(c);

			if(c == '{') {
				stack.push(c);
				if(c == '}')
					if(stack.empty())
						return false;
					else if(stack.peek() == '{')
						stack.pop();
			}
			else if(c == ')')
				if(stack.empty())
					return false;
				else if(stack.peek() == '(')
					stack.pop();
				else
					return false;
		}
		return stack.empty();
	}


}
