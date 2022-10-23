package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum NSISLevel {
	NONE(0, "enum.nsislevel.none"),
	LOW(1, "enum.nsislevel.low"),
	SUBSTANTIAL(2, "enum.nsislevel.substantial"),
	HIGH(3, "enum.nsislevel.high");

	private String message;
	private int level;

	private NSISLevel(int level, String message) {
		this.level = level;
		this.message = message;
	}

	public boolean equalOrLesser(NSISLevel other) {
		if (other == null) {
			return false;
		}

		return this.level <= other.level;
	}

	public boolean isGreater(NSISLevel other) {
		if (other == null) {
			return true;
		}
		return this.level > other.level;
	}

	public String toClaimValue() {
		switch (this) {
			case HIGH:
				return "High";
			case LOW:
				return "Low";
			case SUBSTANTIAL:
				return "Substantial";
			case NONE:
				return null;
		}
		
		return null;
	}
}
