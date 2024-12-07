package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.ApiRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
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
