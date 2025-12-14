package dk.digitalidentity.mvc.admin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.CachedMfaClientService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.mfa.MFAManagementService;
import dk.digitalidentity.mvc.admin.xlsview.AdminKodeviserReportXlsView;
import dk.digitalidentity.security.RequireKodeviserAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireKodeviserAdministrator
@Controller
public class KodeviserController {

	@Autowired
	private CachedMfaClientService cachedMfaClientService;

	@Autowired
	private MFAManagementService mfaManagmentService;
	
	@Autowired
	private SettingService settingsService;
	
	@Autowired
	private SecurityUtil securityUtil;

	@GetMapping("/admin/konfiguration/kodeviser")
	public String listHardwareDevices(Model model) {
		model.addAttribute("removeDeviceSetting", settingsService.getBoolean(SettingKey.REMOVE_DEVICE_WHEN_PERSON_LOCKED));
		
		model.addAttribute("isAdmin", securityUtil.isAdmin());
		
		return "admin/kodeviser-manage";
	}

	record CodeOffsetForm(
			@NotBlank(message = "Serienummeret skal være udfyldt") String serialnumber,
			@Pattern(regexp = "\\d{6}", message = "Første kode skal være på præcis 6 cifre") String firstCode,
			@Pattern(regexp = "\\d{6}", message = "Anden kode skal være på præcis 6 cifre") String secondCode) {
	}
	@GetMapping("/admin/konfiguration/offset")
	public String codeoffsetConfiguration(Model model) {
		model.addAttribute("form", new CodeOffsetForm("", "", ""));
		
		return "admin/kodeviser-code-offset";
	}

	@PostMapping("/admin/konfiguration/offset")
	public String postCodeOffsetConfiguration(Model model, @ModelAttribute("form") CodeOffsetForm form, BindingResult bindingResult) {
		// Validate serial number
		if (form.serialnumber == null || form.serialnumber.isBlank()) {
			bindingResult.rejectValue("serialnumber", "error.serialnumber", "Serienummeret skal være udfyldt");
		}
		
		// Validate TOTP codes (exactly 6 digits)
		if (form.firstCode == null || !form.firstCode.matches("\\d{6}")) {
			bindingResult.rejectValue("firstCode", "error.firstCode", "Første kode skal være på præcis 6 cifre");
		}
		if (form.secondCode == null || !form.secondCode.matches("\\d{6}")) {
			bindingResult.rejectValue("secondCode", "error.secondCode", "Anden kode skal være på præcis 6 cifre");
		}
		
		// Check if codes are identical (common user error)
		if (form.firstCode != null && form.firstCode.equals(form.secondCode)) {
			bindingResult.rejectValue("secondCode", "error.secondCode", "Det skal være 2 forskellige koder");
		}
		
		if (bindingResult.hasErrors()) {
			return "admin/kodeviser-code-offset";
		}
		
		try {
			boolean success = mfaManagmentService.adjustTOTPdrift(form.serialnumber, form.firstCode, form.secondCode);
			if (success) {
				model.addAttribute("success", "Kodeviser ur justeret");
			}
			else {
				model.addAttribute("error", "Fejl ved justering - check at serienummer og koder er korrekte");
			}
		}
		catch (Exception ex) {
			log.warn("TOTP adjustment failed for token: {}", form.serialnumber, ex);

			model.addAttribute("error", "Teknisk fejl ved justering af uret");
		}
		
		return "admin/kodeviser-code-offset";
	}

	@GetMapping("/admin/download/kodeviser")
	@ResponseBody
	public ModelAndView downloadKodeviserToExcel(HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> model = new HashMap<>();

		model.put("cachedMfaClientService", cachedMfaClientService);

		return new ModelAndView(new AdminKodeviserReportXlsView(), model);
	}
}
