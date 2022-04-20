package dk.digitalidentity.config;


import dk.digitalidentity.config.modules.AzureAd;
import dk.digitalidentity.config.modules.CoreData;
import dk.digitalidentity.config.modules.Scheduled;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.azure")
public class OS2faktorAzureADSyncConfiguration {
    private CoreData coreData = new CoreData();
    private AzureAd azureAd = new AzureAd();
    private Scheduled scheduled = new Scheduled();
    private boolean devmode = false;
    private String apiKey;
}
