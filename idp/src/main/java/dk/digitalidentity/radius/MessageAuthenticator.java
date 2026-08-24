package dk.digitalidentity.radius;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusUtil;

/**
 * Byte-level support for the RADIUS Message-Authenticator attribute (type 80),
 * as specified in RFC 2869 / RFC 3579 section 3.2, and required as mitigation for
 * CVE-2024-3596 ("Blast-RADIUS").
 *
 * TinyRadius 1.1.x knows the attribute by name (it is in default_dictionary as
 * "octets") but implements no HMAC at all, so this class operates directly on the
 * encoded packet instead of going through RadiusPacket.
 *
 * Message-Authenticator = HMAC-MD5(Code | Identifier | Length | Request-Authenticator |
 *                                  Attributes, key = shared secret)
 * where the 16 octet value of the Message-Authenticator attribute itself is treated as
 * zeroes while hashing. For responses, the Authenticator field contributes the
 * *request* authenticator, exactly as for the ordinary RFC 2865 Response Authenticator.
 */
public final class MessageAuthenticator {

	/** RADIUS attribute type of Message-Authenticator. */
	public static final int ATTRIBUTE_TYPE = 80;

	/** Encoded size of the attribute: 1 type + 1 length + 16 value. */
	private static final int ATTRIBUTE_LENGTH = 18;

	private static final int HEADER_LENGTH = RadiusPacket.RADIUS_HEADER_LENGTH; // 20
	private static final int AUTHENTICATOR_OFFSET = 4;
	private static final int AUTHENTICATOR_LENGTH = 16;

	private MessageAuthenticator() {
	}

	/**
	 * Verifies the Message-Authenticator of a received request.
	 *
	 * @param data          receive buffer
	 * @param offset        offset of the packet within the buffer
	 * @param length        number of octets received
	 * @param sharedSecret  shared secret of the client the packet came from
	 * @param required      when true, a packet without a Message-Authenticator is rejected
	 * @throws RadiusException if the packet is malformed, or if the Message-Authenticator is
	 *                         wrong, duplicated, or missing while required. The caller is
	 *                         expected to discard the packet without answering it.
	 */
	public static void verifyRequest(byte[] data, int offset, int length, String sharedSecret, boolean required) throws RadiusException {
		if (sharedSecret == null || sharedSecret.isEmpty()) {
			throw new RadiusException("no shared secret has been set");
		}

		byte[] packet = extractPacket(data, offset, length);
		int valueOffset = findAttributeValue(packet);

		if (valueOffset < 0) {
			if (required) {
				throw new RadiusException("packet is missing the required Message-Authenticator attribute");
			}

			return;
		}

		byte[] received = Arrays.copyOfRange(packet, valueOffset, valueOffset + AUTHENTICATOR_LENGTH);

		// "When the message integrity check is calculated the signature string should be
		// considered to be sixteen octets of zero." (RFC 3579)
		Arrays.fill(packet, valueOffset, valueOffset + AUTHENTICATOR_LENGTH, (byte) 0);

		byte[] expected = hmacMd5(sharedSecret, packet);

		if (!MessageDigest.isEqual(received, expected)) {
			throw new RadiusException("Message-Authenticator check failed - wrong shared secret or tampered packet");
		}
	}

	/**
	 * Returns true if the received packet carries a Message-Authenticator attribute. Useful
	 * for logging which clients are already sending it, before switching verification on.
	 */
	public static boolean isPresent(byte[] data, int offset, int length) {
		try {
			return findAttributeValue(extractPacket(data, offset, length)) >= 0;
		}
		catch (RadiusException e) {
			return false;
		}
	}

	/**
	 * Returns a copy of an already encoded response with a Message-Authenticator added as
	 * the <em>first</em> attribute, and with the Response Authenticator recomputed so that
	 * it covers the new attribute.
	 *
	 * Placement matters: a Message-Authenticator appended last does not mitigate
	 * CVE-2024-3596, because an attacker can treat it as an unknown suffix in the same way
	 * as the shared secret. Any Message-Authenticator already present is replaced.
	 *
	 * @param response              encoded response packet
	 * @param requestAuthenticator  16 octet authenticator of the request being answered
	 * @param sharedSecret          shared secret of the client the response is sent to
	 * @return the re-encoded response
	 */
	public static byte[] addToResponse(byte[] response, byte[] requestAuthenticator, String sharedSecret) throws RadiusException {
		if (sharedSecret == null || sharedSecret.isEmpty()) {
			throw new RadiusException("no shared secret has been set");
		}

		if (requestAuthenticator == null || requestAuthenticator.length != AUTHENTICATOR_LENGTH) {
			throw new RadiusException("request authenticator not set");
		}

		byte[] packet = extractPacket(response, 0, response.length);
		byte[] out = new byte[packet.length + ATTRIBUTE_LENGTH];

		// header, then the zeroed placeholder as first attribute
		System.arraycopy(packet, 0, out, 0, HEADER_LENGTH);
		out[HEADER_LENGTH] = (byte) ATTRIBUTE_TYPE;
		out[HEADER_LENGTH + 1] = (byte) ATTRIBUTE_LENGTH;

		// then every original attribute except a Message-Authenticator, if one was there
		int written = HEADER_LENGTH + ATTRIBUTE_LENGTH;
		int read = HEADER_LENGTH;

		while (read < packet.length) {
			int attributeLength = packet[read + 1] & 0x0ff;

			if ((packet[read] & 0x0ff) != ATTRIBUTE_TYPE) {
				System.arraycopy(packet, read, out, written, attributeLength);
				written += attributeLength;
			}

			read += attributeLength;
		}

		byte[] result = (written == out.length) ? out : Arrays.copyOf(out, written);

		if (result.length > RadiusPacket.MAX_PACKET_LENGTH) {
			throw new RadiusException("packet too long");
		}

		result[2] = (byte) (result.length >> 8);
		result[3] = (byte) (result.length & 0x0ff);

		// Both hashes are computed with the request authenticator in the Authenticator field.
		System.arraycopy(requestAuthenticator, 0, result, AUTHENTICATOR_OFFSET, AUTHENTICATOR_LENGTH);

		// 1. Message-Authenticator over the packet as it stands, placeholder still zeroed.
		byte[] messageAuthenticator = hmacMd5(sharedSecret, result);
		System.arraycopy(messageAuthenticator, 0, result, HEADER_LENGTH + 2, AUTHENTICATOR_LENGTH);

		// 2. Response Authenticator afterwards, so that it covers the finished attribute.
		byte[] responseAuthenticator = responseAuthenticator(result, sharedSecret);
		System.arraycopy(responseAuthenticator, 0, result, AUTHENTICATOR_OFFSET, AUTHENTICATOR_LENGTH);

		return result;
	}

	/**
	 * Copies out the packet the Length field claims, and validates the attribute chain.
	 * Octets after Length are ignored, as RFC 2865 requires.
	 */
	private static byte[] extractPacket(byte[] data, int offset, int length) throws RadiusException {
		if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
			throw new RadiusException("invalid receive buffer");
		}

		if (length < HEADER_LENGTH) {
			throw new RadiusException("packet too short, got " + length + " octets");
		}

		int declaredLength = ((data[offset + 2] & 0x0ff) << 8) | (data[offset + 3] & 0x0ff);

		if (declaredLength < HEADER_LENGTH || declaredLength > RadiusPacket.MAX_PACKET_LENGTH) {
			throw new RadiusException("bad packet length field: " + declaredLength);
		}

		if (declaredLength > length) {
			throw new RadiusException("truncated packet, length field says " + declaredLength + " but got " + length + " octets");
		}

		byte[] packet = Arrays.copyOfRange(data, offset, offset + declaredLength);

		// walk the attributes once so that later passes cannot run off the end
		int position = HEADER_LENGTH;

		while (position < packet.length) {
			if (position + 2 > packet.length) {
				throw new RadiusException("malformed packet, truncated attribute header");
			}

			int attributeLength = packet[position + 1] & 0x0ff;

			if (attributeLength < 2 || position + attributeLength > packet.length) {
				throw new RadiusException("malformed packet, bad attribute length " + attributeLength);
			}

			position += attributeLength;
		}

		return packet;
	}

	/**
	 * @return offset of the 16 octet value of the single Message-Authenticator attribute,
	 *         or -1 if the packet has none. Assumes a validated attribute chain.
	 */
	private static int findAttributeValue(byte[] packet) throws RadiusException {
		int found = -1;
		int position = HEADER_LENGTH;

		while (position < packet.length) {
			int attributeLength = packet[position + 1] & 0x0ff;

			if ((packet[position] & 0x0ff) == ATTRIBUTE_TYPE) {
				if (attributeLength != ATTRIBUTE_LENGTH) {
					throw new RadiusException("Message-Authenticator must be 16 octets, attribute length is " + attributeLength);
				}

				if (found >= 0) {
					throw new RadiusException("packet contains more than one Message-Authenticator");
				}

				found = position + 2;
			}

			position += attributeLength;
		}

		return found;
	}

	private static byte[] hmacMd5(String sharedSecret, byte[] packet) throws RadiusException {
		try {
			Mac mac = Mac.getInstance("HmacMD5");
			mac.init(new SecretKeySpec(RadiusUtil.getUtf8Bytes(sharedSecret), "HmacMD5"));

			return mac.doFinal(packet);
		}
		catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
			throw new RadiusException("could not compute HMAC-MD5: " + e.getMessage());
		}
	}

	/**
	 * The plain RFC 2865 Response Authenticator, recomputed here because TinyRadius already
	 * wrote one before we changed the attributes. Expects the request authenticator to be in
	 * the Authenticator field of the passed packet.
	 */
	private static byte[] responseAuthenticator(byte[] packet, String sharedSecret) throws RadiusException {
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(packet);
			md5.update(RadiusUtil.getUtf8Bytes(sharedSecret));

			return md5.digest();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RadiusException("could not compute MD5: " + e.getMessage());
		}
	}
}