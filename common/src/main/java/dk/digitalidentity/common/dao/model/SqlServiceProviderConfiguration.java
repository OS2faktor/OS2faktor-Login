package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_configuration")
@Setter
@Getter
public class SqlServiceProviderConfiguration {
	
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    @NotNull
    @Size(max = 255)
    private String entityId;

    @Column
    @NotNull
    @Size(max = 255)
    private String name;

    @Column
    @NotNull
    @Size(max = 255)
    private String metadataUrl;

    @Column
    @NotNull
    @Size(max = 255)
    private String nameIdFormat;

    @Column
    @NotNull
    @Size(max = 255)
    private String nameIdValue;

    @Column
    @Enumerated(EnumType.STRING)
    private ForceMFARequired forceMfaRequired;

    @Column
    @NotNull
    private boolean preferNemid;

    @Column
    @Enumerated(EnumType.STRING)
    private NSISLevel nsisLevelRequired;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.EAGER)
	private Set<SqlServiceProviderRequiredField> requiredFields;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.EAGER)
	private Set<SqlServiceProviderStaticClaim> staticClaims;
}
