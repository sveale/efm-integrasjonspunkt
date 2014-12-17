package no.difi.meldingsutveksling.dokumentpakking.service;

import no.difi.meldingsutveksling.dokumentpakking.domain.EncryptedContent;
import no.difi.meldingsutveksling.domain.Mottaker;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


public class EncryptPayload {
	//public static final String RSA_MODE = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";
	public static final String RSA_MODE = "RSA";
	public static final String AES_MODE = "AES"; 
	public static final int AES_LENGTH = 128; 
	
	
	
	public EncryptedContent encrypt(byte[] payload, Mottaker mottaker) {
		try {
			Cipher keyCipher = Cipher.getInstance(RSA_MODE);
			Cipher contentCipher = Cipher.getInstance(AES_MODE);
			Key contentKey = generateKey();
			
			keyCipher.init(Cipher.ENCRYPT_MODE, mottaker.getPublicKey());
			contentCipher.init(Cipher.ENCRYPT_MODE, contentKey);
			
			byte[] encryptedContentKey = keyCipher.doFinal(contentKey.getEncoded());
			byte[] encryptedContent = contentCipher.doFinal(payload);
			
			return new EncryptedContent(encryptedContentKey, encryptedContent);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private Key generateKey() throws NoSuchAlgorithmException{
	      KeyGenerator kg = KeyGenerator.getInstance(AES_MODE);
	      SecureRandom random = new SecureRandom();
	      kg.init(AES_LENGTH, random);
	      return kg.generateKey();
	}
}