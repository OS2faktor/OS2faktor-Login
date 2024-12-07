package dk.digitalidentity.common.dao.model.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.SchoolClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "school_class_password_word")
@Getter
@Setter
@NoArgsConstructor
public class SchoolClassPasswordWordMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_class_id")
	@NotNull
	private SchoolClass schoolClass;
	
	@Column
	@NotNull
	private String word;

	public SchoolClassPasswordWordMapping(String word, SchoolClass SchoolClass) {
		this.word = word;
		this.schoolClass = SchoolClass;
	}
}
