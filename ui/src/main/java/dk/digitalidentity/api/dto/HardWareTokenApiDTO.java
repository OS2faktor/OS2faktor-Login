package dk.digitalidentity.api.dto;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HardWareTokenApiDTO {
    private String name;
    private String deviceId;
    private String serialNumber;
    private NSISLevel nsisLevel;
    private LocalDateTime time;
    private String samAccountName;
}
