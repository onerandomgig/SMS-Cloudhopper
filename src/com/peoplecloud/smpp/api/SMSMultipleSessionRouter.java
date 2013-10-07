package com.peoplecloud.smpp.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peoplecloud.smpp.cloudhopper.SMPPClient;
import com.peoplecloud.smpp.utils.VelocityEmailSender;

public class SMSMultipleSessionRouter implements ISMSMessageRouter {
	private int sessionToUse;

	private String lastUsedSMPPSession;
	private List<SMPPClient> smppClients;
	private Properties sysProps;

	Map<Integer, SMPPClient> mapOfSMPPClients;

	private VelocityEmailSender velocityEmailSenderService;

	private static final Logger logger = LoggerFactory
			.getLogger(SMSMultipleSessionRouter.class);

	public SMSMultipleSessionRouter(Properties aSysProps) {
		sessionToUse = 0;
		sysProps = aSysProps;

		smppClients = new ArrayList<SMPPClient>();
		mapOfSMPPClients = new HashMap<Integer, SMPPClient>();
	}

	public VelocityEmailSender getVelocityEmailSenderService() {
		return velocityEmailSenderService;
	}

	public void setVelocityEmailSenderService(
			VelocityEmailSender velocityEmailSenderService) {
		this.velocityEmailSenderService = velocityEmailSenderService;
	}

	public String getSMSPPServerById(Integer aSmscId) {
		SMPPClient lClient = mapOfSMPPClients.get(aSmscId);
		if (lClient != null) {
			return lClient.getClientName();
		}

		return "";
	}

	public String sendSMSTo(String aMsg, String aSendFromNumber,
			String aSendToNumber, int aSmscId) {
		SMPPClient lClient = mapOfSMPPClients.get(aSmscId);

		if (lClient == null
				&& sysProps.getProperty("smsc.connection.name." + aSmscId) == null) {

			logger.error("No configuration defined for SMS ID: " + aSmscId);
			return "No configuration defined for SMS ID: " + aSmscId;

		} else if (lClient == null) {

			lClient = new SMPPClient(sysProps, aSmscId,
					velocityEmailSenderService);

			try {
				lClient.initialize();
				mapOfSMPPClients.put(aSmscId, lClient);
			} catch (Exception ex) {
				logger.error(
						"Could not initialize session " + aSmscId
								+ ". Will attempt to reinitialize. Error is : "
								+ ex.getMessage(), ex);

				lClient.reinitializeSession();
			}
		}

		String lStatus = lClient.sendSMSMessage(aMsg, aSendFromNumber,
				aSendToNumber);
		logger.info("Using session " + lClient.getClientName()
				+ " to send message, Index: " + aSmscId);

		return lStatus;
	}

	public void initialize() {
		int lSMPPSessionCount = Integer.parseInt(sysProps
				.getProperty("smsc.connection.count"));

		SMPPClient lClient = null;
		for (int lCount = 1; lCount <= lSMPPSessionCount; lCount++) {
			lClient = new SMPPClient(sysProps, lCount,
					velocityEmailSenderService);

			try {
				lClient.initialize();
				smppClients.add(lClient);

				mapOfSMPPClients.put(lCount, lClient);
			} catch (Exception ex) {
				logger.error(
						"Could not initialize session " + lCount
								+ ". Will attempt to reinitialize. Error is : "
								+ ex.getMessage(), ex);

				lClient.reinitializeSession();
			}
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
