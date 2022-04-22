package dk.digitalidentity.common.log;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
public class ErrorLogDto {
    private final String exceptionType;
    private final String message;
    private final NSISLevel passwordNSISLevel;
    private final NSISLevel mfaNSISlevel;
    private final NSISLevel personNsisLevel;
    private final String passwordTimestamp;
    private final String mfaTimestamp;
    private final boolean hasApprovedConditions;
    private final boolean hasNsisUser;
    private final boolean nsisAllowed;
    private final boolean lockedByBadPasswords;
    private final boolean lockedByPerson;
    private final boolean lockedByAdmin;
    private final boolean lockedByDataset;
    private final String sendTo;

    public ErrorLogDto(Exception exception, String sendTo, Person person, NSISLevel passwordNSISLevel, LocalDateTime passwordTimestamp, NSISLevel mfaNSISlevel, LocalDateTime mfaTimestamp) {
        // Exception
        this.exceptionType = exception.getClass().getSimpleName();
        this.message = exception.getMessage();

        // Session
        DateTimeFormatter timestampFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        this.passwordNSISLevel = passwordNSISLevel;
        this.passwordTimestamp = passwordTimestamp != null ? passwordTimestamp.format(timestampFormatter) : null;
        this.mfaNSISlevel = mfaNSISlevel;
        this.mfaTimestamp = mfaTimestamp != null ? mfaTimestamp.format(timestampFormatter) : null;

        // Person
        this.hasNsisUser = person.hasActivatedNSISUser();
        this.nsisAllowed = person.isNsisAllowed();
        this.lockedByBadPasswords = person.isLockedPassword();
        this.lockedByPerson = person.isLockedPerson();
        this.lockedByAdmin = person.isLockedAdmin();
        this.lockedByDataset = person.isLockedDataset();
        this.personNsisLevel = person.getNsisLevel();
        this.hasApprovedConditions = person.isApprovedConditions();
        
        // recipient
        this.sendTo = sendTo;
    }
}