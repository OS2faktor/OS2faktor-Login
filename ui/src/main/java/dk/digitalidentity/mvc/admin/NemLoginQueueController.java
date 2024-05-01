package dk.digitalidentity.mvc.admin;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.service.MitIdErhvervAccountErrorService;
import dk.digitalidentity.common.service.NemloginQueueService;
import dk.digitalidentity.security.RequireSupporter;

@RequireSupporter
@Controller
public class NemLoginQueueController {

	@Autowired
	private NemloginQueueService nemloginQueueService;

	@Autowired
	private MitIdErhvervAccountErrorService mitIdErhvervAccountErrorService;

	@GetMapping("/admin/nemlogin_queue")
	public String getNemLoginQueue(Model model) {
		List<NemloginQueue> nemloginQueueList = nemloginQueueService.getAll().stream()
			.filter(x -> x.isFailed())
			.collect(Collectors.toList());

		model.addAttribute("failedNemLoginQueue", nemloginQueueList);

		List<MitIdErhvervAccountError> allErrors = mitIdErhvervAccountErrorService.getAll();
		model.addAttribute("mitIdErrors", allErrors);

		return "admin/nemlogin_queue";
	}
}