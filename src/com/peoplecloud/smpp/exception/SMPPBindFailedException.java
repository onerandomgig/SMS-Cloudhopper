package com.peoplecloud.smpp.exception;

public class SMPPBindFailedException extends Exception {

	private static final long serialVersionUID = 3597607722965875147L;

	public SMPPBindFailedException(String msg, Exception e) {
		super(msg, e);
	}
}
