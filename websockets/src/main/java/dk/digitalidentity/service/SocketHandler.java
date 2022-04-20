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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.api.dto.PasswordResponse.PasswordStatus;
import dk.digitalidentity.config.Commands;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.model.Request;
import dk.digitalidentity.service.model.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SocketHandler extends TextWebSocketHandler {
	private static SecureRandom random = new SecureRandom();
	private List<Session> sessions = new ArrayList<>();
	private Map<String, RequestHolder> requests = new ConcurrentHashMap<>();
	private Map<String, ResponseHolder> responses = new ConcurrentHashMap<>();

	@Autowired
	private OS2faktorConfiguration configuration;

	@Async
	public AsyncResult<PasswordResponse> validatePassword(String username, String password, String domain) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);
		
		Session session = getSession(domain);
		if (session == null) {
			log.error("Failed to get an authenticated WebSocket connection for validatePassword for domain: " + domain);
			response.setMessage("No authenticated WebSocket connection available");
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
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
			return new AsyncResult<PasswordResponse>(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JsonProcessingException ex) {
			log.error("Cannot serialize validatePassword request", ex);
			
			response.setMessage("Cannot serialize validatePassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}

		TextMessage message = new TextMessage(data);
		try {
			requests.put(request.getTransactionUuid(), new RequestHolder(request));

			session.getSession().sendMessage(message);
		}
		catch (IOException ex) {
			log.error("Failed to send valiatePassword request", ex);
			
			response.setMessage("Failed to send valiatePassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}
		
		// wait for result for 5 seconds (polling every 100 ms)
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= 50);
		
		if (holder == null) {
			log.error("Timeout waiting for response on validatePassword on transactionUuid " + request.getTransactionUuid());
			response.setMessage("Timeout waiting for response");
			response.setStatus(PasswordStatus.TIMEOUT);
			return new AsyncResult<PasswordResponse>(response);
		}

		return new AsyncResult<PasswordResponse>(holder.getResponse());
	}
	
	@Async
	public AsyncResult<PasswordResponse> setPassword(String userId, String password, String domain) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			log.error("Failed to get an authenticated WebSocket connection for setPassword");
			response.setMessage("No authenticated WebSocket connection available");
			return new AsyncResult<PasswordResponse>(response);
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
			return new AsyncResult<PasswordResponse>(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JsonProcessingException ex) {
			response.setMessage("Cannot serialize setPassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}

		TextMessage message = new TextMessage(data);
		try {
			requests.put(request.getTransactionUuid(), new RequestHolder(request));

			session.getSession().sendMessage(message);
		}
		catch (IOException ex) {
			log.error("Failed to send setPassword request", ex);
						
			response.setMessage("Failed to send setPassword request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}
		
		// wait for result for 5 seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= 50);
		
		if (holder == null) {
			log.error("Timeout waiting for response on setPassword with transactionUUid " + request.getTransactionUuid());
			response.setMessage("Timeout waiting for response");
			response.setStatus(PasswordStatus.TIMEOUT);
			return new AsyncResult<PasswordResponse>(response);
		}

		return new AsyncResult<PasswordResponse>(holder.getResponse());
	}
	
	@Async
	public AsyncResult<PasswordResponse> unlockAccount(String userId, String domain) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		Session session = getSession(domain);
		if (session == null) {
			log.error("Failed to get an authenticated WebSocket connection for unlockAccount");
			response.setMessage("No authenticated WebSocket connection available");
			return new AsyncResult<PasswordResponse>(response);
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
			return new AsyncResult<PasswordResponse>(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JsonProcessingException ex) {
			response.setMessage("Cannot serialize unlockAccount request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}

		TextMessage message = new TextMessage(data);
		try {
			requests.put(request.getTransactionUuid(), new RequestHolder(request));

			session.getSession().sendMessage(message);
		}
		catch (IOException ex) {
			log.error("Failed to send unlockAccount request", ex);
						
			response.setMessage("Failed to send unlockAccount request: " + ex.getMessage());
			response.setStatus(PasswordStatus.TECHNICAL_ERROR);
			return new AsyncResult<PasswordResponse>(response);
		}
		
		// wait for result for 5 seconds
		int tries = 0;
		ResponseHolder holder = null;
		do {
			holder = responses.get(request.getTransactionUuid());
			
			if (holder != null) {
				break;
			}
			
			Thread.sleep(100);
			
			tries++;
		} while(tries <= 50);
		
		if (holder == null) {
			log.error("Timeout waiting for response on unlockAccount with transactionUUid " + request.getTransactionUuid());
			response.setMessage("Timeout waiting for response");
			response.setStatus(PasswordStatus.TIMEOUT);
			return new AsyncResult<PasswordResponse>(response);
		}

		return new AsyncResult<PasswordResponse>(holder.getResponse());
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
		catch (JsonProcessingException ex) {
			log.error("Cannot serialize authenticate request", ex);
			return;
		}

		TextMessage message = new TextMessage(data);
		try {
			requests.put(request.getTransactionUuid(), new RequestHolder(request));

			session.getSession().sendMessage(message);
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
			case Commands.UNLOCK_ACCOUNT:
				handlePasswordResponse(message);
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

	private void handlePasswordResponse(Response message) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(("true".equals(message.getStatus())) ? PasswordStatus.OK : PasswordStatus.FAILURE);
		response.setMessage(message.getMessage());

		ResponseHolder holder = new ResponseHolder(response);
		
		responses.put(message.getTransactionUuid(), holder);

		switch (message.getCommand()) {
			case "VALIDATE_PASSWORD":
				log.info("Validate password for " + message.getTarget() + ": " + message.getStatus() + " / " + message.getMessage());
				break;
			case "SET_PASSWORD":
				log.info("Set password for " + message.getTarget() + ": " + message.getStatus());
				break;
			default:
				// ignore
				break;
		}
	}
	
	private void handleAuthenticateResponse(Session session, Response message, Request inResponseTo) {
		if (!message.verify(configuration.getWebSocketKey())) {
			log.error("Got invalid hmac on Authenticate response: " + message + " - sessionID = " + session.getId());

			// we keep the connection open on purpose, otherwise it will just reconnect immediately, spamming us
			return;
		}

		if ("true".equals(message.getStatus())) {
			session.setAuthenticated(true);
			session.setDomain(message.getTarget());

			log.info("Authenticated connection from client (version " + message.getClientVersion() + ") for domain " + message.getTarget() + " - sessionID = " + session.getId() + ", activeSession=" + activeConnections(session.getDomain()));
		}
		else {
			log.warn("Client refused to authenticate (version " + message.getClientVersion() + ") for domain " + message.getTarget() + " - sessionID = " + session.getId() + ", activeSession=" + activeConnections(session.getDomain()));
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
		Session session = new Session(webSocketSession);

		synchronized (sessions) {
			sessions.add(session);			
		}
		
		log.info("Connection established - sending AuthRequest - sessionID=" + session.getId());

		sendAuthenticateRequest(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) throws Exception {
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
				.filter(s -> s.isAuthenticated() && Objects.equals(s.getDomain(), domain))
				.collect(Collectors.toList());
		
		if (matchingSessions.size() == 0) {		
			return null;
		}
		
		return matchingSessions.get(random.nextInt(matchingSessions.size()));
	}

	public List<Session> getSessions() {
		return sessions;
	}
	
	public void cleanupRequestResponse() {
		for (String key : requests.keySet()) {
			RequestHolder holder = requests.get(key);

			if (holder.getTts().isAfter(LocalDateTime.now())) {
				requests.remove(key);
			}
		}
		
		for (String key : responses.keySet()) {
			ResponseHolder holder = responses.get(key);

			if (holder.getTts().isAfter(LocalDateTime.now())) {
				responses.remove(key);
			}
		}
	}
	
	public void closeStaleSessions() {
		for (Session session : sessions) {
			if (session.getCleanupTimestamp().isBefore(LocalDateTime.now())) {
				try {
					log.info("Closing stale connection on websocket client - sessionID = " + session.getId());

					session.getSession().close();
				}
				catch (Exception ex) {
					log.warn("Failed to close connection - sessionID = " + session.getId(), ex);
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
}