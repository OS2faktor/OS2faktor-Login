package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sql_service_provider_condition")
@NoArgsConstructor
public class SqlServiceProviderCondition {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sql_service_provider_configuration_id")
    private SqlServiceProviderConfiguration configuration;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column
    private SqlServiceProviderConditionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private Domain domain;

    public SqlServiceProviderCondition(SqlServiceProviderConfiguration configuration, SqlServiceProviderConditionType type, Group group, Domain domain) {
        this.configuration = configuration;
        this.type = type;
        this.group = group;
        this.domain = domain;
    }
}
