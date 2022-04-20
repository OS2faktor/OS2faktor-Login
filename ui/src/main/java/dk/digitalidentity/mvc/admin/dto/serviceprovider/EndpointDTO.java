package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import org.opensaml.saml.saml2.metadata.Endpoint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EndpointDTO {
    private String url;
    private String binding;
    private String type;

    public EndpointDTO(String type, Endpoint endpoint) {
        this.type = type;
        this.url = endpoint.getLocation();
        this.binding = endpoint.getBinding();
    }
}
