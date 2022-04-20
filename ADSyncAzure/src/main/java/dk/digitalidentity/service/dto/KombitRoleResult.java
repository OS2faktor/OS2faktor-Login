package dk.digitalidentity.service.dto;

import java.util.ArrayList;
import java.util.HashMap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KombitRoleResult {
	private HashMap<String, UserKombitRoleResultEntry> users;
	private ArrayList<BatchedCall> deltaLinks;

	public KombitRoleResult(HashMap<String, UserKombitRoleResultEntry> users, ArrayList<BatchedCall> deltaLinks) {
		this.users = users;
		this.deltaLinks = deltaLinks;
	}

	public KombitRoleResult() {
		this.users = new HashMap<>();
		this.deltaLinks = new ArrayList<>();
	}
}
