package dk.digitalidentity.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;

@RestController
public class InternalApi {

    @Autowired
    private SettingService settingService;

    @PostMapping("/api/internal/setTraceLog")
    public ResponseEntity<?> setTraceLog(@RequestBody boolean isEnabled) {
        settingService.setBoolean(SettingKey.TRACE_LOGGING, isEnabled);
        return ResponseEntity.ok("Set the value of trace log to " + isEnabled);
    }
}
