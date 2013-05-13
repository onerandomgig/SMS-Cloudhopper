package com.peoplecloud.smpp.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.peoplecloud.smpp.dao.BasicDAO;

@SuppressWarnings("rawtypes")
public class DBPersistanceService {
	private BasicDAO basicDao;

	public BasicDAO getBasicDao() {
		return basicDao;
	}

	public void setBasicDao(BasicDAO basicDao) {
		this.basicDao = basicDao;
	}

	@Transactional
	public void save(Object aObjectToSave) {
		
		basicDao.save(aObjectToSave);
	}

	public List getAll(Class<?> aEntityClass) {
		return basicDao.getAll(aEntityClass);
	}
}
