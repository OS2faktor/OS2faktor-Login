package dk.digitalidentity.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.api.dto.PasswordResponse.PasswordStatus;
import dk.digitalidentity.config.Commands;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.model.Request;
import dk.digitalidentity.service.model.Response;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class SocketHandler extends TextWebSocketHandler {
	private static SecureRandom random = new SecureRandom();
	private List<Session> sessions = new ArrayList<>();
	private Map<String, RequestHolder> requests = new ConcurrentHashMap<>();
	private Map<String, ResponseHolder> responses = new ConcurrentHashMap<>();
	private int timeoutCounter = 0; // not thread-safe, but also not critical that it is
	private Map<String, LocalDateTime> ttsFirstNoConnectionMap = new ConcurrentHashMap<>();
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Async
	public CompletableFuture<PasswordResponse> validatePassword(String username, String password, String domain, boolean retry) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);
		
		Session session = getSession(domain);
		if (session == null) {
			logNoAuthenticatedWebsocket("Failed to get an authenticated WebSocket connection for validatePassword for domain: " + domain, domain);
			response.setMessage("No authenticated WebSocket connection available");
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.VALIDATE_PASSWORD);
		request.setTarget(username);
		request.setPayload(password);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign validatePassword message", ex);
			response.setMessage("Failed to sign validatePassword message: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			
			return CompletableFuture.completedFuture(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			log.error("Cannot serialize validatePassword request", ex);
			
			response.setMessage("Cannot serialize validatePassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		long startTts = System.currentTimeMillis();
		
		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send valiatePassword request", ex);
			
			response.setMessage("Failed to send valiatePassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		// wait for result for X seconds, default 4 (polling every 100 ms)
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= configuration.getMaxWait());
		
		if (holder == null) {
			if (timeoutCounter >= 4) {
				log.error("Timeout waiting for response on validatePassword on transactionUuid " + request.getTransactionUuid());
			}
			else {
				log.warn("Timeout waiting for response on validatePassword on transactionUuid " + request.getTransactionUuid());
			}
			
			// flag session as bad, so it will be closed
			session.setBadState(true);
			
			if (!retry) {
				response.setMessage("Timeout waiting for response");
				response.setStatus(PasswordStatus.TIMEOUT);
				timeoutCounter++;

				return CompletableFuture.completedFuture(response);
			}
			else {
				// try once more, against a new connection
				return validatePassword(username, password, domain, false);
			}
		}
		
		timeoutCounter = 0;

		session.logRequest(Commands.VALIDATE_PASSWORD, System.currentTimeMillis() - startTts);
		
		return CompletableFuture.completedFuture(holder.getResponse());
	}

	private void logNoAuthenticatedWebsocket(String message, String domain) {
		LocalDateTime ttsFirstNoConnection = ttsFirstNoConnectionMap.get(domain);

		if (ttsFirstNoConnection != null) {
			// once it has been 15 minutes without a connection, start logging errors
			if (ttsFirstNoConnection.isBefore(LocalDateTime.now().minusMinutes(15))) {
				log.error(message);
			}
			else {
				log.warn(message);
			}
		}
		else {
			ttsFirstNoConnection = LocalDateTime.now();
			ttsFirstNoConnectionMap.put(domain, ttsFirstNoConnection);

			log.warn(message);
		}
	}

	@Async
	public CompletableFuture<PasswordResponse> setPasswordWithForcedChange(String userId, String password, String domain, boolean retry) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			logNoAuthenticatedWebsocket("Failed to get an authenticated WebSocket connection for setPasswordWithForcedChange for " + domain, domain);
			response.setMessage("No authenticated WebSocket connection available");

			return CompletableFuture.completedFuture(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.SET_PASSWORD_WITH_FORCED_CHANGE);
		request.setTarget(userId);
		request.setPayload(password);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign setPasswordWithForcedChange message", ex);
			response.setMessage("Failed to sign setPasswordWithForcedChange message: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			response.setMessage("Cannot serialize setPasswordWithForcedChange request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		long startTts = System.currentTimeMillis();

		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send setPasswordWithForcedChange request", ex);
						
			response.setMessage("Failed to send setPasswordWithForcedChange request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		// wait for result for X seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= configuration.getMaxWait());
	
		if (holder == null) {
			if (timeoutCounter >= 4) {
				log.error("Timeout waiting for response on setPasswordWithForcedChange with transactionUUid " + request.getTransactionUuid());
			}
			else {
				log.warn("Timeout waiting for response on setPasswordWithForcedChange with transactionUUid " + request.getTransactionUuid());
			}
			
			// flag session as bad, so it will be closed
			session.setBadState(true);
			
			if (!retry) {
				response.setMessage("Timeout waiting for response");
				response.setStatus(PasswordStatus.TIMEOUT);
				timeoutCounter++;

				return CompletableFuture.completedFuture(response);
			}
			else {
				// try once more, against a new connection
				return setPasswordWithForcedChange(userId, password, domain, false);
			}
		}
		
		timeoutCounter = 0;

		session.logRequest(Commands.SET_PASSWORD_WITH_FORCED_CHANGE, System.currentTimeMillis() - startTts);

		return CompletableFuture.completedFuture(holder.getResponse());
	}
	
	@Async
	public CompletableFuture<PasswordResponse> setPassword(String userId, String password, String domain, boolean retry) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			logNoAuthenticatedWebsocket("Failed to get an authenticated WebSocket connection for setPassword for " + domain, domain);
			response.setMessage("No authenticated WebSocket connection available");

			return CompletableFuture.completedFuture(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.SET_PASSWORD);
		request.setTarget(userId);
		request.setPayload(password);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign setPassword message", ex);
			response.setMessage("Failed to sign setPassword message: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			response.setMessage("Cannot serialize setPassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		long startTts = System.currentTimeMillis();

		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send setPassword request", ex);
						
			response.setMessage("Failed to send setPassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		// wait for result for X seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= configuration.getMaxWait());
		
		if (holder == null) {
			if (timeoutCounter >= 4) {
				log.error("Timeout waiting for response on setPassword with transactionUUid " + request.getTransactionUuid());
			}
			else {
				log.warn("Timeout waiting for response on setPassword with transactionUUid " + request.getTransactionUuid());
			}
			
			// flag session as bad, so it will be closed
			session.setBadState(true);

			if (!retry) {
				response.setMessage("Timeout waiting for response");
				response.setStatus(PasswordStatus.TIMEOUT);
				timeoutCounter++;

				return CompletableFuture.completedFuture(response);
			}
			else {
				// try once more, against a new connection
				return setPassword(userId, password, domain, false);
			}
		}
		
		timeoutCounter = 0;

		session.logRequest(Commands.SET_PASSWORD, System.currentTimeMillis() - startTts);

		return CompletableFuture.completedFuture(holder.getResponse());
	}
	
	@Async
	public CompletableFuture<PasswordResponse> unlockAccount(String userId, String domain, boolean retry) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			logNoAuthenticatedWebsocket("Failed to get an authenticated WebSocket connection for unlockAccount for " + domain, domain);
			response.setMessage("No authenticated WebSocket connection available");

			return CompletableFuture.completedFuture(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.UNLOCK_ACCOUNT);
		request.setTarget(userId);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign unlockAccount message", ex);
			response.setMessage("Failed to sign unlockAccount message: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			response.setMessage("Cannot serialize unlockAccount request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		long startTts = System.currentTimeMillis();

		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send unlockAccount request", ex);
						
			response.setMessage("Failed to send unlockAccount request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}
		
		// wait for result for X seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= configuration.getMaxWait());
		
		if (holder == null) {
			if (timeoutCounter >= 4) {
				log.error("Timeout waiting for response on unlockAccount with transactionUUid " + request.getTransactionUuid());
			}
			else {
				log.warn("Timeout waiting for response on unlockAccount with transactionUUid " + request.getTransactionUuid());
			}
			
			// flag session as bad, so it will be closed
			session.setBadState(true);
			
			if (!retry) {
				response.setMessage("Timeout waiting for response");
				response.setStatus(PasswordStatus.TIMEOUT);
				timeoutCounter++;

				return CompletableFuture.completedFuture(response);
			}
			else {
				// try once more, against a new connection
				return unlockAccount(userId, domain, false);
			}
		}
		
		timeoutCounter = 0;

		session.logRequest(Commands.UNLOCK_ACCOUNT, System.currentTimeMillis() - startTts);

		return CompletableFuture.completedFuture(holder.getResponse());
	}

	@Async
	public CompletableFuture<PasswordResponse> passwordExpires(String userId, String domain, boolean retry) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			logNoAuthenticatedWebsocket("Failed to get an authenticated WebSocket connection for passwordExpires for " + domain, domain);
			response.setMessage("No authenticated WebSocket connection available");

			return CompletableFuture.completedFuture(response);
		}

		// if not running at least version 2.1.0, we will just return OK and do nothing
		if (!verifyVersion(session, 2, 1)) {
			response.setStatus(PasswordStatus.OK);

			return CompletableFuture.completedFuture(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.PASSWORD_EXPIRES_SOON);
		request.setTarget(userId);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign passwordExpires message", ex);
			response.setMessage("Failed to sign passwordExpires message: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			response.setMessage("Cannot serialize passwordExpires request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);

			return CompletableFuture.completedFuture(response);
		}

		long startTts = System.currentTimeMillis();

		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send passwordExpires request", ex);

			response.setMessage("Failed to send passwordExpires request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			
			return CompletableFuture.completedFuture(response);
		}

		// wait for result for X seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());

			if (holder != null) {
				break;
			}

			Thread.sleep(100);

			tries++;
		} while(tries <= configuration.getMaxWait());

		if (holder == null) {
			if (timeoutCounter >= 4) {
				log.error("Timeout waiting for response on passwordExpires with transactionUUid " + request.getTransactionUuid());
			}
			else {
				log.warn("Timeout waiting for response on passwordExpires with transactionUUid " + request.getTransactionUuid());
			}

			// flag session as bad, so it will be closed
			session.setBadState(true);

			if (!retry) {
				response.setMessage("Timeout waiting for response");
				response.setStatus(PasswordStatus.TIMEOUT);
				timeoutCounter++;

				return CompletableFuture.completedFuture(response);
			}
			else {
				// try once more, against a new connection
				return passwordExpires(userId, domain, false);
			}
		}

		timeoutCounter = 0;

		session.logRequest(Commands.PASSWORD_EXPIRES_SOON, System.currentTimeMillis() - startTts);

		return CompletableFuture.completedFuture(holder.getResponse());
	}
	
	private void sendAuthenticateRequest(Session session) {
		Request request = new Request();
		request.setCommand(Commands.AUTHENTICATE);
		
		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign authenticate message", ex);
			return;
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JacksonException ex) {
			log.error("Cannot serialize authenticate request", ex);
			return;
		}

		TextMessage message = new TextMessage(data);
		try {
			putRequest(request.getTransactionUuid(), new RequestHolder(request));

			sendMessage(session, message);
		}
		catch (IOException ex) {
			log.error("Failed to send Authenticate request", ex);
		}
	}

	// Handle the response from the client
	@Override
	public void handleTextMessage(WebSocketSession webSocketSession, TextMessage textMessage) throws InterruptedException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		Response message = mapper.readValue(textMessage.getPayload(), Response.class);

		RequestHolder holder = requests.get(message.getTransactionUuid());
		if (holder == null) {
			log.error("Got response for unknown request: " + message);
			return;
		}

		// dealt with, so remove from queue
		requests.remove(message.getTransactionUuid());
		
		Request inResponseTo = holder.getRequest();
		if (!inResponseTo.validateEcho(message)) {
			log.error("Response does not echo correctly! request=" + inResponseTo + ", response=" + message);
			invalidateRequest(message.getTransactionUuid(), "Response did not correctly echo request");
			return;
		}

		if (!message.verify(configuration.getWebSocketKey())) {
			log.error("Got invalid hmac on Authenticate response: " + message);
			invalidateRequest(message.getTransactionUuid(), "Got invalid hmac on response");
			return;
		}

		switch (message.getCommand()) {
			case Commands.IS_ALIVE:
				handleIsAliveResponse(message);
				break;
			case Commands.AUTHENTICATE:
				Optional<Session> clientSession = sessions.stream().filter(cs -> cs.getSession().equals(webSocketSession)).findAny();
				if (clientSession.isPresent()) {
					handleAuthenticateResponse(clientSession.get(), message, holder.getRequest());
				}
				else {
					log.error("We got a message from an unknown websocket client: " + message);
				}
				break;
			case Commands.VALIDATE_PASSWORD:
			case Commands.SET_PASSWORD:
			case Commands.SET_PASSWORD_WITH_FORCED_CHANGE:
				handlePasswordResponse(message);
				break;
			case Commands.UNLOCK_ACCOUNT:
				handleUnlockResponse(message);
				break;
			case Commands.PASSWORD_EXPIRES_SOON:
				handlePasswordExpiresResponse(message);
				break;
			default:
				log.error("Unknown command for message: " + message);
				break;
		}
	}

	private void invalidateRequest(String transactionUuid, String message) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.TECHNICAL_ERROR);
		response.setMessage(message);

		ResponseHolder holder = new ResponseHolder(response);

		responses.put(transactionUuid, holder);
	}

	private void handleIsAliveResponse(Response message) {		
		responses.put(message.getTransactionUuid(), new ResponseHolder(null));
	}

	private void handleUnlockResponse(Response message) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(("true".equals(message.getStatus())) ? PasswordStatus.OK : PasswordStatus.FAILURE);
		response.setMessage(message.getMessage());

		ResponseHolder holder = new ResponseHolder(response);
		
		responses.put(message.getTransactionUuid(), holder);

		log.info("Unlock account for " + message.getTarget() + ": " + message.getStatus() + " / " + message.getServerName() + " / " + message.getMessage());
	}
	
	private void handlePasswordResponse(Response message) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(("true".equals(message.getStatus())) ? PasswordStatus.OK : PasswordStatus.FAILURE);
		response.setMessage(message.getMessage());

		ResponseHolder holder = new ResponseHolder(response);
		
		responses.put(message.getTransactionUuid(), holder);

		switch (message.getCommand()) {
			case "VALIDATE_PASSWORD":
				log.info("Validate password for " + message.getTarget() + ": " + message.getStatus() + " / " + message.getServerName() + " / " + message.getMessage());
				break;
			case "SET_PASSWORD":
			case "SET_PASSWORD_WITH_FORCED_CHANGE":
				log.info("Set password for " + message.getTarget() + ": " + message.getStatus() + " / " + message.getServerName() + " / " + message.getMessage());
				break;
			default:
				// ignore
				break;
		}
	}

	private void handlePasswordExpiresResponse(Response message) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(("true".equals(message.getStatus())) ? PasswordStatus.OK : PasswordStatus.FAILURE);
		response.setMessage(message.getMessage());

		ResponseHolder holder = new ResponseHolder(response);

		responses.put(message.getTransactionUuid(), holder);

		log.info("PasswordExpires for " + message.getTarget() + ": " + message.getStatus() + " / " + message.getServerName() + " / " + message.getMessage());
	}
	
	// synchronize all sends to avoid [TEXT_PARTIAL_WRITING]
	private synchronized void sendMessage(Session session, TextMessage message) throws IOException {
		session.getSession().sendMessage(message);
	}
	
	private void handleAuthenticateResponse(Session session, Response message, Request inResponseTo) {
		if (!message.verify(configuration.getWebSocketKey())) {
			log.error("Got invalid hmac on Authenticate response: " + message + " - sessionID = " + session.getIdentifier());

			// we keep the connection open on purpose, otherwise it will just reconnect immediately, spamming us
			return;
		}

		if ("true".equals(message.getStatus())) {
			session.setAuthenticated(true);
			session.setDomain(message.getTarget());
			session.setVersion(message.getClientVersion());

			log.info("Authenticated connection from client (version=" + message.getClientVersion() + ", server=" + message.getServerName() + ") for domain " + message.getTarget() + " - sessionID = " + session.getId() + ", activeSession=" + activeConnections(session.getDomain()));
		}
		else {
			log.warn("Client refused to authenticate (version " + message.getClientVersion() + ", server=" + message.getServerName() + ") for domain " + message.getTarget() + " - sessionID = " + session.getId() + ", activeSession=" + activeConnections(session.getDomain()));
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
		Session session = new Session(webSocketSession);

		synchronized (sessions) {
			sessions.add(session);			
		}
		
		log.info("Connection established - sending AuthRequest - sessionID=" + session.getIdentifier());

		sendAuthenticateRequest(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) throws Exception {
		
		// log what we are closing
		Session closingSession = sessions.stream().filter(s -> s.getSession().equals(webSocketSession)).findFirst().orElse(null);
		if (closingSession != null) {
			log.info("Closing " + closingSession.toString());
		}
		
		synchronized (sessions) {
			sessions.removeIf(s -> s.getSession().equals(webSocketSession));
		}
		
		Map<String, Long> sessionCount = new HashMap<>();
		for (Session session : sessions) {
			Long count = sessionCount.get(session.getDomain());
			if (count == null) {
				count = 0L;
			}

			sessionCount.put(session.getDomain(), ++count);
		}
		
		log.info("After closing connection: " + sessionCount.toString());
	}
	
	private Session getSession(String domain) {
		List<Session> matchingSessions = sessions.stream()
				.filter(s -> s.isAuthenticated() && !s.isStale() && Objects.equals(s.getDomain(), domain))
				.collect(Collectors.toList());

		if (matchingSessions.size() == 0) {
			return null;
		}
		
		// we have a connection, so clear timestamp since last no connection on this domain
		try {
			ttsFirstNoConnectionMap.remove(domain);
		}
		catch (Exception ignored) {
			;
		}
		
		return matchingSessions.get(random.nextInt(matchingSessions.size()));
	}

	public List<Session> getSessions() {
		return sessions;
	}
	
	public void cleanupRequestResponse() {
		for (String key : requests.keySet()) {
			RequestHolder holder = requests.get(key);

			if (holder.getTts().isBefore(LocalDateTime.now())) {
				log.info("Removing request which we never got a response for with uuid " + holder.getRequest().getTransactionUuid());
				requests.remove(key);
			}
		}
		
		for (String key : responses.keySet()) {
			ResponseHolder holder = responses.get(key);

			if (holder.getTts().isBefore(LocalDateTime.now())) {
				responses.remove(key);
			}
		}
	}
	
	public void sendIsAlive() {
		// make a copy to avoid ConcurrentModificationException
		List<Session> tmp = new ArrayList<>(sessions);
		
		for (Session session : tmp) {
			if (!session.supportsIsAliveCheck()) {
				continue;
			}
			
			Request request = new Request();
			request.setCommand(Commands.IS_ALIVE);

			try {
				request.sign(configuration.getWebSocketKey());
			}
			catch (Exception ex) {
				log.error("Failed to sign isAlive message", ex);
				continue;
			}

			String data = null;
			try {
				ObjectMapper mapper = new ObjectMapper();

				data = mapper.writeValueAsString(request);
			}
			catch (JacksonException ex) {
				log.error("JsonProcessing issue on isAlive message", ex);
				continue;
			}

			TextMessage message = new TextMessage(data);
			try {
				putRequest(request.getTransactionUuid(), new RequestHolder(request));

				sendMessage(session, message);
			}
			catch (IllegalStateException ex) {
				try {
					log.warn("IllegalStateException on attempting to send IsAlive to " + session.getIdentifier() + ", message: " + ex.getMessage());

					session.getSession().close();
				}
				catch (Exception ex2) {
					log.warn("Failed to close connection - sessionID = " + session.getIdentifier(), ex2);
				}

				continue;
			}
			catch (IOException ex) {
				try {
					log.error("Closing connection on websocket client due to technical error. " + session.toString(), ex);

					session.getSession().close();
				}
				catch (Exception ex2) {
					log.warn("Failed to close connection - sessionID = " + session.getIdentifier(), ex2);
				}

				continue;
			}
			
			// wait for result for 3 seconds
			int tries = 0;
			ResponseHolder holder = null;
			do {
				holder = responses.get(request.getTransactionUuid());
				
				if (holder != null) {
					break;
				}
				
				try {
					Thread.sleep(100);
				}
				catch (Exception ignored) {
					;
				}
				
				tries++;
			} while(tries <= 30);

			if (holder == null) {
				try {
					log.info("Closing connection on websocket client due to timeout. " + session.toString());

					session.getSession().close();
				}
				catch (Exception ex) {
					log.warn("Failed to close connection - sessionID = " + session.getIdentifier(), ex);
				}

				continue;
			}
		}
	}

	public void closeStaleSessions() {
		for (Session session : sessions) {
			if (session.isStale()) {
				try {
					log.info("Closing stale connection on websocket client. " + session.toString());

					session.getSession().close();
				}
				catch (Exception ex) {
					log.warn("Failed to close connection - sessionID = " + session.getIdentifier(), ex);
				}
				
				// only close a single state connection per loop - do not want to end up with 0 connections during a single cleanup run
				break;
			}
		}
	}
	
	private int activeConnections(String domain) {
		if (domain != null) {
			return (int) sessions.stream().filter(s -> Objects.equals(domain, s.getDomain())).count();
		}
		
		return sessions.size();
	}

	private void putRequest(String transactionUuid, RequestHolder requestHolder) {
		if (!"IS_ALIVE".equals(requestHolder.getRequest().getCommand())) {
			log.info("Sending request " + transactionUuid + " for " + requestHolder.getRequest().getCommand() + " on " + requestHolder.getRequest().getTarget());
		}
		
		requests.put(transactionUuid, requestHolder);
	}

	private boolean verifyVersion(Session session, int major, int minor) {
		if (session == null || !StringUtils.hasLength(session.getVersion())) {
			return false;
		}
		
		String tokens[] = session.getVersion().split("\\.");
		if (tokens.length < 2) {
			log.warn("Invalid client version: " + session.getVersion());
			return false;
		}
		
		try {
			int clientMajor = Integer.parseInt(tokens[0]);
			int clientMinor= Integer.parseInt(tokens[1]);
			
			if (clientMajor > major || (clientMajor == major && clientMinor >= minor)) {
				return true;
			}
		}
		catch (Exception ex) {
			log.error("Parse error on version: " + session.getVersion(), ex);
		}
		
		return false;
	}

}