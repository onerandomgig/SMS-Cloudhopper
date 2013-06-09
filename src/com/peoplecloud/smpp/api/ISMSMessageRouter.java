package com.peoplecloud.smpp.api;

public interface ISMSMessageRouter {
	public String getLastUserServerToSendMessage();

	public String sendSMS(String aMsg, String aSendFromNumber,
			String aSendToNumber);

	public void registerListener(SMSMessageListener aMsgListener);
}
