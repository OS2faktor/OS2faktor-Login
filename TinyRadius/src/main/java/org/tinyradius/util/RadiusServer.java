/**
 * $Id: RadiusServer.java,v 1.11 2008/04/24 05:22:50 wuttke Exp $
 * Created on 09.04.2005
 * 
 * @author Matthias Wuttke
 * @version $Revision: 1.11 $
 */
package org.tinyradius.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;

import lombok.extern.slf4j.Slf4j;

/**
 * Implements a simple Radius server. This class must be subclassed to
 * provide an implementation for getSharedSecret() and getUserPassword().
 * If the server supports accounting, it must override
 * accountingRequestReceived().
 */
@Slf4j
public abstract class RadiusServer {
	private InetAddress listenAddress = null;
	private int authPort = 1812;
	private DatagramSocket authSocket = null;
	private DatagramSocket acctSocket = null;
	private int socketTimeout = 3000;
	private HashMap<String, Long> receivedPackets = new HashMap<>();
	private long lastClean;
	private long duplicateInterval = 30000; // 30 s
	protected transient boolean closing = false;

	/**
	 * Define this executor in child class to make packet processing be made in separate threads
	 */
	protected ExecutorService executor = null;

	/**
	 * Constructs an answer for an Access-Request packet. Either this
	 * method or isUserAuthenticated should be overriden.
	 * 
	 * @param accessRequest
	 *            Radius request packet
	 * @param client
	 *            address of Radius client
	 * @return response packet or null if no packet shall be sent
	 * @exception RadiusException
	 *                malformed request packet; if this
	 *                exception is thrown, no answer will be sent
	 */
	public abstract RadiusPacket accessRequestReceived(AccessRequest accessRequest, InetSocketAddress client) throws RadiusException;

	/**
	 * Returns the shared secret used to communicate with the client with the
	 * passed IP address or null if the client is not allowed at this server.
	 * 
	 * @param client
	 *            IP address and port number of client
	 * @return shared secret or null
	 */
	public abstract String getSharedSecret(InetSocketAddress client);


	public RadiusServer(int authPort) {
		this.authPort = authPort;
	}
	
	/**
	 * Returns the shared secret used to communicate with the client with the
	 * passed IP address and the received packet data or null if the client 
	 * is not allowed at this server.
	 *
	 * for compatiblity this standard implementation just call the getSharedSecret(InetSocketAddress) method
	 * and should be overrived when necessary
	 * 
	 * @param client
	 *            IP address and port number of client
	 * @param packet
	 *            packet received from client, the packettype comes as RESERVED, 
	 *	      because for some packets the secret is necessary for decoding
	 * @return shared secret or null
	 */
	public String getSharedSecret(InetSocketAddress client, RadiusPacket packet) {
		return getSharedSecret(client);
	}

	/**
	 * Constructs an answer for an Accounting-Request packet. This method
	 * should be overriden if accounting is supported.
	 * 
	 * @param accountingRequest
	 *            Radius request packet
	 * @param client
	 *            address of Radius client
	 * @return response packet or null if no packet shall be sent
	 * @exception RadiusException
	 *                malformed request packet; if this
	 *                exception is thrown, no answer will be sent
	 */
	public RadiusPacket accountingRequestReceived(AccountingRequest accountingRequest, InetSocketAddress client) throws RadiusException {
		RadiusPacket answer = new RadiusPacket(RadiusPacket.ACCOUNTING_RESPONSE, accountingRequest.getPacketIdentifier());
		copyProxyState(accountingRequest, answer);
		return answer;
	}

	/**
	 * Starts the Radius server.
	 */
	public void start() {
	    Thread.ofPlatform()
	        .name("Radius Auth Listener")
	        .start(() -> {
	            try {
	                log.info("starting RadiusAuthListener on port " + getAuthPort());
	                listenAuth();
	                log.info("RadiusAuthListener is being terminated");
	            }
	            catch (Exception e) {
	                log.error("auth thread stopped by exception", e);
	            }
	            finally {
	                if (authSocket != null) {
	                    authSocket.close();
	                    log.debug("auth socket closed");
	                }
	            }
	        });
	}

	/**
	 * Stops the server and closes the sockets.
	 */
	public void stop() {
		log.info("stopping Radius server");
		closing = true;

		if (executor != null) { 
			executor.shutdown();
		}

		if (authSocket != null) {
			authSocket.close();
		}
		
		if (acctSocket != null) {
			acctSocket.close();
		}
	}

	/**
	 * Returns the auth port the server will listen on.
	 * 
	 * @return auth port
	 */
	public int getAuthPort() {
		return authPort;
	}

	/**
	 * Returns the socket timeout (ms).
	 * 
	 * @return socket timeout
	 */
	public int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * Returns the duplicate interval in ms.
	 * A packet is discarded as a duplicate if in the duplicate interval
	 * there was another packet with the same identifier originating from the
	 * same address.
	 * 
	 * @return duplicate interval (ms)
	 */
	public long getDuplicateInterval() {
		return duplicateInterval;
	}


	/**
	* Returns a map containing received packets
	*
	* @return list of received packets
	*/
	public Map<String, Long> getReceivedPackets() {
		return receivedPackets;
	}

	/**
	 * Returns the IP address the server listens on.
	 * Returns null if listening on the wildcard address.
	 * 
	 * @return listen address or null
	 */
	public InetAddress getListenAddress() {
		return listenAddress;
	}

	/**
	 * Copies all Proxy-State attributes from the request
	 * packet to the response packet.
	 * 
	 * @param request
	 *            request packet
	 * @param answer
	 *            response packet
	 */
	protected void copyProxyState(RadiusPacket request, RadiusPacket answer) {
		List<RadiusAttribute> proxyStateAttrs = request.getAttributes(33);

		for (Iterator<RadiusAttribute> i = proxyStateAttrs.iterator(); i.hasNext();) {
			RadiusAttribute proxyStateAttr = i.next();
			answer.addAttribute(proxyStateAttr);
		}
	}

	/**
	 * Listens on the auth port (blocks the current thread).
	 * Returns when stop() is called.
	 * 
	 * @throws SocketException
	 * @throws InterruptedException
	 * 
	 */
	protected void listenAuth() throws SocketException {
		listen(getAuthSocket());
	}

	/**
	 * Listens on the passed socket, blocks until stop() is called.
	 * 
	 * @param s
	 *            socket to listen on
	 */
	protected void listen(final DatagramSocket s) {
		while (true) {
			try {
				final DatagramPacket packetIn = new DatagramPacket(new byte[RadiusPacket.MAX_PACKET_LENGTH], RadiusPacket.MAX_PACKET_LENGTH);

				// receive packet
				try {
					if (log.isTraceEnabled()) {
						log.trace("about to call socket.receive()");
					}

					s.receive(packetIn);

					if (log.isDebugEnabled()) {
						log.debug("receive buffer size = " + s.getReceiveBufferSize());
					}
				}
				catch (SocketException se) {
					if (closing) {
						// end thread
						log.info("got closing signal - end listen thread");
						return;
					}

					// retry s.receive()
					log.error("SocketException during s.receive() -> retry", se);
					continue;
				}

				if (executor == null) {
					processRequest(s, packetIn);
				}
				else {
					executor.submit(new Runnable() {
						
						@Override
						public void run() {
							processRequest(s, packetIn);
						}
					});
				}
			}
			catch (SocketTimeoutException ste) {
				// this is expected behaviour
				if (log.isTraceEnabled()) {
					log.trace("normal socket timeout");
				}
			}
			catch (IOException ioe) {
				// error while reading/writing socket
				log.error("communication error", ioe);
			}
		}
	}


	/**
	 * Process a single received request
	 * 
	 * @param s
	 *            socket to send response on
	 * @param packetIn
	 *		data packet 
	 */
	protected void processRequest(final DatagramSocket s, final DatagramPacket packetIn) {
		try {
			// check client
			final InetSocketAddress localAddress = (InetSocketAddress) s.getLocalSocketAddress();
			final InetSocketAddress remoteAddress = new InetSocketAddress(packetIn.getAddress(), packetIn.getPort());
			final String secret = getSharedSecret(remoteAddress, makeRadiusPacket(packetIn, "1234567890", RadiusPacket.RESERVED));
			if (secret == null) {
				log.warn("ignoring packet from unknown client " + remoteAddress + " received on local address " + localAddress);
				return;
			}

			// parse packet
			final RadiusPacket request = makeRadiusPacket(packetIn, secret, RadiusPacket.UNDEFINED);
			log.debug("received packet from " + remoteAddress + " on local address " + localAddress);

			// handle packet
			if (log.isTraceEnabled()) {
				log.trace("about to call RadiusServer.handlePacket()");
			}

			final RadiusPacket response = handlePacket(localAddress, remoteAddress, request, secret);

			// send response
			if (response != null) {
				log.info("send response: " + response + " to " + remoteAddress.getAddress() + ":" + packetIn.getPort());

				final DatagramPacket packetOut = makeDatagramPacket(response, secret, remoteAddress.getAddress(), packetIn.getPort(), request);
				s.send(packetOut);
			}
			else {
				log.debug("no response sent");
			}
		}
		catch (IOException ioe) {
			// error while reading/writing socket
			log.error("communication error", ioe);
		}
		catch (RadiusException re) {
			// malformed packet
			log.error("malformed Radius packet", re);
		}
	}

	/**
	 * Handles the received Radius packet and constructs a response.
	 * 
	 * @param localAddress
	 *            local address the packet was received on
	 * @param remoteAddress
	 *            remote address the packet was sent by
	 * @param request
	 *            the packet
	 * @param sharedSecret
	 * @return response packet or null for no response
	 * @throws RadiusException
	 * @throws IOException
	 */
	protected RadiusPacket handlePacket(InetSocketAddress localAddress, InetSocketAddress remoteAddress, RadiusPacket request, String sharedSecret) throws RadiusException, IOException {
		RadiusPacket response = null;

		// check for duplicates
		if (!isPacketDuplicate(request, remoteAddress)) {
			if (localAddress.getPort() == getAuthPort()) {
				// handle packets on auth port
				if (request instanceof AccessRequest) {
					response = accessRequestReceived((AccessRequest) request, remoteAddress);
				}
				else {
					log.error("unknown Radius packet type: " + request.getPacketType());
				}
			}
			else {
				// ignore packet on unknown port
			}
		}
		else {
			log.debug("ignore duplicate packet");
		}

		return response;
	}

	/**
	 * Returns a socket bound to the auth port.
	 * 
	 * @return socket
	 * @throws SocketException
	 */
	protected DatagramSocket getAuthSocket() throws SocketException {
		if (authSocket == null) {
			if (getListenAddress() == null) {
				authSocket = new DatagramSocket(getAuthPort());
			}
			else {
				authSocket = new DatagramSocket(getAuthPort(), getListenAddress());
			}

			authSocket.setSoTimeout(getSocketTimeout());
		}

		return authSocket;
	}

	/**
	 * Creates a Radius response datagram packet from a RadiusPacket to be send.
	 * 
	 * @param packet
	 *            RadiusPacket
	 * @param secret
	 *            shared secret to encode packet
	 * @param address
	 *            where to send the packet
	 * @param port
	 *            destination port
	 * @param request
	 *            request packet
	 * @return new datagram packet
	 * @throws IOException
	 */
	protected DatagramPacket makeDatagramPacket(RadiusPacket packet, String secret, InetAddress address, int port, RadiusPacket request) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		packet.encodeResponsePacket(bos, secret, request);
		byte[] data = bos.toByteArray();

		return new DatagramPacket(data, data.length, address, port);
	}

	/**
	 * Creates a RadiusPacket for a Radius request from a received
	 * datagram packet.
	 * 
	 * @param packet
	 *            received datagram
	 * @return RadiusPacket object
	 * @exception RadiusException
	 *                malformed packet
	 * @exception IOException
	 *                communication error (after getRetryCount()
	 *                retries)
	 */
	protected RadiusPacket makeRadiusPacket(DatagramPacket packet, String sharedSecret, int forceType) throws IOException, RadiusException {
		ByteArrayInputStream in = new ByteArrayInputStream(packet.getData());

		return RadiusPacket.decodeRequestPacket(in, sharedSecret, forceType);
	}

	/**
	 * Checks whether the passed packet is a duplicate.
	 * A packet is duplicate if another packet with the same identifier
	 * has been sent from the same host in the last time.
	 * 
	 * @param packet
	 *            packet in question
	 * @param address
	 *            client address
	 * @return true if it is duplicate
	 */
	protected boolean isPacketDuplicate(RadiusPacket packet, InetSocketAddress address) {
		long now = System.currentTimeMillis();
		long intervalStart = now - getDuplicateInterval();

		String uniqueKey = address.getAddress().getHostAddress()+ 
			packet.getPacketIdentifier() + 
			Arrays.toString(packet.getAuthenticator());

		synchronized (receivedPackets) {
			if (lastClean == 0 || lastClean < now - getDuplicateInterval()) {
				lastClean = now;
				for (Iterator<Map.Entry<String, Long>> i = receivedPackets.entrySet().iterator(); i.hasNext(); ) {
					Long receiveTime = i.next().getValue();

					if (receiveTime < intervalStart) {
						// packet is older than duplicate interval
						i.remove();
					}
				}
			}

			Long receiveTime = receivedPackets.get(uniqueKey);
			if (receiveTime == null) {
				receivedPackets.put(uniqueKey, System.currentTimeMillis());
				return false;
			}
			else {
				return !(receiveTime < intervalStart);
			}
		}
	}
}
