package com.peoplecloud.smpp.dao;

import java.util.List;

import com.peoplecloud.smpp.persistable.vo.MessageCallback;

public class SMSCallbackDAO extends BasicDAO<MessageCallback, Long> {
	public SMSCallbackDAO() {
		initialize(MessageCallback.class);
	}

	public List<MessageCallback> listAllCallbacks() {
		return findAll();
	}
}