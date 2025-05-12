package dk.digitalidentity.common.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.RoleCatalogueOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Column
    @NotNull
    private boolean singleValueOnly;
}
