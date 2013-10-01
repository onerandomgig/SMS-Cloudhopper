package com.peoplecloud.smpp.api;

public interface ISMSMessageRouter {
	public String getLastUserServerToSendMessage();

	public String sendSMS(String aMsg, String aSendFromNumber,
			String aSendToNumber);
	
	public String sendSMSTo(String aMsg, String aSendFromNumber,
			String aSendToNumber, int aSmscId);

	public String getSMSPPServerById(Integer aSmscId);
	
	public void registerListener(SMSMessageListener aMsgListener);
}
