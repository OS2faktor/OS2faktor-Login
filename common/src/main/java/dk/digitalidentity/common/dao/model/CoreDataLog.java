package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "core_data_log")
@Setter
@Getter
@NoArgsConstructor
public class CoreDataLog {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @CreationTimestamp
    @Column
    private LocalDateTime tts;

    @Column
    private String endpoint;
    
    @Column
    private long processingTime;
    
    @Column
    private boolean success;

    @ManyToOne
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;

    public CoreDataLog(String endpoint, Domain domain, long processingTime, boolean success) {
        this.endpoint = endpoint;
        this.domain = domain;
        this.processingTime = processingTime;
        this.success = success;
    }
}
