package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
