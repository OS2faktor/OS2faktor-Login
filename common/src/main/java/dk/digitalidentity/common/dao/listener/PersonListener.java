package dk.digitalidentity.common.dao.listener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;

@Lazy(value = false)
@Component
public class PersonListener {    
    private static EntityManager entityManager;
	private static CommonConfiguration commonConfiguration;
	private static PersonListenerAdapter personListenerAdapter;

    @Autowired
    public void setEntityManager(EntityManager entityManager) {
        PersonListener.entityManager = entityManager;
    }
    
	@Autowired
    public void setCommonConfiguration(CommonConfiguration commonConfiguration) {
        PersonListener.commonConfiguration = commonConfiguration;
    }

	@Autowired
    public void setPersonListenerAdapter(PersonListenerAdapter personListenerAdapter) {
        PersonListener.personListenerAdapter = personListenerAdapter;
    }
	
    @PostPersist
    public void beforeInsert(Person person) {
    	// only relevant if we need to update MitID Erhverv
		if (!commonConfiguration.getNemLoginApi().isEnabled()) {
			return;
		}
		
		if (person.isTransferToNemlogin()) {
			personListenerAdapter.generateNemloginActions(person);
		}
    }

    @PreUpdate
    public void beforeUpdate(Person person) {
    	// only relevant if we need to update MitID Erhverv
		if (!commonConfiguration.getNemLoginApi().isEnabled()) {
			return;
		}

		Set<String> props = modifiedProperties(person);
		
		// is transferToNemLogin and no UUID exists, or any of the attributes has changed, perform an update
		if ((!StringUtils.hasLength(person.getNemloginUserUuid()) && person.isTransferToNemlogin()) ||
			props.contains("transferToNemlogin") ||
			props.contains("email") ||
			props.contains("privateMitId") ||
			props.contains("qualifiedSignature")) {

			personListenerAdapter.generateNemloginActions(props, person);
		}
    }

    private Set<String> modifiedProperties(Person person) {
    	Set<String> props = new HashSet<String>();
    	
        // Unwrap directly to SessionImplementor - this handles the proxy correctly
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        PersistenceContext persistenceContext = session.getPersistenceContext();
        
        EntityEntry entityEntry = persistenceContext.getEntry(person);
        
        if (entityEntry != null) {
            Object[] loadedState = entityEntry.getLoadedState();
            EntityPersister persister = entityEntry.getPersister();
            String[] propertyNames = persister.getPropertyNames();
            
            for (int i = 0; i < propertyNames.length; i++) {
                Object oldValue = loadedState[i];
                Object newValue = persister.getValue(person, i);
                
                if (!Objects.equals(oldValue, newValue)) {
                	props.add(propertyNames[i]);
                }
            }
        }
        
        return props;
    }
}
