package dk.digitalidentity.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
		response.setValid(false);
		
		Session session = getSession(domain);
		if (session == null) {
			log.error("Failed to get an authenticated WebSocket connection");
			response.setMessage("No authenticated WebSocket connection available");
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
			log.error("Timeout waiting for response on validatePassword!");
			response.setMessage("Timeout waiting for response");
			return new AsyncResult<PasswordResponse>(response);
		}

		return new AsyncResult<PasswordResponse>(holder.getResponse());
	}
	
	@Async
	public AsyncResult<PasswordResponse> setPassword(String username, String password, String domain) throws InterruptedException {
		PasswordResponse response = new PasswordResponse();
		response.setValid(false);

		Session session = getSession(domain);
		if (session == null) {
			log.error("Failed to get an authenticated WebSocket connection");
			response.setMessage("No authenticated WebSocket connection available");
			return new AsyncResult<PasswordResponse>(response);
		}
		
		Request request = new Request();
		request.setCommand(Commands.SET_PASSWORD);
		request.setTarget(username);
		request.setPayload(password);

		try {
			request.sign(configuration.getWebSocketKey());
		}
		catch (Exception ex) {
			log.error("Failed to sign setPassword message", ex);
			response.setMessage("Failed to sign setPassword message: " + ex.getMessage());
			return new AsyncResult<PasswordResponse>(response);
		}

		String data = null;
		try {
			ObjectMapper mapper = new ObjectMapper();

			data = mapper.writeValueAsString(request);
		}
		catch (JsonProcessingException ex) {
			response.setMessage("Cannot serialize setPassword request: " + ex.getMessage());
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
			log.error("Timeout waiting for response on setPassword!");
			response.setMessage("Timeout waiting for response");
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
				handlePasswordResponse(message);
				break;
			default:
				log.error("Unknown command for message: " + message);
				break;
		}
	}

	private void invalidateRequest(String transactionUuid, String message) {
		PasswordResponse response = new PasswordResponse();
		response.setValid(false);
		response.setMessage(message);

		ResponseHolder holder = new ResponseHolder(response);

		responses.put(transactionUuid, holder);
	}

	private void handlePasswordResponse(Response message) {
		PasswordResponse response = new PasswordResponse();
		response.setValid("true".equals(message.getStatus()));

		ResponseHolder holder = new ResponseHolder(response);
		
		responses.put(message.getTransactionUuid(), holder);

		switch (message.getCommand()) {
			case "VALIDATE_PASSWORD":
				log.info("Valiate password for " + message.getTarget() + ": " + message.getStatus());
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
			log.error("Got invalid hmac on Authenticate response: " + message);
			
			// we keep the connection open on purpose, otherwise it will just reconnect immediately, spamming us
			return;
		}

		if ("true".equals(message.getStatus())) {
			session.setAuthenticated(true);
			session.setDomain(message.getTarget());

			log.info("Authenticated connection from client (version " + message.getClientVersion() + ")");
		}
		else {
			log.warn("Client refused to authenticate (version " + message.getClientVersion() + ")");
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
		Session session = new Session(webSocketSession);

		synchronized (sessions) {
			sessions.add(session);			
		}
	
		sendAuthenticateRequest(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) throws Exception {
		synchronized (sessions) {
			sessions.removeIf(s -> s.getSession().equals(webSocketSession));			
		}
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
		Set<Session> toClose = new HashSet<>();

		for (Iterator<Session> iterator = sessions.iterator(); iterator.hasNext();) {
			Session session = iterator.next();

			if (session.getCleanupTimestamp().isBefore(LocalDateTime.now())) {
				toClose.add(session);
			}
		}

		for (Session session : toClose) {
			try {
				log.info("Closing stale connection on websocket client");

				session.getSession().close();
			}
			catch (Exception ex) {
				log.warn("Failed to close connection", ex);
			}
		}
	}
}