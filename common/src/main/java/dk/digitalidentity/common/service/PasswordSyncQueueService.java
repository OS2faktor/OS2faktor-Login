package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PasswordSyncQueueDao;
import dk.digitalidentity.common.dao.model.PasswordSyncQueueItem;
import dk.digitalidentity.common.dao.model.PasswordSyncQueueItem.Status;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordSyncQueueService {
    private final ADPasswordService adPasswordService;
    private final PasswordSyncQueueDao dao;
    private final PersonService personService;
    private final CommonConfiguration configuration;
    private final EncryptionUtil encryptionUtil;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(@NonNull final Person person, @NonNull final String password) {
		try {
            final String encrypted = encryptionUtil.encryptPassword(password);
            final PasswordSyncQueueItem item = dao.findPendingByPerson(person).orElseGet(PasswordSyncQueueItem::new);

            item.setEncryptedPassword(encrypted);
            item.setNextAttemptAt(LocalDateTime.now().plus(configuration.getPasswordSync().getSyncBuffer()));
            item.setPerson(person);
            
            dao.save(item);
		}
		catch (Exception ex) {
            log.error("Failed to encrypt queue item for user " + person.getSamaccountName(), ex);
		}
    }

    // TODO: fix this, should not transactional, but we need to load a lot of person data, so we support this check:
    // dk.digitalidentity.common.service.PersonService.isStudentInIndskolingOrSpecialNeedsClass(PersonService.java:707)
    // which currently fails because data is not loaded on person
    @Transactional
    public void processQueueItems() {
        final LocalDateTime now = LocalDateTime.now();
        final List<PasswordSyncQueueItem> items = dao.findProcessableMessages(now.minus(configuration.getPasswordSync().getMaxAge()), now);

        items.forEach(this::process);
    }

    private void process(@NonNull final PasswordSyncQueueItem item) {
        item.setStatus(Status.PROCESSING);

        final var passwordTimestamp = item.getPerson().getPasswordTimestamp();
        if (passwordTimestamp != null && passwordTimestamp.isAfter(item.getTts().minus(configuration.getPasswordSync().getSyncBuffer()))) {
            log.info("Password already changed for user " + item.getPerson().getSamaccountName());
            dequeue(item);
            return;
        }

        final String password;
        try {
            password = encryptionUtil.decryptPassword(item.getEncryptedPassword());
        }
        catch (Exception ex) {
            log.error("Failed to decrypt queue item for user" + item.getPerson().getSamaccountName(), ex);
            dequeue(item);
            return;
        }

        try {
            final ADPasswordStatus resp = adPasswordService.validatePassword(item.getPerson(), password);
            if (resp == ADPasswordStatus.OK) {
            	log.info("Password changed in OS2faktor, based on password change from AD for user " + item.getPerson().getSamaccountName());
                personService.changePasswordBypassQueue(item.getPerson(), password);
                dequeue(item);
            }
            else {
            	log.warn("Password change failed - AD rejected new password for user " + item.getPerson().getSamaccountName());
                dequeue(item);
            }
        }
        catch (Exception ex) {
            log.error("Failed to update password for user " + item.getPerson().getSamaccountName(), ex);
            retry(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanup() {
        dao.deleteOldMessages(LocalDateTime.now().minus(configuration.getPasswordSync().getMaxAge()));
    }

    private void dequeue(@NonNull final PasswordSyncQueueItem item) {
        item.setStatus(Status.DONE);

        dao.save(item);
    }

    private void retry(@NonNull final PasswordSyncQueueItem item) {
        item.setStatus(Status.PENDING);
        item.setNextAttemptAt(LocalDateTime.now().plus(configuration.getPasswordSync().getSyncBuffer()));

        dao.save(item);
    }
}
