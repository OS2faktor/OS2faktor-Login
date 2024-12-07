package dk.digitalidentity.common.dao.model;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ggroups")
public class Group {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private String name;

    @Column
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    @NotNull
    private Domain domain;

    @Column
    private String description;

	@BatchSize(size = 100)
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PersonGroupMapping> members;

    public List<Person> getMembers() {
        return members.stream().map(PersonGroupMapping::getPerson).collect(Collectors.toList());
    }

    public List<PersonGroupMapping> getMemberMapping() {
        return members;
    }
}
