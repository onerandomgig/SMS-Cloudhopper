package com.peoplecloud.smpp.api;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.jboss.resteasy.util.HttpResponseCodes;

import com.peoplecloud.smpp.dao.UserDAO;
import com.peoplecloud.smpp.persistable.vo.User;

@Provider
@ServerInterceptor
public class RestSecurityInterceptor implements PreProcessInterceptor {
	private UserDAO userDao;

	private List<User> authenticatedUsers;

	public RestSecurityInterceptor() {
		authenticatedUsers = new ArrayList<User>();
	}

	public UserDAO getUserDao() {
		return userDao;
	}

	public void setUserDao(UserDAO userDao) {
		this.userDao = userDao;
	}

	@Override
	public ServerResponse preProcess(
			org.jboss.resteasy.spi.HttpRequest request, ResourceMethod method)
			throws Failure, WebApplicationException {
		String lUser = request.getHttpHeaders()
				.getRequestHeader("auth-user").get(0);
		String lPassword = request.getHttpHeaders()
				.getRequestHeader("auth-password").get(0);

		User lValidUser = new User();
		lValidUser.setUser(lUser);
		lValidUser.setPassword(lPassword);

		ServerResponse response = new ServerResponse();
		if (authenticatedUsers.contains(lValidUser)) {
			return null;
		} else {
			lValidUser = userDao.getUserBy(lUser, lPassword);

			if (lValidUser == null) {
				response.setStatus(HttpResponseCodes.SC_UNAUTHORIZED);

				MultivaluedMap<String, Object> headers = new Headers<Object>();
				headers.add("Content-Type", "text/plain");
				response.setMetadata(headers);
				response.setEntity("Error 401 Unauthorized: "
						+ request.getPreprocessedPath());
			} else {
				authenticatedUsers.add(lValidUser);
				return null;
			}
		}

		return response;
	}
}
