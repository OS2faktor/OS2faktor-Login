package dk.digitalidentity.common.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_required_fields")
@Setter
@Getter
public class SqlServiceProviderRequiredField {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    private SqlServiceProviderConfiguration configuration;

    @Column
    @NotNull
    @Size(max = 255)
    private String personField;

    @Column
    @NotNull
    @Size(max = 255)
    private String attributeName;

    @Column
    @NotNull
    private boolean singleValueOnly;
}
