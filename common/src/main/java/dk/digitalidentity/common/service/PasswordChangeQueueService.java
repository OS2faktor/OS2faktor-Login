package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PasswordChangeQueueDao;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordChangeQueueService {
	
	@Autowired
	private ADPasswordService adPasswordService;

	@Autowired
	private PasswordChangeQueueDao passwordChangeQueueDao;

	@Autowired
	private CommonConfiguration commonConfiguration;

	private SecretKeySpec secretKey;

	public PasswordChangeQueue save(PasswordChangeQueue passwordChangeQueue) {
		return save(passwordChangeQueue, true);
	}

	public PasswordChangeQueue save(PasswordChangeQueue passwordChangeQueue, boolean deleteOldEntries) {
		// if the user tries to change password multiple times in a row, we only want to keep the latest - this
		// removes any attempts in the queue that is not already synchronized (which we need to keep for debugging purposes)

		if (deleteOldEntries) {
			List<PasswordChangeQueue> oldQueued = passwordChangeQueueDao.findBySamaccountNameAndDomainAndStatusNot(passwordChangeQueue.getSamaccountName(), passwordChangeQueue.getDomain(), ReplicationStatus.SYNCHRONIZED);
			if (oldQueued != null && oldQueued.size() > 0) {
				passwordChangeQueueDao.deleteAll(oldQueued);
			}
		}

		return passwordChangeQueueDao.save(passwordChangeQueue);
	}

	public void delete(PasswordChangeQueue passwordChangeQueue) {
		passwordChangeQueueDao.delete(passwordChangeQueue);
	}

	public List<PasswordChangeQueue> getAll() {
		return passwordChangeQueueDao.findAll();
	}

	public List<PasswordChangeQueue> getByStatus(ReplicationStatus status) {
		return passwordChangeQueueDao.findByStatus(status);
	}

	public List<PasswordChangeQueue> getUnsynchronized() {
		return passwordChangeQueueDao.findByStatusNotIn(ReplicationStatus.SYNCHRONIZED, ReplicationStatus.FINAL_ERROR, ReplicationStatus.DO_NOT_REPLICATE);
	}

	// only to be used from the UI
	public ADPasswordStatus attemptPasswordChange(Person person, String newPassword) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		PasswordChangeQueue change = new PasswordChangeQueue(person, encryptPassword(newPassword));

		ADPasswordStatus status = adPasswordService.attemptPasswordReplication(change);
		switch (status) {
			// inform user through UI
			case FAILURE:
			case TECHNICAL_ERROR:
				break;

			case NOOP:
				log.error("Got a NOOP case here - that should not happen");
				break;

			// save result - so it is correctly logged to the queue
			case OK:
				save(change);
				break;

			// delay replication in case of a timeout
			case TIMEOUT:
				save(change);
				break;
		}

		return status;
	}

	public PasswordChangeQueue getOldestUnsynchronizedByDomain(String domain) {
		return passwordChangeQueueDao.findFirst1ByDomainAndStatusOrderByTtsAsc(domain, ReplicationStatus.WAITING_FOR_REPLICATION);
	}

	private String encryptPassword(String password) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		SecretKeySpec key = getKey(commonConfiguration.getAd().getPasswordSecret());
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
		cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
		return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes("UTF-8")));
	}

	public String decryptPassword(String encryptedPassword) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		SecretKeySpec key = getKey(commonConfiguration.getAd().getPasswordSecret());
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
		cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
		return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)));
	}

	private SecretKeySpec getKey(String myKey) {
		if (secretKey != null) {
			return secretKey;
		}

		byte[] key;
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		}
		catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			log.error("Error in generating key", e);
		}

		return secretKey;
	}

	public List<PasswordChangeQueue> getByDomain(String domain) {
		return passwordChangeQueueDao.findByDomain(domain);
	}
}
