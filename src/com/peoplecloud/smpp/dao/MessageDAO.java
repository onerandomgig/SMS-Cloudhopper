package com.peoplecloud.smpp.dao;

import java.util.List;

import com.peoplecloud.smpp.persistable.vo.Message;

public class MessageDAO extends BasicDAO<Message, Long> {
	public MessageDAO() {
		initialize(Message.class);
	}

	public List<Message> listAllMessages() {
		return findAll();
	}
}