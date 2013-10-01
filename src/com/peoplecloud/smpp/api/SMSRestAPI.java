package com.peoplecloud.smpp.api;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.peoplecloud.smpp.persistable.vo.Message;
import com.peoplecloud.smpp.persistable.vo.MessageCallback;
import com.peoplecloud.smpp.service.SMSCallbackService;
import com.peoplecloud.smpp.service.MessagePersistanceService;

@Path("/api")
@SuppressWarnings("unchecked")
public class SMSRestAPI implements SMSMessageListener {
	private static final Logger logger = LoggerFactory
			.getLogger(SMSRestAPI.class);
	private String name;
	private HttpClient httpClient;
	private ISMSMessageRouter messageRouter;

	private ExecutorService msgForwardExecutorService;
	private MessagePersistanceService dbPersistanceService;
	private SMSCallbackService smsCallbackService;

	private Map<String, List<MessageCallback>> registeredListenersMap;

	public SMSRestAPI(String aName) {
		name = aName;
		registeredListenersMap = new HashMap<String, List<MessageCallback>>();
		msgForwardExecutorService = Executors.newFixedThreadPool(10);

		PoolingClientConnectionManager cxMgr = new PoolingClientConnectionManager(
				SchemeRegistryFactory.createDefault());
		cxMgr.setMaxTotal(100);
		cxMgr.setDefaultMaxPerRoute(20);

		httpClient = new DefaultHttpClient(cxMgr);
	}

	public ISMSMessageRouter getMessageRouter() {
		return messageRouter;
	}

	public void setMessageRouter(ISMSMessageRouter messageRouter) {
		this.messageRouter = messageRouter;
		this.messageRouter.registerListener(this);
	}

	public void setHttpClient(HttpClient httpclient) {
		this.httpClient = httpclient;
	}

	public HttpClient getHttpClient() {
		return httpClient;
	}

	public MessagePersistanceService getDbPersistanceService() {
		return dbPersistanceService;
	}

	public void setDbPersistanceService(
			MessagePersistanceService dbPersistanceService) {
		this.dbPersistanceService = dbPersistanceService;
	}

	public SMSCallbackService getSmsCallbackService() {
		return smsCallbackService;
	}

	public void setSmsCallbackService(SMSCallbackService aSmsCallbackService) {
		this.smsCallbackService = aSmsCallbackService;
	}

	private void registerCallback(MessageCallback aCallback) {
		List<MessageCallback> msgCallbackList = registeredListenersMap
				.get(aCallback.getShortCode());

		if (msgCallbackList == null) {
			msgCallbackList = new ArrayList<MessageCallback>();
			msgCallbackList.add(aCallback);

			registeredListenersMap.put(aCallback.getShortCode(),
					msgCallbackList);
		} else {
			msgCallbackList.add(aCallback);
		}
	}

	private boolean callbackRegistered(MessageCallback aCallback) {
		List<MessageCallback> msgCallbackList = registeredListenersMap
				.get(aCallback.getShortCode());

		if (msgCallbackList == null) {
			return false;
		} else {
			return msgCallbackList.contains(aCallback);
		}
	}

	public void initialize() {
		List<MessageCallback> lMsgCallbacks = smsCallbackService
				.getAllMessageCallbacks();

		if (lMsgCallbacks != null) {
			for (MessageCallback lCallback : lMsgCallbacks) {
				registerCallback(lCallback);
			}
		}
	}

	@POST
	@Path("/registercallback")
	@Produces("application/json")
	public Response registerReceiveSMSCallbacks(
			@FormParam("registerParams") String aRegisterParamsJSON)
			throws URISyntaxException {

		if (logger.isDebugEnabled()) {
			logger.debug("Request to register callback: " + aRegisterParamsJSON);
		}

		try {
			JSONObject lRequestJSON = (JSONObject) new JSONParser()
					.parse(aRegisterParamsJSON);

			String lApplicationName = lRequestJSON.get("application")
					.toString();
			String lCallback = lRequestJSON.get("callback").toString();
			String lCallbackMethod = lRequestJSON.get("callbackmethod")
					.toString();
			String[] lShortcodes = lRequestJSON.get("shortCodeList").toString()
					.split(",");

			MessageCallback msgCallBack = null;
			if (lShortcodes != null && lShortcodes.length != 0) {
				for (String lShortCode : lShortcodes) {
					msgCallBack = new MessageCallback();
					msgCallBack.setAppName(lApplicationName);
					msgCallBack.setCallBackURL(lCallback);
					msgCallBack.setShortCode(lShortCode);
					msgCallBack.setCallbackMethod(lCallbackMethod);
					msgCallBack.setRegisterDate(new Date());

					if (!callbackRegistered(msgCallBack)) {
						smsCallbackService.registerCallback(msgCallBack);
						registerCallback(msgCallBack);
					}
				}
			}
		} catch (ParseException e) {
			logger.error("Error parsing register SMS Callback: "
					+ aRegisterParamsJSON);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Registered Callback Successfully: "
					+ aRegisterParamsJSON);
		}

		return Response.status(Status.OK).entity(aRegisterParamsJSON).build();
	}

	@POST
	@Path("/unregistercallback")
	@Produces("application/json")
	public Response unregisterReceiveSMSCallbacks(
			@FormParam("unregisterParams") String aUnRegisterParamsJSON)
			throws URISyntaxException {

		if (logger.isDebugEnabled()) {
			logger.debug("Request to unregister callback: "
					+ aUnRegisterParamsJSON);
		}

		try {
			JSONObject lRequestJSON = (JSONObject) new JSONParser()
					.parse(aUnRegisterParamsJSON);

			String lApplicationName = lRequestJSON.get("application")
					.toString();
			String lCallback = lRequestJSON.get("callback").toString();
			String lCallbackMethod = lRequestJSON.get("callbackmethod")
					.toString();
			String lShortCode = lRequestJSON.get("shortcode").toString();

			MessageCallback msgCallBack = new MessageCallback();
			msgCallBack.setAppName(lApplicationName);
			msgCallBack.setCallBackURL(lCallback);
			msgCallBack.setShortCode(lShortCode);
			msgCallBack.setCallbackMethod(lCallbackMethod);

			List<MessageCallback> lCallbackList = registeredListenersMap
					.get(msgCallBack.getShortCode());
			if (lCallbackList != null) {
				int lIdx = lCallbackList.indexOf(msgCallBack);

				if (lIdx != -1) {
					smsCallbackService.unregisterCallback(lCallbackList
							.get(lIdx));
				}
			}
		} catch (ParseException e) {
			logger.error("Error parsing unregister SMS Callback: "
					+ aUnRegisterParamsJSON);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Registered Callback Successfully: "
					+ aUnRegisterParamsJSON);
		}

		return Response.status(Status.OK).entity(aUnRegisterParamsJSON).build();
	}

	@GET
	@Path("/send")
	@Produces("application/json")
	public Response requestSendMessage(@QueryParam("appname") String aAppName,
			@QueryParam("message") String aMsg,
			@QueryParam("from") String aSendFromNumber,
			@QueryParam("to") String aSendToNumber) throws URISyntaxException {
		return sendSMS(aMsg, aSendFromNumber, aSendToNumber, aAppName);
	}

	@POST
	@Path("/send")
	@Produces("application/json")
	public Response sendMessage(@FormParam("appname") String aAppName,
			@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber) throws URISyntaxException {
		return sendSMS(aMsg, aSendFromNumber, aSendToNumber, aAppName);
	}

	@GET
	@Path("/sendto")
	@Produces("application/json")
	public Response requestSendMessageTo(
			@QueryParam("appname") String aAppName,
			@QueryParam("message") String aMsg,
			@QueryParam("from") String aSendFromNumber,
			@QueryParam("to") String aSendToNumber,
			@QueryParam("smscid") Integer aSmscId) throws URISyntaxException {
		return sendSMSTo(aMsg, aSendFromNumber, aSendToNumber, aAppName, aSmscId);
	}

	@POST
	@Path("/sendto")
	@Produces("application/json")
	public Response sendMessageTo(@FormParam("appname") String aAppName,
			@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber,
			@FormParam("smscid") Integer aSmscId) throws URISyntaxException {
		return sendSMSTo(aMsg, aSendFromNumber, aSendToNumber, aAppName,
				aSmscId);
	}

	@POST
	@Path("/log")
	@Produces("application/json")
	public Response log(@FormParam("messageBody") String aMsg,
			@FormParam("shortcode") String aShortCodeNum,
			@FormParam("callbackurl") String aCallbackURL,
			@FormParam("appname") String aAppName,
			@FormParam("cellPhoneNumber") String aFromNumber,
			@FormParam("dateOfMessage") String aDate) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("messageBody", aMsg);
		lRequestJSON.put("shortcode", aShortCodeNum);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);
		lRequestJSON.put("cellPhoneNumber", aFromNumber);
		lRequestJSON.put("dateOfMessage", aDate);

		if (logger.isDebugEnabled()) {
			logger.debug("Log Request POST: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	@GET
	@Path("/log")
	@Produces("application/json")
	public Response requestLog(@QueryParam("messageBody") String aMsg,
			@QueryParam("shortcode") String aShortCodeNum,
			@QueryParam("callbackurl") String aCallbackURL,
			@QueryParam("appname") String aAppName,
			@QueryParam("cellPhoneNumber") String aFromNumber,
			@QueryParam("dateOfMessage") String aDate)
			throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("messageBody", aMsg);
		lRequestJSON.put("shortcode", aShortCodeNum);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);
		lRequestJSON.put("cellPhoneNumber", aFromNumber);
		lRequestJSON.put("dateOfMessage", aDate);

		if (logger.isDebugEnabled()) {
			logger.debug("Log Request GET: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	public Response requestForwardMessage(String aMsg, String aShortCode,
			String aFromNumber, String aCallbackURL, String aAppName)
			throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		String lResponseBody = "";
		try {
			String lDate = DateFormatUtils.ISO_DATETIME_FORMAT
					.format(new Date());
			lRequestJSON.put("messageBody", aMsg);
			lRequestJSON.put("shortcode", aShortCode);
			lRequestJSON.put("appname", aAppName);
			lRequestJSON.put("cellPhoneNumber", aFromNumber);
			lRequestJSON.put("dateOfMessage", lDate);

			String lGetURL = aCallbackURL + "?messageBody="
					+ java.net.URLEncoder.encode(aMsg, "UTF-8") + "&shortcode="
					+ aShortCode + "&appname=" + aAppName + "&callbackurl="
					+ java.net.URLEncoder.encode(aCallbackURL, "UTF-8")
					+ "&cellPhoneNumber=" + aFromNumber + "&dateOfMessage="
					+ lDate;
			lRequestJSON.put("callbackurl", lGetURL);

			if (logger.isDebugEnabled()) {
				logger.debug("Forward Msg via HTTP GET: "
						+ lRequestJSON.toJSONString());
			}

			HttpGet httpGet = new HttpGet(lGetURL);

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpGet, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);
		if (logger.isDebugEnabled()) {
			logger.debug("Status - Forward Msg via HTTP GET: "
					+ lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lResponseBody).build();
	}

	public Response forwardMessage(String aMsg, String aShortCodeNum,
			String aFromNumber, String aCallbackURL, String aAppName)
			throws URISyntaxException {

		String lDate = DateFormatUtils.ISO_DATETIME_FORMAT.format(new Date());
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("messageBody", aMsg);
		lRequestJSON.put("shortcode", aShortCodeNum);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);
		lRequestJSON.put("cellPhoneNumber", aFromNumber);
		lRequestJSON.put("dateOfMessage", lDate);

		if (logger.isDebugEnabled()) {
			logger.debug("Forward Msg via HTTP POST: "
					+ lRequestJSON.toJSONString());
		}

		HttpPost httpost = new HttpPost(aCallbackURL);
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("messageBody", aMsg));
			nvps.add(new BasicNameValuePair("shortcode", aShortCodeNum));
			nvps.add(new BasicNameValuePair("appname", aAppName));
			nvps.add(new BasicNameValuePair("callbackurl", aCallbackURL));
			nvps.add(new BasicNameValuePair("cellPhoneNumber", aFromNumber));
			nvps.add(new BasicNameValuePair("dateOfMessage", lDate));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, Charset
					.forName("UTF-8")));

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpost, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);
		if (logger.isDebugEnabled()) {
			logger.debug("Status - Forward Msg via HTTP POST: "
					+ lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lResponseBody).build();
	}

	private Response sendSMS(String aMsg, String aSendFromNumber,
			String aSendToNumber, String aApplication) {

		String lMsg = new String(CharsetUtil.encode(aMsg,
				CharsetUtil.NAME_MODIFIED_UTF8));

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", lMsg);
		lRequestJSON.put("from", aSendFromNumber);
		lRequestJSON.put("to", aSendToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Request: " + lRequestJSON.toJSONString());
		}

		String lSentMsg = messageRouter.sendSMS(aMsg, aSendFromNumber,
				aSendToNumber);
		lRequestJSON.put("statusmsg", lSentMsg);

		// Save message to database.
		persistMessageToDB(lMsg, aSendFromNumber, aSendToNumber, Message.MT,
				aApplication, lSentMsg,
				messageRouter.getLastUserServerToSendMessage());

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Status: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	private Response sendSMSTo(String aMsg, String aSendFromNumber,
			String aSendToNumber, String aApplication, Integer aSmscId) {

		String lMsg = new String(CharsetUtil.encode(aMsg,
				CharsetUtil.NAME_MODIFIED_UTF8));

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", lMsg);
		lRequestJSON.put("from", aSendFromNumber);
		lRequestJSON.put("to", aSendToNumber);
		lRequestJSON.put("smscid", aSmscId);

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Request: " + lRequestJSON.toJSONString());
		}

		String lSentMsg = messageRouter.sendSMSTo(aMsg, aSendFromNumber,
				aSendToNumber, aSmscId);
		lRequestJSON.put("statusmsg", lSentMsg);

		// Save message to database.
		persistMessageToDB(lMsg, aSendFromNumber, aSendToNumber, Message.MT,
				aApplication, lSentMsg,
				messageRouter.getSMSPPServerById(aSmscId));

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Status: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	public void notify(final String aMessage, final String aFromNumber,
			final String aToNumber, final String aSMPPSession) {

		final String lMsg = aMessage;
		// new String(aMessage.getBytes(Charset
		// .forName("UTF-8")), Charset.forName("UTF-8"));

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", lMsg);
		lRequestJSON.put("from", aFromNumber);
		lRequestJSON.put("to", aToNumber);
		lRequestJSON.put("messageFrom", aSMPPSession);

		if (logger.isDebugEnabled()) {
			logger.debug("Listener got notified :: "
					+ lRequestJSON.toJSONString());
		}

		try {
			// Invoke registered callback.
			List<MessageCallback> lCallbacks = registeredListenersMap
					.get(aToNumber);

			if (lCallbacks != null) {
				for (final MessageCallback lCallback : lCallbacks) {

					if (lCallback.getCallbackMethod().equals(
							MessageCallback.CALL_BACK_HTTP_METHOD_GET)) {

						msgForwardExecutorService.execute(new Runnable() {
							public void run() {

								String lStatus = lCallback.getAppName()
										+ ":"
										+ lCallback.getCallBackURL()
										+ ":"
										+ MessageCallback.CALL_BACK_HTTP_METHOD_POST;
								Response lResp = null;
								try {
									lResp = requestForwardMessage(lMsg,
											aToNumber, aFromNumber,
											lCallback.getCallBackURL(),
											lCallback.getAppName());

									if (logger.isDebugEnabled()) {
										logger.debug("Message forwarded successfully to : "
												+ lResp.getEntity().toString());
									}

									lStatus = lStatus + " ["
											+ lResp.getStatus() + ":"
											+ lResp.getEntity() + "]";
								} catch (Exception ex) {
									lStatus = lStatus
											+ " [Message could not be delivered successfully. Msg: "
											+ ex.getMessage() + "]";
									logger.error(lStatus, ex);
								}

								// Save message to database.
								persistMessageToDB(lMsg, aFromNumber,
										aToNumber, Message.MO,
										lCallback.getAppName(), lStatus,
										aSMPPSession);
							}
						});
					} else if (lCallback.getCallbackMethod().equals(
							MessageCallback.CALL_BACK_HTTP_METHOD_POST)) {
						msgForwardExecutorService.execute(new Runnable() {
							public void run() {

								String lStatus = lCallback.getAppName()
										+ ":"
										+ lCallback.getCallBackURL()
										+ ":"
										+ MessageCallback.CALL_BACK_HTTP_METHOD_POST;
								Response lResp = null;
								try {

									lResp = forwardMessage(lMsg, aToNumber,
											aFromNumber,
											lCallback.getCallBackURL(),
											lCallback.getAppName());

									if (logger.isDebugEnabled()) {
										logger.debug("Message forwarded successfully to : "
												+ lResp.getEntity().toString());
									}

									lStatus = lStatus + " ["
											+ lResp.getStatus() + ":"
											+ lResp.getEntity() + "]";
								} catch (Exception ex) {
									lStatus = lStatus
											+ " [Message could not be delivered successfully. Msg: "
											+ ex.getMessage() + "]";
									logger.error(lStatus, ex);
								}

								// Save message to database.
								persistMessageToDB(lMsg, aFromNumber,
										aToNumber, Message.MO,
										lCallback.getAppName(), lStatus,
										aSMPPSession);
							}
						});
					}
				}
			}
		} catch (Exception e) {
			logger.error("Could not forward received message via http: "
					+ lRequestJSON.toJSONString());
		}
	}

	private void persistMessageToDB(String aMessage, String aFromNumber,
			String aToNumber, String aMessageType, String aApplication,
			String aStatus, String aSMPPSession) {
		if (logger.isDebugEnabled()) {
			logger.debug("Persisting message to database: [" + aMessage + ", "
					+ aFromNumber + ", " + aToNumber + ", " + aMessageType
					+ ", " + aStatus + "]");
		}

		Message msg = new Message();
		msg.setMessage(aMessage);
		msg.setFromNumber(aFromNumber);
		msg.setToNumber(aToNumber);
		msg.setApplication(aApplication);
		msg.setStatus(aStatus);
		msg.setMessageServer(aSMPPSession);

		if (aMessageType.equals(Message.MO)) {
			msg.setReceivedDate(new Date());
		}

		if (aMessageType.equals(Message.MT)) {
			msg.setSentDate(new Date());
		}

		msg.setMessageType(aMessageType);

		dbPersistanceService.saveMessage(msg);

		if (logger.isDebugEnabled()) {
			logger.debug("Message persisting to database successfully: ["
					+ aMessage + ", " + aFromNumber + ", " + aToNumber + ", "
					+ aMessageType + "]");
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SMSRestAPI other = (SMSRestAPI) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SMSRestAPI [name=" + name + "]";
	}
}