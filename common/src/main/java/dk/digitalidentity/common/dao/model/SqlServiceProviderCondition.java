package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
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
