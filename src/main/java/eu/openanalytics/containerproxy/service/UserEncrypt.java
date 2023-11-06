package eu.openanalytics.containerproxy.service;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class UserEncrypt {
   private static final Logger log = LogManager.getLogger(UserEncrypt.class);
   private static SecretKeySpec secretKey;
   private static byte[] key;
   private static String secretString;

   @Inject
	private Environment environment;

   @PostConstruct
	public void init() {
      secretString = environment.getProperty("proxy.user-encrypt-key");
   }
   
   
   public static void setKey(final String myKey) {
      MessageDigest sha = null;

      try {
         key = myKey.getBytes("UTF-8");
         sha = MessageDigest.getInstance("SHA-1");
         key = sha.digest(key);
         key = Arrays.copyOf(key, 16);
         secretKey = new SecretKeySpec(key, "AES");
      } catch (UnsupportedEncodingException | NoSuchAlgorithmException var3) {
         var3.printStackTrace();
      }

   }

   public static String encrypt(final String strToEncrypt, final String secret) {
      try {
         setKey(secret);
         Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
         cipher.init(1, secretKey);
         return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
      } catch (Exception var3) {
         System.out.println("Error while encrypting: " + var3.toString());
         return null;
      }
   }

   public static String obfuscateUser(String userRef) {

      if (secretString != null && !secretString.isEmpty()) {
         
         String encrypted = encrypt(userRef, secretString);
         return Base64.getEncoder().withoutPadding().encodeToString(encrypted.getBytes());
      } else {
         log.warn("No secretKey provided to obfuscate users - return unencrypted");
         return userRef;
      }
   }
}
