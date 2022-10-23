package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
