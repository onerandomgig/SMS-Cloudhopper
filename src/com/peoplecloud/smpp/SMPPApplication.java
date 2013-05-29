package com.peoplecloud.smpp;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.eclipse.jetty.server.Server;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.peoplecloud.smpp.api.RestSecurityInterceptor;
import com.peoplecloud.smpp.api.SMSRestAPI;

public class SMPPApplication extends Application {
	private static Set<Object> services = new HashSet<Object>();

	private ClassPathXmlApplicationContext mAppContext;

	public void start() {
		mAppContext = new ClassPathXmlApplicationContext(
				"applicationContext.xml");

		// initialize restful services
		services.add((SMSRestAPI) mAppContext.getBean("smsRestAPIService"));
		services.add((RestSecurityInterceptor) mAppContext
				.getBean("securityInterceptor"));

		try {
			Server lWebServer = (Server) mAppContext.getBean("Main");
			lWebServer.start();
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public Set<Object> getSingletons() {
		return services;
	}

	public void shutdown() {
		mAppContext.close();
	}

	public static void main(String[] args) {
		SMPPApplication lApp = new SMPPApplication();
		lApp.start();
	}
}