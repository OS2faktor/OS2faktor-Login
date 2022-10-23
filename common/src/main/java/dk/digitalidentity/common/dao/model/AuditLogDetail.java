package dk.digitalidentity.common.dao.model;

import java.util.Base64;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.util.ZipUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

	public String getDetailContent() {
		if (detailType == DetailType.XML_ZIP) {
			if (detailContent != null) {
				try {
					return new String(ZipUtil.decompress(Base64.getDecoder().decode(detailContent)));
				} catch (Exception e) {
					log.warn("AuditLogDetail: Error occured while tying to decompress xml log details.", e);
				}
			}
		}
		
		return detailContent;
	}

	public DetailType getDetailType() {
		if (detailType == DetailType.XML_ZIP) {
			return DetailType.XML;
		}

		return detailType;
	}
}
