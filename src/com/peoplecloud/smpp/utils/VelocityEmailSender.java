package com.peoplecloud.smpp.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.URLDataSource;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.ui.velocity.VelocityEngineUtils;

public class VelocityEmailSender {

	private static final Logger logger = Logger
			.getLogger(VelocityEmailSender.class.getName());

	private VelocityEngine velocityEngine;
	private JavaMailSender mailerService;
	private Properties sysPropsResource;

	private ExecutorService emailSenderThreadPool;

	public VelocityEmailSender() {
		emailSenderThreadPool = Executors.newFixedThreadPool(20);
	}
	
	public VelocityEngine getVelocityEngine() {
		return velocityEngine;
	}

	public JavaMailSender getMailerService() {
		return mailerService;
	}

	public void setVelocityEngine(VelocityEngine aEngine) {
		velocityEngine = aEngine;
	}

	public void setMailerService(JavaMailSender mailerService) {
		this.mailerService = mailerService;
	}

	public Properties getSysPropsResource() {
		return sysPropsResource;
	}

	public void setSysPropsResource(Properties sysPropsResource) {
		this.sysPropsResource = sysPropsResource;
	}

	public synchronized void sendMail(String aTemplate, String aServerName, String aSubj,
			String aError) {
		Map<String, Object> hTemplateVariables = new HashMap<String, Object>();
		hTemplateVariables.put("template", aTemplate);
		hTemplateVariables.put("serverName", aServerName);
		hTemplateVariables.put("error", aError);

		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setTo(sysPropsResource.getProperty("smpp.email.notification.to")
				.split(","));
		msg.setFrom(sysPropsResource
				.getProperty("smpp.email.notification.from"));
		msg.setSubject(aSubj);

		send(msg, hTemplateVariables);
	}

	/**
	 * Sends e-mail using Velocity template for the body and the properties
	 * passed in as Velocity variables.
	 * 
	 * @param msg
	 *            The e-mail message to be sent, except for the body.
	 * @param hTemplateVariables
	 *            Variables to use when processing the template.
	 */
	public void send(final SimpleMailMessage msg,
			final Map<String, Object> hTemplateVariables) {
		emailSenderThreadPool.execute(new Runnable(){
			public void run() {
				MimeMessagePreparator preparator = new MimeMessagePreparator() {
					public void prepare(MimeMessage mimeMessage) throws Exception {
						MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
						message.setTo(msg.getTo());
						message.setFrom(msg.getFrom());
						message.setSubject(msg.getSubject());

						String body = VelocityEngineUtils.mergeTemplateIntoString(
								velocityEngine,
								"/email/" + hTemplateVariables.get("template"),
								"utf-8", hTemplateVariables);

						logger.info("body: " + body);

						message.setText(body, true);
					}
				};

				mailerService.send(preparator);

				logger.info("Sent e-mail to " + msg.getTo());
			}
		});
	}

	public void sendWithAttachment(final Map<String, Object> hTemplateVariables) {
		// Instead of starting a new thread, queue the requests and use one
		// thread to send the mail.
		Thread lMailSenderThread = new Thread(new Runnable() {
			public void run() {

				final Object reportBytes = hTemplateVariables.get("attachment");
				final String aToEmail = hTemplateVariables.get("sendEmailTo")
						.toString();

				try {

					String body = VelocityEngineUtils.mergeTemplateIntoString(
							velocityEngine,
							"email/"
									+ hTemplateVariables.get("template")
											.toString(), "utf-8",
							hTemplateVariables);

					if (reportBytes == null) {
						logger.info("No report received for user with email: "
								+ aToEmail + ". Cannot send report.");
					}

					MimeMessage lMessage = mailerService.createMimeMessage();
					MimeMessageHelper lHelper = new MimeMessageHelper(lMessage,
							true);

					// Prepare a multipart HTML
					Multipart multipart = new MimeMultipart();

					// Prepare the HTML
					BodyPart htmlPart = new MimeBodyPart();
					htmlPart.setContent(body, "text/html");

					// PREPARE THE IMAGE
					BodyPart imgPart = new MimeBodyPart();
					imgPart.setDisposition(BodyPart.INLINE);
					imgPart.setFileName("logo.png");
					String fileName = "email/icept.png";
					ClassLoader classLoader = Thread.currentThread()
							.getContextClassLoader();
					if (classLoader == null) {
						classLoader = this.getClass().getClassLoader();
						if (classLoader == null) {
							System.out.println("IT IS NULL AGAIN!!!!");
						}
					}

					DataSource ds = new URLDataSource(classLoader
							.getResource(fileName));
					imgPart.setDataHandler(new DataHandler(ds));
					imgPart.setHeader("Content-ID",
							hTemplateVariables.get("logoUdid").toString());

					multipart.addBodyPart(imgPart);
					multipart.addBodyPart(htmlPart);
					lHelper.setSubject("iCept Report");

					lMessage.setContent(multipart);
					lHelper.setTo(aToEmail);
					lHelper.setFrom(new javax.mail.internet.InternetAddress(
							"info@visionforlearning.co.nz",
							"info@visionforlearning.co.nz"));
					lHelper.setReplyTo("info@visionforlearning.co.nz");

					if (reportBytes != null) {
						logger.info("Report sent to user with email: "
								+ aToEmail);

						BodyPart lReportPart = new MimeBodyPart();
						lReportPart.setFileName("iCeptReport.pdf");
						lReportPart.setDataHandler(new DataHandler(
								new DataSource() {

									@Override
									public OutputStream getOutputStream()
											throws IOException {
										return null;
									}

									@Override
									public String getName() {
										return "iCept Report";
									}

									@Override
									public InputStream getInputStream()
											throws IOException {
										return new ByteArrayInputStream(
												(byte[]) reportBytes);
									}

									@Override
									public String getContentType() {
										return "application/pdf";
									}
								}));
						multipart.addBodyPart(lReportPart);
					}
					mailerService.send(lMessage);
				} catch (Exception me) {
					logger.error("Failed to send report to user with email: "
							+ aToEmail, me);
				}
			}
		});

		lMailSenderThread.start();

	}
}