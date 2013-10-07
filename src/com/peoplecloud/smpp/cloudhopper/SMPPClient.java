package com.peoplecloud.smpp.cloudhopper;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.peoplecloud.smpp.api.SMSMessageListener;
import com.peoplecloud.smpp.exception.SMPPBindFailedException;
import com.peoplecloud.smpp.utils.VelocityEmailSender;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class SMPPClient {
	private static final Logger logger = LoggerFactory
			.getLogger(SMPPClient.class);

	private boolean requestDeliveryReceipt;
	private boolean isRunning;
	private SmppSession smppSession;

	private Properties configProperties;

	private Thread mSMSCConnMonitorThread;
	private ThreadPoolExecutor executor;
	private ScheduledThreadPoolExecutor monitorExecutor;
	private DefaultSmppClient clientBootstrap;

	private VelocityEmailSender velocityEmailSenderService;
	private ClientSmppSessionHandler sessionHandler;

	private int sessionNum;

	private List<SMSMessageListener> listOfMessageListeners;

	public SMPPClient(Properties aProps, int aSessionNum,
			VelocityEmailSender aVelocityEmailSenderService) {
		sessionNum = aSessionNum;
		configProperties = aProps;
		listOfMessageListeners = new ArrayList<SMSMessageListener>();

		velocityEmailSenderService = aVelocityEmailSenderService;

		sessionHandler = new ClientSmppSessionHandler(this,
				listOfMessageListeners, velocityEmailSenderService,
				configProperties);
	}

	public void initialize() throws SMPPBindFailedException {
		requestDeliveryReceipt = Boolean.parseBoolean(configProperties
				.getProperty("smsc.server.requestdeliveryreceipt"));
		//
		// setup 3 things required for any session we plan on creating
		//

		// For monitoring thread use, it's preferable to create your own
		// instance of an executor with Executors.newCachedThreadPool() and cast
		// it to ThreadPoolExecutor this permits exposing thinks like
		// executor.getActiveCount() via JMX possible no point renaming the
		// threads in a factory since underlying Netty framework does not easily
		// allow you to customize your thread names.
		executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

		// to enable automatic expiration of requests, a second scheduled
		// executor is required which is what a monitor task will be executed
		// with - this is probably a thread pool that can be shared with between
		// all client bootstraps
		monitorExecutor = (ScheduledThreadPoolExecutor) Executors
				.newScheduledThreadPool(1, new ThreadFactory() {
					private AtomicInteger sequence = new AtomicInteger(0);

					@Override
					public Thread newThread(Runnable r) {
						Thread t = new Thread(r);
						t.setName("SmppClientSessionWindowMonitorPool-"
								+ sequence.getAndIncrement());
						return t;
					}
				});

		// a single instance of a client bootstrap can technically be shared
		// between any sessions that are created (a session can go to any
		// different number of SMSCs) - each session created under a client
		// bootstrap will use the executor and monitorExecutor set in its
		// constructor - just be *very* careful with the "expectedSessions"
		// value to make sure it matches the actual number of total concurrent
		// open sessions you plan on handling - the underlying netty library
		// used for NIO sockets essentially uses this value as the max number of
		// threads it will ever use, despite the "max pool size", etc. set on
		// the executor passed in here
		clientBootstrap = new DefaultSmppClient(
				Executors.newCachedThreadPool(), 1, monitorExecutor);

		//
		// setup configuration for a client session
		//
		bindSMPPSession();
	}

	public void reinitializeSession() {
		sessionHandler.handleReinitialize();
	}

	/**
	 * Could either implement SmppSessionHandler or only override select methods
	 * by extending a DefaultSmppSessionHandler.
	 */
	public static class ClientSmppSessionHandler extends
			DefaultSmppSessionHandler {

		private List<SMSMessageListener> listOfListeners;
		private SMPPClient thisClient;
		private VelocityEmailSender velocityEmailSenderService;
		private Properties configProperties;

		public ClientSmppSessionHandler(SMPPClient aClient,
				List<SMSMessageListener> aListOfListeners,
				VelocityEmailSender aVelocityEmailSenderService,
				Properties configProps) {
			super(logger);
			thisClient = aClient;
			listOfListeners = aListOfListeners;
			velocityEmailSenderService = aVelocityEmailSenderService;
			configProperties = configProps;
		}

		public void handleReinitialize() {
			new Thread(new Runnable() {
				public void run() {
					boolean isRestarted = false;
					while (!isRestarted) {
						try {
							int lReconnectTime = Integer.parseInt(configProperties
									.getProperty("smpp.reconnect.time.interval"));
							velocityEmailSenderService.sendMail(
									"connectfail.vm",
									thisClient.getClientName(),
									configProperties
											.getProperty("smpp.email.error.alert.subject")
											+ " "
											+ thisClient.getClientName()
											+ " **",
									"SMPP Connection failed to server "
											+ thisClient.getClientName()
											+ ". Will attempt to reconnect in "
											+ lReconnectTime + " minutes.");

							thisClient.shutdown();
							logger.error("Shutting down SMPP Connection. Will reinitalize in "
									+ lReconnectTime + " minutes.");
							Thread.sleep(lReconnectTime * 60 * 1000);
							logger.error("Shutdown Complete. Will **REINITIALIZE** now.");
							thisClient.initialize();

							isRestarted = true;
							break;
						} catch (Exception ex) {
							logger.error(
									"Failed to reinitialize. Channel was closed unexpectedly. SMPP Client "
											+ thisClient.getClientName()
											+ " will not work. Will attempt to reinitialize again. Error is: ",
									ex);
						}
					}
				}
			}).start();
		}

		public void fireChannelUnexpectedlyClosed() {
			logger.error("Default handling is to discard an unexpected channel closed");
			handleReinitialize();
		}

		@Override
		public void fireUnknownThrowable(Throwable t) {
			if (t instanceof ClosedChannelException) {
				logger.warn("Unknown throwable received, but it was a ClosedChannelException, calling fireChannelUnexpectedlyClosed instead");
				fireChannelUnexpectedlyClosed();
			} else {
				logger.warn(
						"Default handling is to discard an unknown throwable:",
						t);
			}
		}

		@Override
		public void firePduRequestExpired(PduRequest pduRequest) {
			logger.warn("PDU request expired: [ " + pduRequest
					+ " ] [ Command ID : " + pduRequest.getCommandId()
					+ ", Command Status : " + pduRequest.getCommandStatus()
					+ ", Command Length : " + pduRequest.getCommandStatus()
					+ " ]");
		}

		@Override
		public PduResponse firePduRequestReceived(PduRequest pduRequest) {
			PduResponse response = pduRequest.createResponse();

			// do any logic here
			if (logger.isDebugEnabled()) {
				logger.debug("PDU request received: [ " + pduRequest
						+ " ], Command ID: " + pduRequest.getCommandId());
			}

			if (pduRequest.getCommandId() == SmppConstants.CMD_ID_DELIVER_SM) {
				DeliverSm mo = (DeliverSm) pduRequest;

				Address sourceAddress = mo.getSourceAddress();
				Address destAddress = mo.getDestAddress();
				byte[] shortMessage = mo.getShortMessage();
				String sms = CharsetUtil.decode(shortMessage,
						CharsetUtil.CHARSET_ISO_8859_1);

				if (logger.isDebugEnabled()) {
					logger.debug("Received Message is :: " + sms + ", from :: "
							+ sourceAddress.getAddress());
				}

				for (SMSMessageListener lListener : listOfListeners) {
					lListener.notify(sms, sourceAddress.getAddress(),
							destAddress.getAddress(),
							thisClient.getClientName());
				}
			}

			return response;
		}
	}

	public synchronized String sendSMSMessage(String aMessage,
			String aSentFromNumber, String aSendToNumber) {
		byte[] textBytes = CharsetUtil.encode(aMessage,
				CharsetUtil.CHARSET_ISO_8859_1);

		try {
			SubmitSm submitMsg = new SubmitSm();

			// add delivery receipt if enabled.
			if (requestDeliveryReceipt) {
				submitMsg
						.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
			}

			submitMsg.setSourceAddress(new Address((byte) 0x03, (byte) 0x00,
					aSentFromNumber));
			submitMsg.setDestAddress(new Address((byte) 0x01, (byte) 0x01,
					aSendToNumber));
			submitMsg.setShortMessage(textBytes);

			logger.debug("About to send message to " + aSendToNumber
					+ ", Msg is :: " + aMessage + ", from :: "
					+ aSentFromNumber + " using session " + sessionNum);

			SubmitSmResp submitResp = smppSession.submit(submitMsg, 15000);
			logger.debug("Message sent to " + aSendToNumber
					+ " with message id " + submitResp.getMessageId()
					+ " using session " + sessionNum);
			return "Message ID - " + submitResp.getMessageId();
		} catch (Exception ex) {
			logger.error("Exception sending message [Msg, From, To] :: ["
					+ aMessage + ", " + aSentFromNumber + ", " + aSendToNumber
					+ ", {Session Number: }" + sessionNum + "]", ex);
		}

		logger.debug("Message **NOT** sent to " + aSendToNumber
				+ " from {session number} " + sessionNum);
		return "Message Not Submitted to " + aSendToNumber;
	}

	public void registerListener(SMSMessageListener aMsgListener) {
		if (!listOfMessageListeners.contains(aMsgListener)) {
			listOfMessageListeners.add(aMsgListener);
		}
	}

	public void setListOfMessageListeners(List<SMSMessageListener> aListenerList) {
		listOfMessageListeners = aListenerList;
	}

	public List<SMSMessageListener> getListOfMessageListeners() {
		return listOfMessageListeners;
	}

	public String getClientName() {
		return configProperties.getProperty("smsc.connection.name."
				+ sessionNum);
	}

	public void bindSMPPSession() throws SMPPBindFailedException {
		logger.info("Binding SMPP Session " + sessionNum + " ...");

		SmppSessionConfiguration smppSessionConfig = new SmppSessionConfiguration();
		smppSessionConfig.setWindowSize(1);
		smppSessionConfig.setName(configProperties
				.getProperty("smsc.connection.name." + sessionNum));
		smppSessionConfig.setType(SmppBindType.TRANSCEIVER);
		smppSessionConfig.setConnectTimeout(10000);
		smppSessionConfig.getLoggingOptions().setLogBytes(true);

		smppSessionConfig.setHost(configProperties
				.getProperty("smsc.server.host." + sessionNum));
		smppSessionConfig.setPort(Integer.parseInt(configProperties
				.getProperty("smsc.server.port." + sessionNum)));
		smppSessionConfig.setSystemId(configProperties
				.getProperty("smsc.server.systemid." + sessionNum));
		smppSessionConfig.setPassword(configProperties
				.getProperty("smsc.server.password." + sessionNum));

		// to enable monitoring (request expiration)
		smppSessionConfig.setRequestExpiryTimeout(30000);
		smppSessionConfig.setWindowMonitorInterval(15000);
		smppSessionConfig.setCountersEnabled(true);

		//
		// create session, enquire link, submit an sms, close session
		//
		try {
			// create a session by having the bootstrap connect a
			// socket, send the bind request, and wait for a bind response
			smppSession = clientBootstrap.bind(smppSessionConfig,
					sessionHandler);

			isRunning = true;
			
			// send periodic requests to keep session alive.
			startAsynchronousSMPPConnectionMonitor();

			// Send Bind success email.
			velocityEmailSenderService.sendMail(
					"connectpass.vm",
					getClientName(),
					configProperties
							.getProperty("smpp.email.success.alert.subject")
							+ " " + getClientName() + " **",
					"SMPP Connection succeeded to server " + getClientName());
		} catch (Exception e) {

			String errorMsg = "Error occured while binding smpp session "
					+ getClientName()
					+ ". Cannot send or receive any messages. Error is : "
					+ e.getMessage();
			logger.error(errorMsg);

			throw new SMPPBindFailedException(
					getClientName()
							+ " failed to bind. Cannot send or receive any messages. Error is : "
							+ e.getMessage(), e);
		}
	}

	private void startAsynchronousSMPPConnectionMonitor() {

		if (mSMSCConnMonitorThread != null && mSMSCConnMonitorThread.isAlive()) {
			return;
		}

		mSMSCConnMonitorThread = new Thread(new Runnable() {
			public void run() {
				while (isRunning) {
					try {
						// "asynchronous" enquireLink call - send it, get a
						// future, and then optionally choose to pick when we
						// wait for it
						WindowFuture<Integer, PduRequest, PduResponse> enquireLinkFuture = smppSession
								.sendRequestPdu(new EnquireLink(), 100000, true);

						if (!enquireLinkFuture.await()) {
							logger.warn("Failed to receive enquire_link_resp within specified time for session "
									+ sessionNum);
						} else if (enquireLinkFuture.isSuccess()) {
							EnquireLinkResp enquireLinkResp = (EnquireLinkResp) enquireLinkFuture
									.getResponse();
							logger.warn("enquire link response: commandStatus [Session Num: "
									+ sessionNum
									+ ", "
									+ enquireLinkResp.getCommandStatus()
									+ "="
									+ enquireLinkResp.getResultMessage() + "]");
						} else {
							logger.warn("Failed to properly receive enquire link response for session : "
									+ sessionNum
									+ ", "
									+ enquireLinkFuture.getCause());
						}

						// Wait for the timeout interval before rechecking.
						try {
							Thread.sleep(Long.parseLong(configProperties
									.getProperty("smpp.session.enquirelink.interval")));
						} catch (InterruptedException ie) {
							// Ignore.
						}
					} catch (Exception e) {
						logger.error(
								"Exception occured while waiting for enquire link response for session : "
										+ sessionNum + ", " + e.getMessage(), e);
					}
				}
			}
		});

		mSMSCConnMonitorThread.start();
	}

	public void releaseSMPPSession() {
		logger.info("Releasing SMPP Session " + sessionNum + " ...");
		if (smppSession != null) {
			smppSession.unbind(5000);
		}
	}

	public void shutdown() {
		// Stop the enquire link thread.
		isRunning = false;

		if (mSMSCConnMonitorThread != null) {
			mSMSCConnMonitorThread.interrupt();
			mSMSCConnMonitorThread = null;
		}

		try {
			// Wait for the enquire link timeout at most (Not sure if its
			// required as i have already interrupted the thread)
			Thread.sleep(Long.parseLong(configProperties
					.getProperty("smpp.session.enquirelink.interval")));
		} catch (InterruptedException ie) {
			// Ignore.
		}

		// this is required to not causing server to hang from non-daemon
		// threads this also makes sure all open Channels are closed to I
		// *think*
		logger.info("Releasing SMPP Session " + sessionNum
				+ " and shutting down client bootstrap and executors...");

		releaseSMPPSession();

		if (smppSession != null) {
			logger.info("Cleaning up session " + sessionNum
					+ " ... (logging final counters)");

			if (smppSession.hasCounters()) {
				logger.info("tx-enquireLink :: "
						+ smppSession.getCounters().getTxEnquireLink());
				logger.info("tx-submitSM :: "
						+ smppSession.getCounters().getTxSubmitSM());
				logger.info("tx-deliverSM :: "
						+ smppSession.getCounters().getTxDeliverSM());
				logger.info("tx-dataSM :: "
						+ smppSession.getCounters().getTxDataSM());
				logger.info("rx-enquireLink :: "
						+ smppSession.getCounters().getRxEnquireLink());
				logger.info("rx-submitSM :: "
						+ smppSession.getCounters().getRxSubmitSM());
				logger.info("rx-deliverSM :: "
						+ smppSession.getCounters().getRxDeliverSM());
				logger.info("rx-dataSM :: "
						+ smppSession.getCounters().getRxDataSM());
			}

			smppSession.destroy();
			// alternatively, could call close(), get outstanding requests from
			// the sendWindow (if we wanted to retry them later), then call
			// shutdown()
		}

		clientBootstrap.destroy();
		executor.shutdownNow();
		monitorExecutor.shutdownNow();
	}

	public static Properties getDefaultProperties() {
		Properties lDefaultProps = new Properties();
		lDefaultProps.put("smsc.server.host", "XX.XX.XX.XX");
		lDefaultProps.put("smsc.server.port", "0000");
		lDefaultProps.put("smsc.server.systemid", "XXXXX");
		lDefaultProps.put("smsc.server.password", "XXXXX");

		lDefaultProps.put("smsc.server.requestdeliveryreceipt", "false");
		lDefaultProps.put("smpp.session.enquirelink.interval", "30000");

		return lDefaultProps;
	}
}