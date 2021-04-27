package dk.digitalidentity.common.dao.model;


import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity(name = "password_change_queue")
@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String uuid;

    @Column
    private String samaccountName;

    @Column
    private String domain;

    @Column
    private String password;

    @Column
    @CreationTimestamp
    private LocalDateTime tts;

    @Column
    @Enumerated(EnumType.STRING)
    private ReplicationStatus status;

    @Column
    private String message;

    public PasswordChangeQueue(Person person, String newPassword) {
    	this.password = newPassword;
    	this.domain = person.getDomain().getName();
    	this.samaccountName = person.getSamaccountName();
    	this.uuid = person.getUuid();
    	this.status = ReplicationStatus.WAITING_FOR_REPLICATION;
    }
}
