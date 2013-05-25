package com.peoplecloud.smpp.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.peoplecloud.smpp.dao.SMSCallbackDAO;
import com.peoplecloud.smpp.persistable.vo.MessageCallback;

@SuppressWarnings("rawtypes")
public class SMSCallbackService {
	private SMSCallbackDAO callbackDAO;

	public SMSCallbackDAO getCallbackDAO() {
		return callbackDAO;
	}

	public void setCallbackDAO(SMSCallbackDAO callbackDAO) {
		this.callbackDAO = callbackDAO;
	}

	@Transactional
	public void registerCallback(MessageCallback aCallback) {
		MessageCallback lSavedCallback = callbackDAO.save(aCallback);
		aCallback.setId(lSavedCallback.getId());
	}

	@Transactional
	public void unregisterCallback(MessageCallback aCallback) {
		callbackDAO.delete(aCallback);
	}

	public List getAllMessageCallbacks() {
		return callbackDAO.listAllCallbacks();
	}
}
