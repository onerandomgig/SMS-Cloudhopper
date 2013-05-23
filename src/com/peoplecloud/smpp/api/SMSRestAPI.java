package com.peoplecloud.smpp.api;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peoplecloud.smpp.cloudhopper.SMPPClient;
import com.peoplecloud.smpp.persistable.vo.Message;
import com.peoplecloud.smpp.service.MessagePersistanceService;

@Path("/api")
@SuppressWarnings("unchecked")
public class SMSRestAPI implements SMSMessageListener {
	private static final Logger logger = LoggerFactory
			.getLogger(SMSRestAPI.class);

	private DefaultHttpClient httpClient;
	private SMPPClient smppClient;
	private MessagePersistanceService dbPersistanceService;

	private Properties configProps;

	public SMSRestAPI(Properties aProps) {
		configProps = aProps;
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

	@POST
	@Path("/receive")
	@Produces("application/json")
	public Response receiveMessage(@FormParam("message") String aMsg,
			@FormParam("from") String aReceiveFromNumber,
			@FormParam("to") String aSentToNumber) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aReceiveFromNumber);
		lRequestJSON.put("to", aSentToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Received Msg: " + lRequestJSON.toJSONString());
		}

		HttpPost httpost = new HttpPost(
				configProps.getProperty("smpp.http.forward.listener.url"));
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("message", aMsg));
			nvps.add(new BasicNameValuePair("from", aReceiveFromNumber));
			nvps.add(new BasicNameValuePair("to", aSentToNumber));

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

	@GET
	@Path("/receive")
	@Produces("application/json")
	public Response sendReceiveMessage(@QueryParam("message") String aMsg,
			@QueryParam("from") String aReceiveFromNumber,
			@QueryParam("to") String aSentToNumber) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aReceiveFromNumber);
		lRequestJSON.put("to", aSentToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Received Msg: " + lRequestJSON.toJSONString());
		}

		HttpGet httpGet = new HttpGet(
				configProps.getProperty("smpp.http.forward.listener.url")
						+ "?message=" + aMsg + "&from=" + aReceiveFromNumber
						+ "&to=" + aSentToNumber);
		String lResponseBody = "";

		try {
			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpGet, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);
		if (logger.isDebugEnabled()) {
			logger.debug("Received Msg: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lResponseBody).build();
	}

	@POST
	@Path("/log")
	@Produces("application/json")
	public Response log(@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("logged-msg", aMsg);
		lRequestJSON.put("logged-from", aSendFromNumber);
		lRequestJSON.put("logged-to", aSendToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Log Request: " + lRequestJSON.toJSONString());
		}

		return Response.status(Status.OK).entity(lRequestJSON).build();
	}

	@POST
	@Path("/forward")
	@Produces("application/json")
	public Response forward(@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aSendFromNumber);

		String lForwardToNumbers = configProps
				.getProperty("smsc.server.forward.numbers");
		String[] lNumbers = lForwardToNumbers.split(",");

		if (logger.isDebugEnabled()) {
			logger.debug("Forward Request: " + lRequestJSON.toJSONString());
		}

		JSONArray lResponseArray = new JSONArray();

		for (String lNumberToFwdTo : lNumbers) {
			Response lResp = sendMessage(aMsg, aSendFromNumber,
					lNumberToFwdTo.trim());
			lResponseArray.add(lResp.getEntity());
		}

		return Response.status(Status.OK).entity(lResponseArray.toJSONString())
				.build();
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

			// Invoke registered URL.
			Response lResp = receiveMessage(aMessage, aFromNumber, aToNumber);

			if (logger.isDebugEnabled()) {
				logger.debug("HTTP receive message invoked successfully for: "
						+ lResp.getEntity().toString());
			}
		} catch (URISyntaxException e) {
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