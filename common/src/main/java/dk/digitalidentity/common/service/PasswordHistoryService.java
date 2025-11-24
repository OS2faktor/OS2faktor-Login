package dk.digitalidentity.common.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordHistoryDao;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;

@Service
public class PasswordHistoryService {

    @Autowired
    private PasswordHistoryDao passwordHistoryDao;
    
    @Autowired
    private PasswordSettingService passwordSettingService;

    public void save(PasswordHistory passwordHistory) {
        passwordHistoryDao.save(passwordHistory);
    }

    public void delete(PasswordHistory passwordHistory) {
        passwordHistoryDao.delete(passwordHistory);
    }

    public List<PasswordHistory> getAll() {
        return passwordHistoryDao.findAll();
    }

    private List<PasswordHistory> getByPerson(Person person) {
        return passwordHistoryDao.findByPerson(person);
    }

    public List<String> getLastXPasswords(Person person) {
        // Get and sort list
        List<PasswordHistory> records = getByPerson(person);
        records.sort(Comparator.comparing(PasswordHistory::getId));

        PasswordSetting settings = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
        
        // Delete records if we have more than the number configured in settings
        int amountToBeDeleted = records.size() - Math.min(records.size(), settings.getOldPasswordNumber().intValue());
        for (int i = 0; i < amountToBeDeleted; i++) {
            passwordHistoryDao.delete(records.get(i));
        }

        // Return list of passwords
        return passwordHistoryDao.findByPerson(person).stream().map(PasswordHistory::getPassword).collect(Collectors.toList());
    }
}
