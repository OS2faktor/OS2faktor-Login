package dk.digitalidentity.mvc.students;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordChangeQueueService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SchoolClassService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.mvc.otherUsers.validator.PasswordChangeValidator;
import dk.digitalidentity.mvc.students.dto.PasswordChangeForm;
import dk.digitalidentity.mvc.students.dto.StudentDTO;
import dk.digitalidentity.security.RequireChangePasswordOnOthersRole;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireChangePasswordOnOthersRole
@Controller
public class ChangePasswordOnStudentsController {

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;
	
	@Autowired
	private SchoolClassService schoolClassService;
	
	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/andre-brugere/kodeord/skift/list")
	public String getList(Model model) {
		Person loggedInPerson = securityUtil.getPerson();
		if (loggedInPerson == null) {
			return "redirect:/";
		}

		model.addAttribute("people", personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson, null).stream()
				.map(p -> new StudentDTO(p, (personService.isYoungStudent(p) != null)))
				.collect(Collectors.toList()));

		model.addAttribute("classes", schoolClassService.getClassesPasswordCanBeChangedOn(loggedInPerson));

		model.addAttribute("passwordMatrixEnabled", commonConfiguration.getStilStudent().isIndskolingSpecialEnabled());
		
		return "students/password-change/list";
	}
	
	// this is the special function that prints a password-matrix
	@GetMapping("/andre-brugere/klasser/{id}/print")
	public String getClassPrint(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id) {
		Person loggedInPerson = securityUtil.getPerson();
		if (loggedInPerson == null) {
			return "redirect:/";
		}
		
		SchoolClass schoolClass = schoolClassService.getById(id);
		if (schoolClass == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Klassen kan ikke findes");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		if (!schoolClassService.getClassesPasswordCanBeChangedOnFromIndskoling(loggedInPerson).stream().anyMatch(c -> c.getClassIdentifier().equals(schoolClass.getClassIdentifier()) && c.getInstitutionId().equals(schoolClass.getInstitutionId()))) {
			redirectAttributes.addFlashAttribute("flashError", "Denne klasse har ikke en kodeords-matrix");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		model.addAttribute("words", schoolClass.getPasswordWords().stream().map(w -> w.getWord()).collect(Collectors.toList()));

		return "students/print_classes";
	}
	
	// this is the special function that prints a class, including passwords if requested and possible
	@GetMapping("/andre-brugere/klasser/{id}/print-students")
	public String getClassStudentPrint(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id, @RequestParam("withPassword") boolean withPassword) {
		Person loggedInPerson = securityUtil.getPerson();
		if (loggedInPerson == null) {
			return "redirect:/";
		}
		
		SchoolClass schoolClass = schoolClassService.getById(id);
		if (schoolClass == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Klassen kan ikke findes");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		if (!schoolClassService.getClassesPasswordCanBeChangedOn(loggedInPerson).stream().anyMatch(c -> c.getClassIdentifier().equals(schoolClass.getClassIdentifier()) && c.getInstitutionId().equals(schoolClass.getInstitutionId()))) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Du har ikke adgang til denne klasse");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		// sanity check, to make sure password cannot be requested for older classes
		try {
			int level = Integer.parseInt(schoolClass.getLevel());
			if (level >= 4) {
				withPassword = false;
			}
		}
		catch (Exception ignored) {
			withPassword = false;
		}
		
		final boolean fWithPassword = withPassword;
		List<StudentDTO> students = personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson, schoolClass)
			.stream()
			.map(p -> new StudentDTO(p, fWithPassword))
			.collect(Collectors.toList());

		if (withPassword) {
			for (StudentDTO student : students) {
				if (student.isCanSeePassword()) {
					try {
						if (StringUtils.hasLength(student.getPassword())) {
							student.setPassword(passwordChangeQueueService.decryptPassword(student.getPassword()));
						}
					}
					catch (Exception ex) {
						log.warn("Cannot decrypt student password for " + student.getSamaccountName() + " : " + ex.getMessage());
						student.setPassword(null);
					}
				}
			}
		}

		model.addAttribute("withPassword", withPassword);
		model.addAttribute("students", students);
		model.addAttribute("className", schoolClass.getName());

		return "students/print_classes_students";
	}

	@GetMapping("/andre-brugere/{id}/kodeord/skift")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id) {
		Person personToBeEdited = personService.getById(id);
		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));
		model.addAttribute("passwordForm", new PasswordChangeForm(personToBeEdited, isForcePasswordChange(personToBeEdited)));

		return "students/password-change/change-password";
	}
	
	@GetMapping("/andre-brugere/{id}/kodeord/se")
	public String seePassword(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id) {
		Person personToBeEdited = personService.getById(id);
		if (personToBeEdited == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Brugeren findes ikke");
			return "redirect:/andre-brugere/kodeord/skift/list";			
		}
		
		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger og du kan derfor heller ikke se kodeordet");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}
		
		if (personService.isYoungStudent(personToBeEdited) == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke muligt at se denne brugers kodeord");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}
		
		if (!StringUtils.hasLength(personToBeEdited.getStudentPassword())) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Der er ikke dannet et kodeord for denne bruger endnu");
			return "redirect:/andre-brugere/kodeord/skift/list";			
		}

		try {
			model.addAttribute("decrypted", passwordChangeQueueService.decryptPassword(personToBeEdited.getStudentPassword()));
		}
		catch (InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Kodeordet kan ikke vises");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		model.addAttribute("person", personToBeEdited);

		return "students/see-password";
	}

	@PostMapping("/andre-brugere/kodeord/skift")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) {
		Person personToBeEdited = personService.getById(form.getPersonId());
		if (personToBeEdited == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		// Check for password errors
		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));

			return "students/password-change/change-password";
		}

		try {
			Person loggedInPerson = securityUtil.getPerson();

			if (loggedInPerson == null) {
				log.warn("Person ikke logget ind, session timeout?");
				redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt administrator");

				return "redirect:/andre-brugere/kodeord/skift/list";
			}

			ADPasswordResponse.ADPasswordStatus adPasswordStatus = personService.changePasswordByAdmin(personToBeEdited, form.getPassword(), loggedInPerson, form.isForceChangePassword());

			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				model.addAttribute("technicalError", true);
				model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));

				return "students/password-change/change-password";
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			log.error("Exception while trying to change password on another user", e);

			redirectAttributes.addFlashAttribute("flashError", "Fejl! Der opstod en teknisk fejl");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		redirectAttributes.addFlashAttribute("flashSuccess", "Kodeord ændret");

		return "redirect:/andre-brugere/kodeord/skift/list";
	}

	private boolean isForcePasswordChange(Person student) {
		long age = PersonService.getAge(student.getCpr());
		if (age > commonConfiguration.getStilStudent().getForceChangePasswordAfterAge()) {
			return true;
		}

		return false;
	}

	private boolean allowedPasswordChange(Person personToBeEdited) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return false;
		}

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (loggedInPerson == null) {
			return false;
		}

		if (personToBeEdited == null) {
			return false;
		}

		if (personToBeEdited.isNsisAllowed()) {
			return false;
		}

		PasswordSetting settings = passwordSettingService.getSettings(personToBeEdited);
		if (settings.isCanNotChangePasswordEnabled() && settings.getCanNotChangePasswordGroup() != null && GroupService.memberOfGroup(personToBeEdited, Collections.singletonList(settings.getCanNotChangePasswordGroup()))) {
			return false;
		}

		if (personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson, null).stream().anyMatch(p -> p.getUuid().equals(personToBeEdited.getUuid()))) {
			return true;
		}

		return false;
	}
}
