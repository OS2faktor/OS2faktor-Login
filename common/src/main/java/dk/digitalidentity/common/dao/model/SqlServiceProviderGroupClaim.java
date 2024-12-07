package dk.digitalidentity.common.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_group_claims")
@Setter
@Getter
public class SqlServiceProviderGroupClaim {
	
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JsonIgnore
    private SqlServiceProviderConfiguration configuration;

    @NotNull
    @Size(max = 255)
    @Column(name = "claim_name")
    private String claimName;

    @Size(max = 255)
    @Column(name = "claim_value")
    private String claimValue;
    
	@OneToOne
	@JoinColumn(name = "group_id", referencedColumnName = "id")
	private Group group;
}
