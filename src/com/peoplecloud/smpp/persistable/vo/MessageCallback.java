package com.peoplecloud.smpp.persistable.vo;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "message_call_back")
public class MessageCallback {
	private long id;
	private String appName;
	private String callBackURL;
	private String shortCode;
	private String callbackMethod;

	private Date registerDate;

	public static final String CALL_BACK_HTTP_METHOD_POST = "HTTP_POST";
	public static final String CALL_BACK_HTTP_METHOD_GET = "HTTP_GET";

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@Basic
	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	@Basic
	public String getCallBackURL() {
		return callBackURL;
	}

	public void setCallBackURL(String callBackURL) {
		this.callBackURL = callBackURL;
	}

	@Basic
	public String getShortCode() {
		return shortCode;
	}

	public void setShortCode(String shortCode) {
		this.shortCode = shortCode;
	}

	@Basic
	public String getCallbackMethod() {
		return callbackMethod;
	}

	public void setCallbackMethod(String callbackMethod) {
		this.callbackMethod = callbackMethod;
	}

	@Basic
	public Date getRegisterDate() {
		return registerDate;
	}

	public void setRegisterDate(Date registerDate) {
		this.registerDate = registerDate;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appName == null) ? 0 : appName.hashCode());
		result = prime * result
				+ ((callBackURL == null) ? 0 : callBackURL.hashCode());
		result = prime * result
				+ ((callbackMethod == null) ? 0 : callbackMethod.hashCode());
		result = prime * result
				+ ((shortCode == null) ? 0 : shortCode.hashCode());
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
		MessageCallback other = (MessageCallback) obj;
		if (appName == null) {
			if (other.appName != null)
				return false;
		} else if (!appName.equals(other.appName))
			return false;
		if (callBackURL == null) {
			if (other.callBackURL != null)
				return false;
		} else if (!callBackURL.equals(other.callBackURL))
			return false;
		if (callbackMethod == null) {
			if (other.callbackMethod != null)
				return false;
		} else if (!callbackMethod.equals(other.callbackMethod))
			return false;
		if (shortCode == null) {
			if (other.shortCode != null)
				return false;
		} else if (!shortCode.equals(other.shortCode))
			return false;
		return true;
	}
}