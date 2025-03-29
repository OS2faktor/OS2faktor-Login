package dk.digitalidentity.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.service.BadPasswordService;

@Controller
public class BadPasswordController {

	@Autowired
	private BadPasswordService badPasswordService;

	@GetMapping("/badPasswords")
	public String badPasswordsPage(Model model) {
		model.addAttribute("badPasswords", badPasswordService.getAll());

		return "bad-passwords";
	}

}