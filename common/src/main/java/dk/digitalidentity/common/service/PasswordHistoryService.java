package dk.digitalidentity.common.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordHistoryDao;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.Person;

@Service
public class PasswordHistoryService {

    @Autowired
    private PasswordHistoryDao passwordHistoryDao;

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

    public List<String> getLastTenPasswords(Person person) {
        // Get and sort list
        List<PasswordHistory> persons = getByPerson(person);
        persons.sort(Comparator.comparing(PasswordHistory::getId));

        // Delete records if we have more than 10
        int amountToBeDeleted = persons.size() - Math.min(persons.size(), 10);
        for (int i = 0; i < amountToBeDeleted; i++) {
            passwordHistoryDao.delete(persons.get(i));
        }

        // Return list of passwords
        return passwordHistoryDao.findByPerson(person).stream().map(PasswordHistory::getPassword).collect(Collectors.toList());
    }
}
