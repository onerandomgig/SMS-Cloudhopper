package com.peoplecloud.smpp.dao;

import java.util.List;

import com.peoplecloud.smpp.persistable.vo.User;

public class UserDAO extends BasicDAO<User, Long> {
	public UserDAO() {
		initialize(User.class);
	}

	public List<User> listAllUsers() {
		return findAll();
	}

	public User getUserBy(String aUserName, String aPassword) {
		User lUser = new User();
		lUser.setUser(aUserName);
		lUser.setPassword(aPassword);

		List<User> matchedUsers = findByQuery(
				"From User U Where U.user = ? and U.password = ?", aUserName,
				aPassword);
		if (matchedUsers == null || matchedUsers.size() != 1) {
			return null;
		}

		return matchedUsers.get(0);
	}
}