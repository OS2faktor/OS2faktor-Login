package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum ReplicationStatus {
	WAITING_FOR_REPLICATION("enum.replication.status.waiting_for_replication"),
	ERROR("enum.replication.status.error"),
	SYNCHRONIZED("enum.replication.status.synchronized"),
	FINAL_ERROR("enum.replication.status.final_error"),
	DO_NOT_REPLICATE("enum.replication.status.do_not_replicate");
	
	private String message;
	
	private ReplicationStatus(String message) {
		this.message = message;
	}
}
