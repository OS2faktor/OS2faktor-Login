package dk.digitalidentity.service;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import dk.digitalidentity.nemid.config.NemIdConfiguration;
import dk.digitalidentity.nemid.service.Pid2Cpr;
import dk.digitalidentity.ooapi.certificate.CertificateStatus;
import dk.digitalidentity.ooapi.certificate.PocesCertificate;
import dk.digitalidentity.ooapi.nemid.common.ChallengeGenerator;
import dk.digitalidentity.ooapi.nemid.common.OtpClientGenerator;
import dk.digitalidentity.ooapi.securitypackage.LogonHandler;
import dk.digitalidentity.ooapi.serviceprovider.CertificateAndStatus;
import dk.digitalidentity.service.model.PidAndCprOrError;
import lombok.extern.slf4j.Slf4j;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;

@Slf4j
@Service
public class NemIDService {

	@Autowired
	private Pid2Cpr pid2Cpr;
	
	@Autowired
	private NemIdConfiguration configuration;
	
	@Autowired
	private OtpClientGenerator otpClientGenerator;
	
	@Autowired
	private PersonService personService;

	public void populateModel(Model model, HttpServletRequest request) {
		try {
			String origin = configuration.getOrigin();
			if (StringUtils.isEmpty(origin)) {
				origin = getOrigin(request);
			}
	
			model.addAttribute("jsElement", otpClientGenerator.getJSElement(request, origin));
			model.addAttribute("serverUrlPrefix", configuration.getServerUrlPrefix());
	
			StringBuffer sb = new StringBuffer();
			sb.append(configuration.getServerUrlPrefix());
			sb.append("/launcher/lmt/");
			sb.append(System.currentTimeMillis());
	
			model.addAttribute("iframeSrc", sb.toString());
		}
		catch (Exception ex) {
			log.error("Failed to populate NemID model", ex);
		}
	}

	public PidAndCprOrError verify(String responseB64, HttpServletRequest request) {
		String signature = null;
		String result = null;

		if (responseB64 != null && responseB64.length() > 0) {
			String decodedResponse = new String(Base64.getDecoder().decode(responseB64), Charset.forName("UTF-8"));

			if (decodedResponse.length() > 20) {
				result = "ok";
				signature = decodedResponse;
			}
			else {
				result = decodedResponse;
			}
		}

		if ("ok".equals(result)) {
			try {
				HttpSession httpSession = request.getSession();

				String challenge = ChallengeGenerator.getChallenge(httpSession);
				String serviceProviderName = configuration.getServiceProviderId();

				CertificateAndStatus certificateAndStatus = LogonHandler.validateAndExtractCertificateAndStatus(signature, challenge, serviceProviderName);

				if (certificateAndStatus.getCertificateStatus() != CertificateStatus.VALID) {
					return new PidAndCprOrError("Certificate status is: " + certificateAndStatus.getCertificateStatus());
				}
				else if (certificateAndStatus.getCertificate() instanceof PocesCertificate) {
					PocesCertificate pocesCert = ((PocesCertificate) certificateAndStatus.getCertificate());
					String pid = pocesCert.getPid();
					String cpr;
					List<Person> person = personService.getByNemIdPid(pid);
					if (person != null && person.size() > 0) {
						cpr = person.get(0).getCpr();
					}
					else {
						cpr = pid2Cpr.lookup(pid);
					}

					if (cpr == null || cpr.length() == 0) {
						return new PidAndCprOrError("Unable to get CPR for pid: " + pid);
					}

					return new PidAndCprOrError(pid, cpr);
				}

				return new PidAndCprOrError("Certificate is not FOCES");
			}
			catch (Exception ex) {
				log.error("Failure during NemID validation", ex);

				return new PidAndCprOrError(ex.getMessage());
			}
		}

		log.error("NemID validation failed: " + result);

		return new PidAndCprOrError(result);
	}

	private String getOrigin(HttpServletRequest request) {
		String domain = request.getHeader("host");
		String protocol = request.getHeader("x-forwarded-proto");

		// if we are behind a load balancer do this
		if (domain != null && protocol != null) {
			return protocol + "://" + domain;
		}

		// otherwise just use the request data
		return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
	}
}
