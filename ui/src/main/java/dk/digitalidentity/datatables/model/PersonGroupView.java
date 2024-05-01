package dk.digitalidentity.datatables.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonBackReference;

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