package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ad_password_cache")
@Setter
@Getter
public class ADPasswordCache {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private long domainId;
	
	@Column
	private String samAccountName;
	
	@Column
	private String password;
	
	@Column
	private LocalDateTime lastUpdated;
}
