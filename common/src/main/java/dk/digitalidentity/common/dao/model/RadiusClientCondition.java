package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
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
@Table(name = "radius_client_condition")
@NoArgsConstructor
public class RadiusClientCondition {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "radius_client_id")
    private RadiusClient client;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column
    private RadiusClientConditionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private Domain domain;

    public RadiusClientCondition(RadiusClient client, RadiusClientConditionType type, Group group, Domain domain) {
        this.client = client;
        this.type = type;
        this.group = group;
        this.domain = domain;
    }
}
