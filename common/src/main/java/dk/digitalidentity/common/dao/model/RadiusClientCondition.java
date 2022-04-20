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

import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
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
