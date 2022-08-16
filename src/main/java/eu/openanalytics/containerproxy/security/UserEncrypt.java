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

package eu.openanalytics.containerproxy.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import org.springframework.core.env.Environment;

public class UserEncrypt {

  private final static Logger log = LogManager.getLogger(UserEncrypt.class);

  // private String secretString;

  // @PostConstruct
	// public void init() {
	// 	secretString = environment.getProperty("proxy.user-encrypt-key");
	// }

	// @Inject
	// private Environment environment;



  private static SecretKeySpec secretKey;
  private static byte[] key;
		
  public static void setKey(final String myKey) {
    MessageDigest sha = null;
    try {
      key = myKey.getBytes("UTF-8");
      sha = MessageDigest.getInstance("SHA-1");
      key = sha.digest(key);
      key = Arrays.copyOf(key, 16);
      secretKey = new SecretKeySpec(key, "AES");
    } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  public static String encrypt(final String strToEncrypt, final String secret) {
    try {

      setKey(secret);
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey);
      return Base64.getEncoder()
        .encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
    } catch (Exception e) {
      System.out.println("Error while encrypting: " + e.toString());
    }
    return null;
  }

  // static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  // static SecureRandom rnd = new SecureRandom();

  // private static String randomString(int len){
  //   StringBuilder sb = new StringBuilder(len);
  //   for(int i = 0; i < len; i++)
  //       sb.append(AB.charAt(rnd.nextInt(AB.length())));
  //   return sb.toString();
  // }

  public static String obfuscateUser(String userRef, String secretString) {

    if (secretString == null || secretString.isEmpty()) {
      log.warn("No secretKey provided to obfuscate users - return unencrypted");

      return userRef;
      // TODO: only get reandom key once not every call.. 
      // secretString = randomString(16);  
		} 
    String encrypted = UserEncrypt.encrypt(userRef,secretString);
    return Base64.getEncoder().withoutPadding().encodeToString(encrypted.getBytes());
  }

}
