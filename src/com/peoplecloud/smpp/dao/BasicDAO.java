package com.peoplecloud.smpp.dao;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.Projections;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;

@Repository
public class BasicDAO<T, ID extends Serializable> {

	// ~ Instance fields
	// --------------------------------------------------------

	private Class<T> persistentClass;

	private EntityManager entityManager;

	// ~ Constructors
	// -----------------------------------------------------------
	public void initialize(final Class<T> persistentClass) {
		this.persistentClass = persistentClass;
	}

	// ~ Methods
	// ----------------------------------------------------------------

	public long countForQuery(String aQuery, Object... aParams) {
		javax.persistence.Query query = getEntityManager().createQuery(aQuery);

		for (int i = 0; i < aParams.length; i++) {
			query.setParameter((i + 1), aParams[i]);
		}

		return (Long) query.getResultList().get(0);
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#countAll()
	 */
	public long countAll() {
		return countByCriteria();
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#countByExample(java.lang.Object)
	 */
	public long countByExample(final T exampleInstance) {
		Session session = getEntityManager().unwrap(Session.class);
		Criteria crit = session.createCriteria(getEntityClass());
		crit.setProjection(Projections.rowCount());
		crit.add(Example.create(exampleInstance));

		return (Long) crit.list().get(0);
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#findAll()
	 */
	public List<T> findAll() {
		return findByQuery("From " + persistentClass.getSimpleName());
	}

	@SuppressWarnings({ "rawtypes" })
	public List findAll(Class aClass) {
		Session session = getEntityManager().unwrap(Session.class);
		Criteria crit = session.createCriteria(aClass);

		return crit.list();
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#findByExample(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	public List<T> findByExample(final T exampleInstance) {
		Session session = getEntityManager().unwrap(Session.class);
		Criteria crit = session.createCriteria(getEntityClass());
		final List<T> result = crit.list();
		return result;
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#findById(java.io.Serializable)
	 */
	public T findById(final ID id) {
		final T result = getEntityManager().find(persistentClass, id);
		return result;
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository
	 *      #findByNamedQuery(java.lang.String, java.lang.Object[])
	 */
	@SuppressWarnings("unchecked")
	public List<T> findByNamedQuery(final String name, Object... params) {
		javax.persistence.Query query = getEntityManager().createNamedQuery(
				name);

		for (int i = 0; i < params.length; i++) {
			query.setParameter(i + 1, params[i]);
		}

		final List<T> result = (List<T>) query.getResultList();
		return result;
	}

	@SuppressWarnings("rawtypes")
	public List findByNativeQuery(final String queryString, Class aMapperClass,
			Object... params) {
		javax.persistence.Query query = getEntityManager().createNativeQuery(
				queryString, aMapperClass);

		for (int i = 0; i < params.length; i++) {
			query.setParameter((i + 1), params[i]);
		}

		return query.getResultList();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List findByQuery(final String queryString, Class aMapperClass,
			Object... params) {
		javax.persistence.Query query = getEntityManager().createQuery(
				queryString, aMapperClass);

		for (int i = 0; i < params.length; i++) {
			query.setParameter((i + 1), params[i]);
		}

		return query.getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<T> findByQuery(final String queryString, Object... params) {
		javax.persistence.Query query = getEntityManager().createQuery(
				queryString);

		for (int i = 0; i < params.length; i++) {
			query.setParameter((i + 1), params[i]);
		}

		return query.getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<T> findByQuery(final int aPageNum, final int aPageSize,
			final String queryString, Object... params) {

		int lPageNum = aPageNum;
		if (lPageNum < 0) {
			lPageNum = 0;
		}

		javax.persistence.Query query = getEntityManager().createQuery(
				queryString);
		query.setMaxResults(aPageSize);
		int lFirstRowNumForPage = ((lPageNum - 1) * aPageSize);
		query.setFirstResult(lFirstRowNumForPage);

		for (int i = 0; i < params.length; i++) {
			query.setParameter((i + 1), params[i]);
		}

		return query.getResultList();
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository
	 *      #findByNamedQueryAndNamedParams(java.lang.String, java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	public List<T> findByNamedQueryAndNamedParams(final String name,
			final Map<String, ? extends Object> params) {
		javax.persistence.Query query = getEntityManager().createNamedQuery(
				name);

		for (final Map.Entry<String, ? extends Object> param : params
				.entrySet()) {
			query.setParameter(param.getKey(), param.getValue());
		}

		final List<T> result = (List<T>) query.getResultList();
		return result;
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#getEntityClass()
	 */

	public Class<T> getEntityClass() {
		return persistentClass;
	}

	/**
	 * set the JPA entity manager to use.
	 * 
	 * @param entityManager
	 */
	@Required
	@PersistenceContext(unitName = "smpp-persistence")
	public void setEntityManager(final EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public EntityManager getEntityManager() {
		return entityManager;
	}

	/**
	 * Use this inside subclasses as a convenience method.
	 */
	protected List<T> findByCriteria(final Criterion... criterion) {
		return findByCriteria(-1, -1, criterion);
	}

	/**
	 * Use this inside subclasses as a convenience method.
	 */
	@SuppressWarnings("unchecked")
	protected List<T> findByCriteria(final int firstResult,
			final int maxResults, final Criterion... criterion) {
		Session session = getEntityManager().unwrap(Session.class);
		Criteria crit = session.createCriteria(getEntityClass());

		for (final Criterion c : criterion) {
			crit.add(c);
		}

		if (firstResult > 0) {
			crit.setFirstResult(firstResult);
		}

		if (maxResults > 0) {
			crit.setMaxResults(maxResults);
		}

		final List<T> result = crit.list();
		return result;
	}

	protected long countByCriteria(Criterion... criterion) {
		Session session = getEntityManager().unwrap(Session.class);
		Criteria crit = session.createCriteria(getEntityClass());
		crit.setProjection(Projections.rowCount());

		for (final Criterion c : criterion) {
			crit.add(c);
		}

		return (Long) crit.list().get(0);
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository#delete(java.lang.Object)
	 */

	public void delete(T entity) {
		getEntityManager().remove(entity);
	}

	/**
	 * @see be.bzbit.framework.domain.repository.GenericRepository
	 *      #save(java.lang.Object)
	 */

	public T save(T entity) {
		final T savedEntity = getEntityManager().merge(entity);
		return savedEntity;
	}

	public void persist(T entity) {
		getEntityManager().persist(entity);
	}

	public void flush() {
		entityManager.flush();
	}

	@SuppressWarnings("unchecked")
	public List<String> exportCSVForQuery(String aQuery, Object[] aParams) {
		javax.persistence.Query query = getEntityManager().createQuery(aQuery);

		for (int i = 0; i < aParams.length; i++) {
			query.setParameter((i + 1), aParams[i]);
		}

		return (List<String>) query.getResultList();
	}
}