package dk.digitalidentity.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class IndexController {

	@Autowired
	private SessionHelper sessionHelper;
	
	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("person", sessionHelper.getPerson());
		model.addAttribute("nsis", sessionHelper.getLoginState());
		model.addAttribute("mfa", (NSISLevel.LOW.equalOrLesser(sessionHelper.getMFALevel())) ? "Ja" : "Nej");

		List<String> sps = new ArrayList<>();
		for (String key : sessionHelper.getServiceProviderSessions().keySet()) {
			try {
				ServiceProvider sp = serviceProviderFactory.getServiceProvider(key);
				if (sp != null) {
					sps.add(sp.getName(null));
				}
			}
			catch (Exception ex) {
				log.warn("Could not extract name for SP with key " + key, ex);
			}
		}

		model.addAttribute("sps", sps);
		
		return "index";
	}
}
