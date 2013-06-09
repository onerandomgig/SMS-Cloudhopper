package com.peoplecloud.smpp.api;

public interface SMSMessageListener {
	public void notify(String aMessage, String aFromNumber, String aToNumber,
			String aSMPPSession);
}
