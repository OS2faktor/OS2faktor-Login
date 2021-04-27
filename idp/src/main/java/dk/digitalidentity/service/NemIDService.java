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

		

		return new PidAndCprOrError(handleErrorCode(result));
	}
	
	private String handleErrorCode(String errorCode) {
		
		/*
		 * Translating error codes
		 * via https://www.nets.eu/dk-da/kundeservice/nemid-tjenesteudbyder/Documents/TU-pakken/Java/Dokumentation/implementeringsvejledning_for_nemid.pdf
		 * 3.7.4: Anbefaling til tekster for bruger-rettede fejlkoder
		 */
		String result = "";
		switch (errorCode) {
			case "CAN001":
				result = errorCode + ": " + "Login blev afbrudt i forbindelse med brug af midlertidig adgangskode";
				log.info("NemID validation failed: " + result);
				break;
			case "CAN002":
				result = errorCode + ": " + "Login blev afbrudt";
				log.warn("NemID validation failed: " + result);
				break;
			case "LOCK001":
				result = errorCode + ": " + "Du har angivet forkert bruger-id eller adgangskode 5 gange i træk."
						+ " NemID er nu spærret i 8 timer, hvorefter du igen vil have 5 forsøg."
						+ " Du kan logge på igen efter udløbet af den 8 timers spærreperiode."
						+ " Du kan ophæve spærringen tidligere ved at kontakte NemIDsupport på tlf. 80 30 70 50";
				log.error("NemID validation failed: " + result);
				break;
			case "LOCK002":
				result = errorCode + ": " + "NemID er spærret og kan ikke bruges."
						+ " For at få hjælp til dette, kan du kontakte NemID-support på tlf. 80 30 70 50";
				log.warn("NemID validation failed: " + result);
				break;
			case "LOCK003":
				result = errorCode + ": " + "NemID er spærret og kan ikke bruges."
						+ " For at få hjælp til dette, kan du kontakte NemID-support på tlf." + "80 30 70 50";
				log.warn("NemID validation failed: " + result);
				break;
			case "OCES001":
				result = errorCode + ": " + "Du har kun sagt ja til at bruge NemID til netbank. Ønsker du at"
						+ " bruge NemID til andre hjemmesider, skal du først tilknytte en"
						+ " offentlig digital signatur (OCES) til dit NemID."
						+ " [https://www.nemid.nu/privat/bestil_nemid/nemid_i_netbank/]";
				log.warn("NemID validation failed: " + result);
				break;
			case "OCES002":
				result = errorCode + ": " + "Ønsker du at bruge NemID til andet end netbank, skal du først"
						+ " tilknytte en offentlig digital signatur. Det gør du nemt og hurtigt"
						+ " ved at starte en ny bestilling af NemID på www.nemid.nu"
						+ " hvorved du får mulighed for at tilknytte en offentlig digital signatur."
						+ " [https://www.nemid.nu/privat/bestil_nemid/]";
				log.info("NemID validation failed: " + result);
				break;
			case "OCES003":
				result = errorCode + ": " + "Der er ikke knyttet en digital signatur til det NemID, du har"
						+ " forsøgt at logge på med."
						+ " Hvis du plejer at logge på [TU] med NemID, kan problemet skyldes, at du har flere forskellige NemID og at du nu har brugt"
						+ " et andet NemID, end du plejer.";
				log.info("NemID validation failed: " + result);
				break;
			case "OCES004":
				result = errorCode + ": " + "Du kan kun bruge NemID til netbank. ";
				log.info("NemID validation failed: " + result);
				break;
			case "OCES005":
				result = errorCode + ": " + "Der opstod en fejl under oprettelse af OCES certifikat til"
						+ " NemID Prøv at logge på igen.";
				log.info("NemID validation failed: " + result);
				break;
			case "OCES006":
				result = errorCode + ": " + "Du har ikke i øjeblikket ikke en aktiv offentlig digital signatur"
						+ " (OCES-certifikat) til NemID. Det kan du få ved at starte en"
						+ " bestilling af NemID, hvorved du vil få mulighed for at vælge at"
						+ " bestille og tilknytte en offentlig digital signatur til dit nuværende NemID."
						+ " [https://www.nemid.nu/privat/bestil_nemid/]";
				log.info("NemID validation failed: " + result);
				break;
			case "SRV006":
				result = errorCode + ": " + "Tidsgrænse overskredet. Forsøg igen";
				log.warn("NemID validation failed: " + result);
				break;
			case "APP001":
			case "APP002":
			case "APP004":
			case "SRV001":
			case "SRV002":
			case "SRV003":
			case "SRV005":
				result = errorCode + ": " + "Der er opstået en teknisk fejl. Forsøg igen.";
				log.error("NemID validation failed: " + result);
				break;
			case "APP003":
			case "SRV004":
				result = errorCode + ": " + "Der er opstået en teknisk fejl." + "Kontakt NemID-support på tlf. 80 30 70 50";
				log.error("NemID validation failed: " + result);
				break;
			case "APP005":
				result = errorCode + ": " + "Du skal godkende Nets DanIDs certifikat, før du kan logge på"
						+ " med NemID. Genstart din browser og godkend certifikatet næste gang du"
						+ " bliver spurgt. Har du brug for hjælp, kan du kontakte NemID-support på tlf."
						+ " 80 30 70 50";
				log.info("NemID validation failed: " + result);
				break;
			case "AUTH001":
				result = errorCode + ": " + "Din NemID er spærret. Kontakt venligst NemID-support på tlf. 80 30 70 50.";
				log.warn("NemID validation failed: " + result);
				break;
			case "AUTH004":
				result = errorCode + ": " + "Dit NemID er midlertidigt låst og du kan endnu ikke logge på. Du kan logge på igen når den 8 timers tidslås er ophævet.";
				log.warn("NemID validation failed: " + result);
				break;
			case "AUTH005":
				result = errorCode + ": " + "Dit NemID er spærret. Kontakt venligst NemID-support på tlf. 80 30 70 50.";
				log.warn("NemID validation failed: " + result);
				break;
			case "AUTH006":
			case "AUTH007":
			case "AUTH008":
				result = errorCode + ": " + " Kontakt NemID-support på tlf. 80 30 70 50";
				log.error("NemID validation failed: " + errorCode);
				break;
			default:
				result = errorCode;
				log.error("NemID validation failed: " + result);
				break;
		}
		
		return result;
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
