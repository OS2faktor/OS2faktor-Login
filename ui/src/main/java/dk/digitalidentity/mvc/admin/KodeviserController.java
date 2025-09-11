package dk.digitalidentity.mvc.admin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.CachedMfaClientService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.mvc.admin.xlsview.AdminKodeviserReportXlsView;
import dk.digitalidentity.security.RequireKodeviserAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequireKodeviserAdministrator
@Controller
public class KodeviserController {

	@Autowired
	private CachedMfaClientService cachedMfaClientService;
	
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

	@GetMapping("/admin/download/kodeviser")
	@ResponseBody
	public ModelAndView downloadKodeviserToExcel(HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> model = new HashMap<>();

		model.put("cachedMfaClientService", cachedMfaClientService);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"kodeviser.xlsx\"");

		return new ModelAndView(new AdminKodeviserReportXlsView(), model);
	}
}
