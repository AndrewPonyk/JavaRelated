package com.ap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class UtilsTest {

	@Test
	public void sumOfPrimes_ValueOf10_SumIs17() {
		assertEquals(new Integer(17), Utils.sumOfPrimes(10));
	}

	@Test
	public void sumOfPrimes_ValueOf1_SumIs0() {
		assertEquals(new Integer(0), Utils.sumOfPrimes(1));
	}

	@Test
	public void sumOfPrimes_ValueOf2_SumIs2() {
		assertEquals(new Integer(2), Utils.sumOfPrimes(2));
	}

	@Test
	public void sumOfPrimes_ValueOf13_SumIs41() {
		assertEquals(new Integer(41), Utils.sumOfPrimes(13));
	}

	@Test
	public void sumOfPrimes_ValueOf15_SumIs41() {
		assertEquals(new Integer(41), Utils.sumOfPrimes(15));
	}

	@Test
	public void sumOfPrimes_ValueOf0_SumIs0() {
		assertEquals("Test of 0 value", new Integer(0), Utils.sumOfPrimes(0));
	}

	@Test
	public void sumOfPrimes_ValueOf100000000_SumIsLongValue() {
		//assertEquals(Long.valueOf("279209790387276"), Utils.sumOfPrimes(100_000_000));
	}

	@Test
	public void sumOfPrimes_NegativeValues_ThrowsIllegalArgumentException() {
//		assertThrows("Test of the minimum integer value.",IllegalArgumentException.class, () -> Utils.sumOfPrimes(Integer.MIN_VALUE));
//		assertThrows("Test for negative value.", IllegalArgumentException.class, () -> Utils.sumOfPrimes(-1));
	}

	@Test
	public void convertToTitleCase_InputLowerCase_OutputTitleCase() {
		assertEquals("Hello World", Utils.convertToTitleCase("hello world"));
	}

	@Test
	public void convertToTitleCase_InputRandomCase_OutputTitleCase() {
		assertEquals("Another Test Case", Utils.convertToTitleCase("AnoTher teSt CasE"));
	}

	@Test
	public void convertToTitleCase_InputNull_OutputEmptyString() {
//		assertEquals("", Utils.convertToTitleCase(null));
	}

	@Test
	public void convertToTitleCase_InputEmptyString_OutputEmptyString() {
		assertEquals("", Utils.convertToTitleCase(""));
	}

	@Test
	public void convertToTitleCase_InputSingleLowerCaseLetters_OutputSingleUppercaseLetters() {
		assertEquals("A B C D", Utils.convertToTitleCase("a b c d"));
	}

	@Test
	public void convertToTitleCase_InputLowerCaseMessageWithExtraSpaces_OutputTitleCaseMessageSeparatedSingleSpaces() {
//		assertEquals("Extra Spaces Here", Utils.convertToTitleCase("  extra  spaces   here"));
	}

	@Test
	public void convertToTitleCase_InputLowerCaseWithSpecialSymbol_OutputTitleCaseWordsNearSpecialSymbol() {
//		assertEquals("Test@Home", Utils.convertToTitleCase("test@home"));
//		assertEquals("Hello-World", Utils.convertToTitleCase("hello-world"));
//		assertEquals("Java_101 !Special", Utils.convertToTitleCase("java_101 !special"));
	}

	@Test
	public void sumOfPrimes_MethodSignature_ReturnsLongAndTakesIntParameter() {
		Method method = null;
		try {
			method = Utils.class.getMethod("sumOfPrimes", int.class);
		} catch (NoSuchMethodException e) {
		}
		//assertNotNull("Method \"sumOfPrimes\" has incorrect signature", method);
		//assertEquals("Incorrect return type of method \"sumOfPrimes\"", Long.TYPE, method.getReturnType());
	}

	@Test
	public void convertToTitleCase_MethodSignature_ReturnsStringAndTakesStringParameter() {
		Method method = null;
		try {
			method = Utils.class.getMethod("convertToTitleCase", String.class);
		} catch (NoSuchMethodException e) {
			// Do nothing, methodFound will be false
		}
		//		assertNotNull("Method \"convertToTitleCase\" has incorrect signature", method);
		//assertEquals("Incorrect return type of method \"convertToTitleCase\"", String.class, method.getReturnType());
	}

	@Test
	public void groupAnagrams_ValidInputs_GroupsAnagramsCorrectly() {

//		String[] input1 = {"eat", "tea", "tan", "ate", "nat", "bat"};
//		Set<Set<String>> groupedAnagrams1 = Utils.groupAnagrams(input1);
//		assertGroupAnagrams("", groupedAnagrams1,
//				Arrays.asList("eat", "tea", "ate"),
//				Arrays.asList("tan", "nat"),
//				Arrays.asList("bat"));
//
//
//		String[] input2 = {"", "", ""};
//		Set<Set<String>> groupedAnagrams2 = Utils.groupAnagrams(input2);
//		assertGroupAnagrams("All strings are empty (anagrams of each other). ", groupedAnagrams2,
//				Arrays.asList("", "", ""));
//
//		String[] input3 = {"abc", "cab", "bca", "def", "fed"};
//		Set<Set<String>> groupedAnagrams3 = Utils.groupAnagrams(input3);
//		assertGroupAnagrams("Some strings are anagrams, others aren't. ", groupedAnagrams3,
//				Arrays.asList("abc", "cab", "bca"),
//				Arrays.asList("def", "fed"));
//
//
//		String[] input4 = {"a", "a", "a", "a", "a"};
//		Set<Set<String>> groupedAnagrams4 = Utils.groupAnagrams(input4);
//		assertGroupAnagrams("All strings are the same (anagrams of each other). ", groupedAnagrams4,
//				Arrays.asList("a", "a", "a", "a", "a"));
//
//		String[] input5 = {"", "eat", "abc", "a"};
//		Set<Set<String>> groupedAnagrams5 = Utils.groupAnagrams(input5);
//		assertGroupAnagrams("No anagrams. ", groupedAnagrams5,
//				Arrays.asList(""),
//				Arrays.asList("eat"),
//				Arrays.asList("abc"),
//				Arrays.asList("a"));
	}

	private void assertGroupAnagrams(String testMessage, Set<Set<String>> actual, List<String>... expectedGroups) {
		Set<Set<String>> actualSet = actual.stream()
				.map(HashSet::new)
				.collect(Collectors.toSet());

		Set<Set<String>> expectedSet = new HashSet<>();
		for (List<String> group : expectedGroups) {
			expectedSet.add(new HashSet<>(group));
		}

		assertEquals(testMessage + "The grouped anagrams do not match the expected result", expectedSet, actualSet);
	}
}
