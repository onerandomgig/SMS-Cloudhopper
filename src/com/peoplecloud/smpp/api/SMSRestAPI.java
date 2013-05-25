package com.peoplecloud.smpp.api;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peoplecloud.smpp.cloudhopper.SMPPClient;
import com.peoplecloud.smpp.persistable.vo.Message;
import com.peoplecloud.smpp.persistable.vo.MessageCallback;
import com.peoplecloud.smpp.service.SMSCallbackService;
import com.peoplecloud.smpp.service.MessagePersistanceService;

@Path("/api")
@SuppressWarnings("unchecked")
public class SMSRestAPI implements SMSMessageListener {
	private static final Logger logger = LoggerFactory
			.getLogger(SMSRestAPI.class);

	private DefaultHttpClient httpClient;
	private SMPPClient smppClient;

	private MessagePersistanceService dbPersistanceService;
	private SMSCallbackService smsCallbackService;

	private Map<String, List<MessageCallback>> registeredListenersMap;

	public SMSRestAPI() {
		registeredListenersMap = new HashMap<String, List<MessageCallback>>();
	}

	public SMPPClient getSmppClient() {
		return smppClient;
	}

	public void setSmppClient(SMPPClient smppClient) {
		this.smppClient = smppClient;
		this.smppClient.registerListener(this);
	}

	public void setHttpClient(DefaultHttpClient httpclient) {
		this.httpClient = httpclient;
	}

	public DefaultHttpClient getHttpClient() {
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
	public Response requestSendMessage(@QueryParam("message") String aMsg,
			@QueryParam("from") String aSendFromNumber,
			@QueryParam("to") String aSendToNumber) throws URISyntaxException {
		return sendSMS(aMsg, aSendFromNumber, aSendToNumber);
	}

	@POST
	@Path("/send")
	@Produces("application/json")
	public Response sendMessage(@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber) throws URISyntaxException {
		return sendSMS(aMsg, aSendFromNumber, aSendToNumber);
	}

	@POST
	@Path("/log")
	@Produces("application/json")
	public Response log(@FormParam("msg") String aMsg,
			@FormParam("shortcode") String aShortCodeNum,
			@FormParam("callbackurl") String aCallbackURL,
			@FormParam("appname") String aAppName) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("shortcode", aShortCodeNum);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);

		if (logger.isDebugEnabled()) {
			logger.debug("Log Request: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	public Response requestForwardMessage(String aMsg, String aShortCode,
			String aCallbackURL, String aAppName) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("shortcode", aShortCode);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);

		if (logger.isDebugEnabled()) {
			logger.debug("Forward Msg via HTTP GET: "
					+ lRequestJSON.toJSONString());
		}

		String lResponseBody = "";
		try {
			HttpGet httpGet = new HttpGet(aCallbackURL + "?message=" + aMsg
					+ "&shortcode=" + aShortCode + "&app=" + aAppName);

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
			String aCallbackURL, String aAppName) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("shortcode", aShortCodeNum);
		lRequestJSON.put("callbackurl", aCallbackURL);
		lRequestJSON.put("appname", aAppName);

		if (logger.isDebugEnabled()) {
			logger.debug("Forward Msg via HTTP POST: "
					+ lRequestJSON.toJSONString());
		}

		HttpPost httpost = new HttpPost(aCallbackURL);
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("message", aMsg));
			nvps.add(new BasicNameValuePair("shortcode", aShortCodeNum));
			nvps.add(new BasicNameValuePair("app", aAppName));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpost, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);
		if (logger.isDebugEnabled()) {
			logger.debug("Received Msg: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lResponseBody).build();
	}

	private Response sendSMS(String aMsg, String aSendFromNumber,
			String aSendToNumber) {
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aSendFromNumber);
		lRequestJSON.put("to", aSendToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Request: " + lRequestJSON.toJSONString());
		}

		// Save message to database.
		persistMessageToDB(aMsg, aSendFromNumber, aSendToNumber, Message.MT);

		String lSentMsg = smppClient.sendSMSMessage(aMsg, aSendFromNumber,
				aSendToNumber);
		lRequestJSON.put("statusmsg", lSentMsg);

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Status: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	public void notify(String aMessage, String aFromNumber, String aToNumber) {
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMessage);
		lRequestJSON.put("from", aFromNumber);
		lRequestJSON.put("to", aToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Listener got notified :: "
					+ lRequestJSON.toJSONString());
		}

		try {
			// Save message to database.
			persistMessageToDB(aMessage, aFromNumber, aToNumber, Message.MO);

			// Invoke registered callback.
			List<MessageCallback> lCallbacks = registeredListenersMap
					.get(aToNumber);

			Response lResp = null;
			if (lCallbacks != null) {
				for (MessageCallback lCallback : lCallbacks) {
					if (lCallback.getCallbackMethod().equals(
							MessageCallback.CALL_BACK_HTTP_METHOD_GET)) {
						lResp = requestForwardMessage(
								new String(aMessage.getBytes(), "utf-8"),
								aToNumber, lCallback.getCallBackURL(),
								lCallback.getAppName());
					} else if (lCallback.getCallbackMethod().equals(
							MessageCallback.CALL_BACK_HTTP_METHOD_POST)) {
						lResp = forwardMessage(aMessage, aToNumber,
								lCallback.getCallBackURL(),
								lCallback.getAppName());
					}
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Message forwarded successfully: "
						+ lResp.getEntity().toString());
			}
		} catch (Exception e) {
			logger.error("Could not forward received message via http: "
					+ lRequestJSON.toJSONString());
		}
	}

	private void persistMessageToDB(String aMessage, String aFromNumber,
			String aToNumber, String aMessageType) {
		if (logger.isDebugEnabled()) {
			logger.debug("Persisting message to database: [" + aMessage + ", "
					+ aFromNumber + ", " + aToNumber + ", " + aMessageType
					+ "]");
		}

		Message msg = new Message();
		msg.setMessage(aMessage);
		msg.setFromNumber(aFromNumber);
		msg.setToNumber(aToNumber);

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
}