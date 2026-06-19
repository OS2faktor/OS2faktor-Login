package dk.digitalidentity.common.config.modules;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordSyncQueueConfiguration {

    // maximum age of queue items. Items older than this are skipped during processing and deleted during cleanup.
    private Duration maxAge = Duration.ofMinutes(10);

    // delay used to avoid syncing timing issues
    private Duration syncBuffer = Duration.ofMinutes(2);
}
