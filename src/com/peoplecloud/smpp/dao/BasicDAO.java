package com.peoplecloud.smpp.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

@SuppressWarnings("rawtypes")
public class BasicDAO extends HibernateDaoSupport {

	public void refresh(Object object) {
		getHibernateTemplate().refresh(object);
	}

	public List getAll(Class<?> entityClass) {
		return getHibernateTemplate().loadAll(entityClass);
	}

	public Long save(Object object) {
		Long id = (Long) getHibernateTemplate().save(object);
		return id;
	}

	public void update(Object object) {
		getHibernateTemplate().update(object);
	}

	public void delete(Object object) {
		getHibernateTemplate().delete(object);
	}

	public void saveOrUpdateAll(Collection collection) {
		getHibernateTemplate().saveOrUpdateAll(collection);
	}

	public Object merge(Object object) {
		return getHibernateTemplate().merge(object);
	}
}