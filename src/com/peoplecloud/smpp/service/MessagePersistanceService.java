package com.peoplecloud.smpp.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.peoplecloud.smpp.dao.MessageDAO;
import com.peoplecloud.smpp.persistable.vo.Message;

@SuppressWarnings("rawtypes")
public class MessagePersistanceService {
	private MessageDAO msgDAO;

	public MessageDAO getMsgDAO() {
		return msgDAO;
	}

	public void setMsgDAO(MessageDAO msgDao) {
		this.msgDAO = msgDao;
	}

	@Transactional
	public void saveMessage(Message aMsg) {
		msgDAO.save(aMsg);
	}

	public List getAllMessages() {
		return msgDAO.listAllMessages();
	}
}
