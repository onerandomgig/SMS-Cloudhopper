package com.peoplecloud.smpp.cloudhopper;

public class EncodeDecodeUtils {

	public static void main(String[] args) {
		String lTest = "[B@3ff59a06";

		try {
			String lEncodedTest = java.net.URLEncoder.encode(lTest,
					"ISO-8859-15");

			String lDecodedTest = java.net.URLDecoder.decode(lTest,
					"ISO-8859-15");

			System.out.println("Original: " + lTest);
			System.out.println("Encoded Test: " + lEncodedTest);
			System.out.println("Decoded Test: " + lDecodedTest);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}