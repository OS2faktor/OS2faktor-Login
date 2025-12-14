package dk.digitalidentity.radius;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusServer;

import dk.digitalidentity.common.config.modules.RadiusConfiguration;
import dk.digitalidentity.service.OS2faktorRadiusService;

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
}
