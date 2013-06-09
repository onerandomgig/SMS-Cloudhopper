package com.peoplecloud.smpp.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peoplecloud.smpp.cloudhopper.SMPPClient;

public class SMSMultipleSessionRouter implements ISMSMessageRouter {
	private int sessionToUse;

	private String lastUsedSMPPSession;
	private List<SMPPClient> smppClients;
	private Properties sysProps;

	private static final Logger logger = LoggerFactory
			.getLogger(SMSMultipleSessionRouter.class);

	public SMSMultipleSessionRouter(Properties aSysProps) {
		sessionToUse = 0;
		sysProps = aSysProps;

		smppClients = new ArrayList<SMPPClient>();
	}

	public void initialize() {
		int lSMPPSessionCount = Integer.parseInt(sysProps
				.getProperty("smsc.connection.count"));

		SMPPClient lClient = null;
		for (int lCount = 1; lCount <= lSMPPSessionCount; lCount++) {
			lClient = new SMPPClient(sysProps, lCount);
			lClient.initialize();

			smppClients.add(lClient);
		}
	}

	public void registerListener(SMSMessageListener aMsgListener) {
		for (SMPPClient lClient : smppClients) {
			lClient.registerListener(aMsgListener);
		}
	}

	public String getLastUserServerToSendMessage() {
		return lastUsedSMPPSession;
	}

	@Override
	public String sendSMS(String aMsg, String aSendFromNumber,
			String aSendToNumber) {
		String lStatus = smppClients.get(sessionToUse).sendSMSMessage(aMsg,
				aSendFromNumber, aSendToNumber);

		lastUsedSMPPSession = smppClients.get(sessionToUse).getClientName();

		logger.info("Using session " + lastUsedSMPPSession
				+ " to send message, Index: " + sessionToUse);

		sessionToUse++;

		if (sessionToUse >= smppClients.size()) {
			sessionToUse = 0;
		}

		return lStatus;
	}
}
