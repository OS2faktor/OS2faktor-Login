package dk.digitalidentity.rest.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.service.CmsMessageBundle;
import dk.digitalidentity.security.RequireAdministrator;
import lombok.extern.slf4j.Slf4j;

@RequireAdministrator
@RestController
@Slf4j
public class CMSRestController {

	@Autowired
	private CmsMessageBundle cmsMessageBundle;

	@DeleteMapping("/rest/admin/cms/reset")
	@ResponseBody
	public ResponseEntity<?> resetText(@RequestParam String key) {
		try {
			cmsMessageBundle.resetText(key);
			
			return new ResponseEntity<>(HttpStatus.OK);
		}
		catch (Exception ex) {
			log.warn("Failed to reset CMS key: {}", key, ex);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
