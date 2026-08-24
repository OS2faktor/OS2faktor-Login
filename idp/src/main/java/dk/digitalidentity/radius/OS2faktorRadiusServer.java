package dk.digitalidentity.radius;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;

import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusServer;

import dk.digitalidentity.common.config.modules.RadiusConfiguration;
import dk.digitalidentity.service.OS2faktorRadiusService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OS2faktorRadiusServer extends RadiusServer {
	private RadiusConfiguration radiusConfiguration;
	private OS2faktorRadiusService os2faktorRadiusService;
	private boolean requireMfa;
	
	public OS2faktorRadiusServer(int authPort, boolean requireMfa, OS2faktorRadiusService os2faktorRadiusService, RadiusConfiguration radiusConfiguration) {
		super(authPort);
		
		this.requireMfa = requireMfa;
		this.os2faktorRadiusService = os2faktorRadiusService;
		this.radiusConfiguration = radiusConfiguration;
		
		if (this.radiusConfiguration.isEnabled()) {
			// configure thread pool
			this.executor =  Executors.newFixedThreadPool(5, Thread.ofPlatform().factory());

			this.start();
		}		
	}

	@Override
	public String getSharedSecret(InetSocketAddress client) {
		return os2faktorRadiusService.getSharedSecret(client);
	}

	@Override
	public RadiusPacket accessRequestReceived(AccessRequest accessRequest, InetSocketAddress client) throws RadiusException {
		return os2faktorRadiusService.accessRequestReceived(requireMfa, accessRequest, client);
	}
	
	@Override
	public RadiusPacket accountingRequestReceived(AccountingRequest accountingRequest, InetSocketAddress client) throws RadiusException {
		throw new UnsupportedOperationException("accounting is not supported!");
	}
	
	/**
	 * Whether an Access-Request from this client must carry a valid Message-Authenticator.
	 *
	 * Note that turning this on will lock out any NAS that does not send the attribute, so it
	 * belongs per client rather than globally. Verification of a Message-Authenticator that
	 * *is* present always happens, regardless of this setting.
	 */
	protected boolean isMessageAuthenticatorRequired(InetSocketAddress client) {
		// TODO: perhaps make it client-specific
		return radiusConfiguration.isUseMessageAuthentication();
	}

	/**
	 * Verifies the Message-Authenticator (RFC 3579, attribute 80) of incoming Access-Requests
	 * before the packet is decoded. Verification runs on the raw octets received, which is the
	 * only place the original wire format is still available.
	 */
	@Override
	protected RadiusPacket makeRadiusPacket(DatagramPacket packet, String sharedSecret, int forceType) throws IOException, RadiusException {
		// RadiusServer.processRequest() calls this method twice: first with a dummy shared
		// secret and forceType == RESERVED, purely to look the client up, and then again with
		// the real secret. Only the second call is able to verify anything.
		if (forceType == RadiusPacket.RESERVED) {
			return super.makeRadiusPacket(packet, sharedSecret, forceType);
		}

		int packetType = packet.getData()[packet.getOffset()] & 0x0ff;

		if (packetType == RadiusPacket.ACCESS_REQUEST) {
			InetSocketAddress client = new InetSocketAddress(packet.getAddress(), packet.getPort());

			try {
				MessageAuthenticator.verifyRequest(packet.getData(), packet.getOffset(), packet.getLength(), sharedSecret, isMessageAuthenticatorRequired(client));
			}
			catch (RadiusException ex) {
				// silently discarded by processRequest(), which is what RFC 3579 asks for
				log.warn("discarding Access-Request from " + client + ": " + ex.getMessage());

				throw ex;
			}
		}

		return super.makeRadiusPacket(packet, sharedSecret, forceType);
	}

	/**
	 * Adds a Message-Authenticator as the first attribute of every response to an
	 * Access-Request, and recomputes the Response Authenticator over it.
	 */
	@Override
	protected DatagramPacket makeDatagramPacket(RadiusPacket packet, String secret, InetAddress address, int port, RadiusPacket request) throws IOException {
		DatagramPacket datagram = super.makeDatagramPacket(packet, secret, address, port, request);

		if (request == null || request.getPacketType() != RadiusPacket.ACCESS_REQUEST) {
			return datagram;
		}

		byte[] encoded = Arrays.copyOfRange(datagram.getData(), datagram.getOffset(), datagram.getOffset() + datagram.getLength());

		try {
			byte[] withMessageAuthenticator = MessageAuthenticator.addToResponse(encoded, request.getAuthenticator(), secret);

			return new DatagramPacket(withMessageAuthenticator, withMessageAuthenticator.length, address, port);
		}
		catch (RadiusException ex) {
			throw new IOException("could not add Message-Authenticator to response for " + address, ex);
		}
	}
}
