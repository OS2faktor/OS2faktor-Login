package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.RoleCatalogueOperation;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_rc_claims")
@Setter
@Getter
public class SqlServiceProviderRoleCatalogueClaim {
	
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JsonIgnore
    private SqlServiceProviderConfiguration configuration;

    @NotNull
    @Column
    @Enumerated(EnumType.STRING)
    private RoleCatalogueOperation externalOperation;
    
    @Size(max = 255)
    @Column(name = "external_operation_argument")
    private String externalOperationArgument;
    
    @NotNull
    @Size(max = 255)
    @Column(name = "claim_name")
    private String claimName;

    @Size(max = 255)
    @Column(name = "claim_value")
    private String claimValue;
}
