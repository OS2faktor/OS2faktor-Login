package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.hibernate.envers.Audited;

import lombok.Getter;
import lombok.Setter;

@Audited
@Entity(name = "privacy_policy")
@Getter
@Setter
public class PrivacyPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String content;
}
