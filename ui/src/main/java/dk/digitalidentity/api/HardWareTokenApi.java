package dk.digitalidentity.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.HardWareTokenApiDTO;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.service.CachedMfaClientService;

@RestController
public class HardWareTokenApi {

    @Autowired
    private CachedMfaClientService cachedMfaClientService;

    @GetMapping("/api/kodeviser")
    public ResponseEntity<?> getAll() {
        List<CachedMfaClient> cachedMfaClientList = cachedMfaClientService.findAll();
        List<HardWareTokenApiDTO> result = new ArrayList<>();

        for (CachedMfaClient entry : cachedMfaClientList) {
            HardWareTokenApiDTO apiDTO = new HardWareTokenApiDTO();
            apiDTO.setTime(entry.getLastUsed());
            apiDTO.setName(entry.getName());
            apiDTO.setDeviceId(entry.getDeviceId());
            apiDTO.setNsisLevel(entry.getNsisLevel());
            apiDTO.setSamAccountName(entry.getPerson().getSamaccountName());
            apiDTO.setSerialNumber(entry.getSerialnumber());

            result.add(apiDTO);
        }

        return ResponseEntity.ok(result);
    }
}
