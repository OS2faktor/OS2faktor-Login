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
    
    /* this is used if we get data like this
     * 
    "customSecurityAttributes": {
        "AlleAnsatteCPR": {
            "@odata.type": "#microsoft.graph.customSecurityAttributeValue",
            "CPR@odata.type": "#Collection(String)",
            "CPR": [
                "0000000000"
            ]
        }
    }
     *
     */
    private boolean extractCustomCpr = false;
    private String customCprCollectionName;
    private String customCprFieldName;
}
