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
    private boolean changeOnNextLogin;

    @Column
    @CreationTimestamp
    private LocalDateTime tts;

    @Column
    @Enumerated(EnumType.STRING)
    private ReplicationStatus status;

    @Column
    private String message;
    
    @Column
    private boolean externallyReplicated;

    @Column
    private boolean azureReplicated;

    @Column
    private boolean googleWorkspaceReplicated;

    public PasswordChangeQueue(Person person, String newPassword, boolean forceChangePasswordOnNextLogin) {
    	this.password = newPassword;
    	this.samaccountName = person.getSamaccountName();
    	this.uuid = person.getUuid();
    	this.status = ReplicationStatus.WAITING_FOR_REPLICATION;
        this.domain = person.getTopLevelDomain().getName();
        this.changeOnNextLogin = forceChangePasswordOnNextLogin;
    }
}
