package dk.digitalidentity.common.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.modules.school.StudentPwdRoleSettingConfiguration;
import dk.digitalidentity.common.config.modules.school.StudentPwdSpecialNeedsClassConfiguration;
import dk.digitalidentity.common.dao.SchoolClassDao;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.common.dao.model.enums.RoleSettingType;
import dk.digitalidentity.common.dao.model.mapping.SchoolClassPasswordWordMapping;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;

@Service
public class SchoolClassService {
	private final String EASY_WORDS = "kat,mus,hund,hest,gris,ko,ged,hvalp,føl,rød,grøn,brun,blå,pink,gul,grå,sko,ske,bold,ost,æble,gave,kage,ven,fugl,gren,pind,tand,bil,bord,løve,pote,abe,hue,leg,spil,hus,stol,sol,seng,pude,dør,sø,lys,lyn,sky,tyv,tårn,æg,hop,fod,bog,nat,barn,sok,drøm,lyd,træ,skov,får,dag,uge,år,baby,blad,glas,låg,reol,hæk,lus,nål,kål,bus,mål,bål,ugle,frø,myre,måne,æske,is,haj,orm,øje,kop,dæk,sæl,tog,ørn,rat,ræv,flag,hval,fisk,rose,høne,flue,hane,mund,hjul";
	private SecureRandom random = new SecureRandom();

	@Autowired
	private SchoolClassDao schoolClassDao;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	public SchoolClass save(SchoolClass schoolClass) {
		return schoolClassDao.save(schoolClass);
	}
	
	public SchoolClass getByClassIdentifierAndInstitutionId(String classIdentifier, String institutionId) {
		return schoolClassDao.findByClassIdentifierAndInstitutionId(classIdentifier, institutionId);
	}

	public List<SchoolClass> getAll() {
		return schoolClassDao.findAll();
	}
	
	public void deleteAll(List<SchoolClass> toDelete) {
		schoolClassDao.deleteAll(toDelete);
	}
	
	public SchoolClass getById(long id) {
		return schoolClassDao.findById(id);
	}
	
	public boolean isSpecialNeedsClass(SchoolClass schoolClass) {
		for (StudentPwdSpecialNeedsClassConfiguration setting : commonConfiguration.getStilStudent().getSpecialNeedsClasses()) {
			if (setting.getClassName().equals(schoolClass.getName()) && setting.getInstitutionNumber().equals(schoolClass.getInstitutionId())) {
				return true;
			}
		}

		return false;
	}
	
	@Transactional
	public void generateSchoolClassPasswordWords() {
		for (SchoolClass clazz : getAll()) {
			if (!StringUtils.hasLength(clazz.getLevel()) || clazz.getPasswordWords().size() > 0) {
				continue;
			}
			
			int level = 99;
			
			try {
				level = Integer.parseInt(clazz.getLevel());
			}
			catch (Exception ignored) {
				;
			}
			
			if (level <= 3) {
				addPasswordWords(clazz);
			}
		}
	}
	
	public List<SchoolClass> getClassesPasswordCanBeChangedOnFromIndskoling(Person loggedInPerson) {
		List<SchoolClass> result = new ArrayList<>();

		for (SchoolClass schoolClass : getClassesPasswordCanBeChangedOn(loggedInPerson)) {
					
			// make sure the level is between 0 and 3 or it is a special needs class
			if (schoolClass.isIndskoling() || isSpecialNeedsClass(schoolClass)) {
				result.add(schoolClass);
			}
		}
		
		return result;
	}
	
	public List<SchoolClass> getClassesPasswordCanBeChangedOn(Person adult) {
		List<SchoolClass> result = new ArrayList<>();

		// quick abort
		if (adult.getSchoolRoles() == null || adult.getSchoolRoles().size() == 0) {
			return result;
		}

		// filter all roles that could potentially access student passwords
		List<SchoolRole> roles = new ArrayList<>();
		for (SchoolRole role : adult.getSchoolRoles()) {
			
			// find the settings for this role
			StudentPwdRoleSettingConfiguration roleSetting = commonConfiguration.getStilStudent().getRoleSettings().stream()
					.filter(r -> r.getRole().equals(role.getRole()))
					.findFirst()
					.orElse(null);

			// make sure password change is allowed for this role
			if (!adult.isInstitutionStudentPasswordAdmin()) {
				if (roleSetting == null || roleSetting.getType().equals(RoleSettingType.CANNOT_CHANGE_PASSWORD)) {
					continue;
				}
			}

			roles.add(role);
		}

		// no reason to do any further lookups
		if (roles.size() == 0) {
			return result;
		}

		for (SchoolClass clazz : getAll()) {
			// check each role for a match
			for (SchoolRole role : roles) {
				// skip if we have already added this class
				if (result.stream().anyMatch(c -> c.getId() == clazz.getId())) {
					continue;
				}

				// if the adult isInstitutionStudentPasswordAdmin password change is allowed on all students inside its own institutions
				if (clazz.getInstitutionId().equals(role.getInstitutionId()) && adult.isInstitutionStudentPasswordAdmin()) {
					result.add(clazz);
					break;
				}
				
				StudentPwdRoleSettingConfiguration roleSetting = commonConfiguration.getStilStudent().getRoleSettings().stream()
						.filter(r -> r.getRole().equals(role.getRole()))
						.findFirst()
						.orElse(null);

				switch (roleSetting.getType()) {
					case CAN_CHANGE_PASSWORD_ON_GROUP_MATCH:
						// make sure there is a match on the type of class (MAIN_GROUP is the usual configuration here, but it could be any)
						List<String> filterClassTypes = Arrays.asList(roleSetting.getFilter().split(","));
						if (clazz.getType() == null || !filterClassTypes.contains(clazz.getType().toString())) {
							break;
						}

						for (SchoolRoleSchoolClassMapping schoolClassMapping : role.getSchoolClasses()) {
							SchoolClass roleSchoolClass = schoolClassMapping.getSchoolClass();

							if (Objects.equals(clazz.getClassIdentifier(), roleSchoolClass.getClassIdentifier())) {
								result.add(clazz);
								break;
							}
						}

						break;
					case CAN_CHANGE_PASSWORD_ON_LEVEL_MATCH:
						// make sure there is a match on which levels are granted access to from this role, and the classes level
						List<String> filterClassLevels = Arrays.asList(roleSetting.getFilter().split(","));
						if (!StringUtils.hasLength(clazz.getLevel()) || !filterClassLevels.contains(clazz.getLevel())) {
							break;
						}

						result.add(clazz);
						break;
					case CANNOT_CHANGE_PASSWORD:
						// should not get here, but we add it so we do not need to add a DEFAULT case, allowing us to easy detect modifications to the ENUM in the future
						break;
				}
			}
		}
		
		return result;
	}
	
	private void addPasswordWords(SchoolClass passwordClass) {
		List<String> easyWords = getEasyWords(); 
		int[] indexes = random.ints(0, 100).distinct().limit(9).toArray();

		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[0]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[1]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[2]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[3]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[4]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[5]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[6]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[7]), passwordClass));
		passwordClass.getPasswordWords().add(new SchoolClassPasswordWordMapping(easyWords.get(indexes[8]), passwordClass));
		
		save(passwordClass);
	}
	
	private List<String> getEasyWords() {
		return Arrays.asList(EASY_WORDS.split(","));
	}
}
