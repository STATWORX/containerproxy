/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.stat.impl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Optional;

import eu.openanalytics.containerproxy.security.UserEncrypt;
import eu.openanalytics.containerproxy.event.*;
import org.apache.commons.io.IOUtils;

import eu.openanalytics.containerproxy.stat.IStatCollector;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;


/**
 * E.g.:
 * usage-stats-url: http://localhost:8086/write?db=shinyproxy_usagestats
 */
public class InfluxDBCollector extends AbstractDbCollector {

	

	private String destination;
	private String secretString;

	@PostConstruct
	public void init() {
		destination = environment.getProperty("proxy.usage-stats-url");
		secretString = environment.getProperty("proxy.user-encrypt-key");
	}

	@Inject
	private Environment environment;

	@Override
	protected void writeToDb(long timestamp, String userId, String type, String data) throws IOException {

		String encryptedID = UserEncrypt.obfuscateUser(userId.replace(" ", "\\ "),secretString);
		
		String body = String.format("event,username=%s,type=%s data=\"%s\"",
				encryptedID,
				type.replace(" ", "\\ "),
				Optional.ofNullable(data).orElse(""));

		HttpURLConnection conn = (HttpURLConnection) new URL(destination).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
			dos.write(body.getBytes("UTF-8"));
			dos.flush();
		}
		int responseCode = conn.getResponseCode();
		if (responseCode == 204) {
			// All is well.
		} else {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(conn.getErrorStream(), bos);
			throw new IOException(new String(bos.toByteArray()));
		}
	}


// PoC of extend logging --> put in new class 
// 
// private final Logger logger = LogManager.getLogger(getClass());
// 
// 	@EventListener
// 	public void onProxyStartEvent(ProxyStartEvent event) throws IOException {
// 		logger.warn("ProxyStartEvent [user: {}, startupTime: {}]", event.getUserId(), event.getStartupTime());
// 		String body = String.format("event,username=%s,type=%s data=\"%s\"",
// 		"test",
// 		"test",
// 		"test");

// 		HttpURLConnection conn = (HttpURLConnection) new URL("http://influxdb:8086/write?db=shinyproxy_stats").openConnection();
// 		conn.setRequestMethod("POST");
// 		conn.setDoOutput(true);
// 		try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
// 			dos.write(body.getBytes("UTF-8"));
// 			dos.flush();
// 		}
// 		int responseCode = conn.getResponseCode();
// 		if (responseCode == 204) {
// 			// All is well.
// 		} else {
// 			ByteArrayOutputStream bos = new ByteArrayOutputStream();
// 			IOUtils.copy(conn.getErrorStream(), bos);
// 			throw new IOException(new String(bos.toByteArray()));
// }
// 	}




}


