package dk.digitalidentity.common.dao.model.mapping;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.SchoolClass;
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
	@BatchSize(size = 100)
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
