package dk.digitalidentity.datatables.model;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

@Entity(name = "view_persons_groups")
@Getter
public class PersonGroupView {

	@Id
	@Column
	private long id;
	
	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private AdminPersonView person;
	
	@Column
	private long groupId;
}