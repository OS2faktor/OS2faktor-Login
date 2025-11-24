package dk.digitalidentity.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.log.MemoryLogger;

@RestController
public class InternalApi {

	@Autowired
	private MemoryLogger memoryLogger;

	@GetMapping("/api/internal/memory")
	public ResponseEntity<?> dumpMemory() {
		memoryLogger.logMemoryMetrics();

		return ResponseEntity.ok().build();
	}
}
