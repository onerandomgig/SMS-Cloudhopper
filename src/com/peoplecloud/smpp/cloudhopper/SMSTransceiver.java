package com.peoplecloud.smpp.cloudhopper;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;

import com.peoplecloud.smpp.persistable.vo.MessageCallback;

@SuppressWarnings("unchecked")
public class SMSTransceiver {
	private DefaultHttpClient httpClient;

	private String SEND_SMS_END_POINT;
	private String RECEIVE_SMS_END_POINT;
	private String UNREGISTER_RECEIVE_SMS_END_POINT;

	public static void main(String[] args) {
		// Instantiate and set endpoints
		// SMSTransceiver lTranceiver = new SMSTransceiver();
		// lTranceiver.setSMSEndPoints("http://localhost:8080/api/send",
		// "http://localhost:8080/api/registercallback",
		// "http://localhost:8080/api/unregistercallback");

		// OR Instantiate by passing host.
		SMSTransceiver lTranceiver = new SMSTransceiver(
				"http://msg2.zenithss.com");

		String notificatinResponse = lTranceiver
				.registerReceieveSMSCallbackURL("PMT",
						"http://web2.zenithss.com/scmppush/sms",
						MessageCallback.CALL_BACK_HTTP_METHOD_POST,
						new String[] { "200" });
		System.out.println("Notification has started: " + notificatinResponse);
	}

	public SMSTransceiver() {
		httpClient = new DefaultHttpClient();
	}

	public SMSTransceiver(String aHost) {
		this();

		SEND_SMS_END_POINT = aHost + "/api/send";
		RECEIVE_SMS_END_POINT = aHost + "/api/registercallback";
		UNREGISTER_RECEIVE_SMS_END_POINT = aHost + "/api/unregistercallback";
	}

	public void setSMSEndPoints(String aSendEndPoint,
			String aRegisterReceiverEndPoint, String aUnregisterReceiverEndPoint) {
		SEND_SMS_END_POINT = aSendEndPoint;
		RECEIVE_SMS_END_POINT = aRegisterReceiverEndPoint;
		UNREGISTER_RECEIVE_SMS_END_POINT = aUnregisterReceiverEndPoint;
	}

	public String unregisterSMSCallbackURL(String aAppName,
			String aCallBackURL, String aCallBackMethod, String aShortCode) {
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("application", aAppName);
		lRequestJSON.put("callback", aCallBackURL);
		lRequestJSON.put("callbackmethod", aCallBackMethod);
		lRequestJSON.put("shortcode", aShortCode);

		HttpPost httpost = new HttpPost(UNREGISTER_RECEIVE_SMS_END_POINT);
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("unregisterParams", lRequestJSON
					.toJSONString()));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpost, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);

		return lRequestJSON.toJSONString();
	}

	public String registerReceieveSMSCallbackURL(String aAppName,
			String aCallBackURL, String aCallBackMethod, String[] aShortCodeList) {
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("application", aAppName);
		lRequestJSON.put("callback", aCallBackURL);
		lRequestJSON.put("callbackmethod", aCallBackMethod);

		if (aShortCodeList == null || aShortCodeList.length == 0) {
			return "{\"status\":\"Error. Callback not registered. Shortcode list cannot be empty\"}";
		}

		StringBuffer lShortCodeBuffer = new StringBuffer();
		for (String lShortCode : aShortCodeList) {
			lShortCodeBuffer.append(lShortCode).append(",");
		}
		lRequestJSON.put("shortCodeList",
				lShortCodeBuffer.substring(0, lShortCodeBuffer.length() - 1));

		HttpPost httpost = new HttpPost(RECEIVE_SMS_END_POINT);
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("registerParams", lRequestJSON
					.toJSONString()));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpost, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);

		return lRequestJSON.toJSONString();
	}

	public String sendSMS(String aMsg, String aFromNumber, String aToNumber) {
		JSONObject lRequestJSON = new JSONObject();
		lRequestJSON.put("msg", aMsg);
		lRequestJSON.put("from", aFromNumber);
		lRequestJSON.put("to", aToNumber);

		HttpPost httpost = new HttpPost(SEND_SMS_END_POINT);
		String lResponseBody = "";

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("message", aMsg));
			nvps.add(new BasicNameValuePair("from", aFromNumber));
			nvps.add(new BasicNameValuePair("to", aToNumber));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

			ResponseHandler<String> lResponseHandler = new BasicResponseHandler();
			lResponseBody = httpClient.execute(httpost, lResponseHandler);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		lRequestJSON.put("response", lResponseBody);

		return lRequestJSON.toJSONString();
	}
}