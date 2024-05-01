package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
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

import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.serviceprovider.ServiceProviderConfig;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_configuration")
@Setter
@Getter
public class SqlServiceProviderConfiguration implements ServiceProviderConfig {
	
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
    @Size(max = 255)
    private String metadataUrl;

    @Column
    private String metadataContent;

    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private NameIdFormat nameIdFormat;

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
    @NotNull
    private boolean nemLogInBrokerEnabled;

    @Column
    @Enumerated(EnumType.STRING)
    private NSISLevel nsisLevelRequired;

    @Column
    private boolean encryptAssertions;

    @Column
    private boolean enabled;
    
    @Column
    private boolean requireOiosaml3Profile;
    
    @Column
    private boolean allowUnsignedAuthnRequests;

    @Column
    private boolean disableSubjectConfirmation;

    @Column
    @Enumerated(EnumType.STRING)
    private Protocol protocol;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderRequiredField> requiredFields;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderStaticClaim> staticClaims;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderRoleCatalogueClaim> rcClaims;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderAdvancedClaim> advancedClaims;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderGroupClaim> groupClaims;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
	private Set<SqlServiceProviderMfaExemptedDomain> mfaExemptions;

	@Column
	private LocalDateTime lastUpdated;

	@Column
	private LocalDateTime manualReloadTimestamp;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "configuration", fetch = FetchType.LAZY)
    private Set<SqlServiceProviderCondition> conditions;

    @Column(name = "prefer_nist")
    private boolean preferNIST;
    
    @Column
    private boolean badMetadata;
    
    @Column
    private String additionalEntityIds;

    @Column
    private Long customPasswordExpiry;

    @Column
    private Long customMfaExpiry;

    public void loadFully() {
        this.requiredFields.size();
        this.staticClaims.size();
        this.rcClaims.size();
        this.advancedClaims.size();
        this.groupClaims.size();
        this.mfaExemptions.size();

        if (this.conditions != null) {
            this.conditions.size();
            this.conditions.forEach(c -> {
                if (c.getGroup() != null) {
                    c.getGroup().getMemberMapping().size();
                    c.getGroup().getDomain().getName();
                }

                if (c.getDomain() != null) {
                    c.getDomain().getChildDomains().size();
                    if (c.getDomain().getParent() != null) {
                    	c.getDomain().getParent().getName();
                    }
                }
            });
        }
    }
}
