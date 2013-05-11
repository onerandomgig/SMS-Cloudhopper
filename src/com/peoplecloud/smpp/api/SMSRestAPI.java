package com.peoplecloud.smpp.api;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peoplecloud.smpp.cloudhopper.SMPPClient;

@Path("/api")
@SuppressWarnings("unchecked")
public class SMSRestAPI implements SMSMessageListener {
	private static final Logger logger = LoggerFactory
			.getLogger(SMSRestAPI.class);

	private DefaultHttpClient httpClient;
	private SMPPClient smppClient;

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

	@POST
	@Path("/send")
	@Produces("application/json")
	public Response sendMessage(@FormParam("message") String aMsg,
			@FormParam("from") String aSendFromNumber,
			@FormParam("to") String aSendToNumber) throws URISyntaxException {

		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aSendFromNumber);
		lRequestJSON.put("to", aSendToNumber);

		if (logger.isDebugEnabled()) {
			logger.debug("Send Msg Request: " + lRequestJSON.toJSONString());
		}

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
}