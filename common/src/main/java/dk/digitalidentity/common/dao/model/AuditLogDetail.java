package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import dk.digitalidentity.common.dao.model.enums.DetailType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditlogs_details")
@Setter
@Getter
public class AuditLogDetail {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Enumerated(EnumType.STRING)
	@Column
	private DetailType detailType;
	
	@Column
	private String detailContent;
	
	@Column
	private String detailSupplement;
}
