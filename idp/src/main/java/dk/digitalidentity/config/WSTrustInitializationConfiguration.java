package dk.digitalidentity.config;

import org.apache.wss4j.dom.engine.WSSConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WSTrustInitializationConfiguration {
	
	@EventListener(ApplicationReadyEvent.class)
	public void initWSTrust() {
		WSSConfig.init();
		log.info("WS-Trust initialized");
	}
}
