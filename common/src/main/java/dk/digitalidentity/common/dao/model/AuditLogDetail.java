package dk.digitalidentity.common.dao.model;

import java.util.Base64;

import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.util.ZipUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
