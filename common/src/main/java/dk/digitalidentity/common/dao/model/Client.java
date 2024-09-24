package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import dk.digitalidentity.common.dao.model.enums.ApiRole;
import lombok.Getter;
import lombok.Setter;

@Table(name = "clients")
@Setter
@Getter
@Entity
public class Client {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @Column
    private String apiKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column
    private ApiRole role;

    @Column
    private String description;
}
