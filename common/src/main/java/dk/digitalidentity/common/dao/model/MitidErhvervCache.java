package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mitid_erhverv_cache")
@Setter
@Getter
public class MitidErhvervCache {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private long mitidErhvervId;

	@Column
	private String status;

	@Column
	private String givenname;

	@Column
	private String surname;
	
	@Column
	private String cpr;
	
	@Column
	private String uuid;

	@Column
	private String email;

	@Column
	private String rid;

	@Column
	private boolean localCredential;

	@Column
	private boolean mitidPrivatCredential;

	@Column
	private boolean qualifiedSignature;

	@Column
	private LocalDateTime lastUpdated;

	public boolean equalsTo(MitidErhvervCache hit) {
		if (!Objects.equals(this.status, hit.status)) {
			return false;
		}

		if (!Objects.equals(this.givenname, hit.givenname)) {
			return false;
		}
		
		if (!Objects.equals(this.surname, hit.surname)) {
			return false;
		}
		
		if (!Objects.equals(this.cpr, hit.cpr)) {
			return false;
		}

		// very weird if this happens
		if (!Objects.equals(this.uuid, hit.uuid)) {
			return false;
		}

		if (!Objects.equals(this.email, hit.email)) {
			return false;
		}

		if (!Objects.equals(this.rid, hit.rid)) {
			return false;
		}

		if (this.localCredential != hit.localCredential) {
			return false;
		}

		if (this.mitidPrivatCredential != hit.mitidPrivatCredential) {
			return false;
		}

		if (this.qualifiedSignature != hit.qualifiedSignature) {
			return false;
		}

		return true;
	}

	public void copyFields(MitidErhvervCache employee) {
		this.status = employee.getStatus();
		this.givenname = employee.getGivenname();
		this.surname = employee.getSurname();
		this.cpr = employee.getCpr();
		this.uuid = employee.getUuid();
		this.email = employee.getEmail();
		this.rid = employee.getRid();
		this.localCredential = employee.isLocalCredential();
		this.mitidPrivatCredential = employee.isMitidPrivatCredential();		
		this.qualifiedSignature = employee.isQualifiedSignature();
	}
}
