package dk.digitalidentity.datatables.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_hardware_token")
@Getter
@Setter
public class KodeviserView {

	@Id
	@Column
	private long id;

	@Column
	private String name;

	@Column
	private String personName;

	@Column
	private String samaccountName;

	@Column
	private boolean locked;

	@Column
	private String serialnumber;

}
