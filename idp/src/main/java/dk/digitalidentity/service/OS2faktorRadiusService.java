package dk.digitalidentity.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tinyradius.attribute.IpAttribute;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.RadiusClient;
import dk.digitalidentity.common.dao.model.RadiusClientClaim;
import dk.digitalidentity.common.dao.model.RadiusClientCondition;
import dk.digitalidentity.common.dao.model.enums.RadiusClientConditionType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.RadiusClientService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OS2faktorRadiusService {

	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private PasswordService passwordService;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private RadiusClientService radiusClientService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private AdvancedRuleService advancedRuleService;
	
	public String getSharedSecret(InetSocketAddress client) {
		RadiusClient radiusClient = getRadiusClient(client);
		if (radiusClient != null) {
			return radiusClient.getPassword();
		}
		
		// if we cannot find a client, we return a random UUID, which ensures that any random
		// attempt to login will have a "for all practical purposes" 0% chance of triggering a login flow
		return UUID.randomUUID().toString();
	}

	@Transactional
	public RadiusPacket accessRequestReceived(boolean requireMfa, AccessRequest accessRequest, InetSocketAddress client) throws RadiusException {
		String reasonText = "Årsag ukendt";
		RadiusClient radiusClient = getRadiusClient(client);
		int type = RadiusPacket.ACCESS_REJECT;
		Person person = null;

		String username = accessRequest.getUserName();
		String password = accessRequest.getUserPassword();

		auditLogger.radiusLoginRequestReceived(username, (radiusClient != null ? radiusClient.getName() : "<ukendt klient>"));
		
		if (radiusClient != null) {
			// if the radius client have Domain conditions we only fetch persons that are within those domains
			// this allows radius to work in multi-domain setups where SAMAccountName clashes might happen
			// the SAMAccountName will still have to be unique within all the domains chosen as conditions
			List<Domain> domains = Stream.ofNullable(radiusClient.getConditions()).flatMap(Collection::stream)
					.filter(condition -> RadiusClientConditionType.DOMAIN.equals(condition.getType()) && condition.getDomain() != null)
					.map(RadiusClientCondition::getDomain)
					.collect(Collectors.toList());

			List<Person> persons = domains.isEmpty() ? personService.getBySamaccountNameFullyLoaded(username) : personService.getBySamaccountNameAndDomainsFullyLoaded(username, domains);

			if (persons == null || persons.size() == 0) {
				log.warn("Supplied username does not exist '" + username + "'");
				reasonText = "Brugernavn findes ikke: " + username;
			}
			else if (persons.size() > 1) {
				log.warn("Supplied username matches multiple persons '" + username + "' : " + persons.size());
				reasonText = "Flere personer med det angive brugernavn: " + username;
			}
			else {
				person = persons.get(0);
				boolean canBeUsed = false;

				// Validate that the person meets all requirements
				Set<RadiusClientCondition> conditions = radiusClient.getConditions();
				if (conditions == null) {
					// No conditions set, everybody can use this
					canBeUsed = true;
				}
				else {
					// Verify WITH_ATTRIBUTE conditional access
					boolean withAttributeConditionApplies = conditions
							.stream()
							.anyMatch(radiusClientCondition -> radiusClientCondition.getType()
							.equals(RadiusClientConditionType.WITH_ATTRIBUTE));

					boolean withAttributeConditionMet = false;
					if (withAttributeConditionApplies) {
						String radiusAttr = person.getAttributes().get("RADIUS");
						String[] clients = radiusAttr.split(",");

						for (int i = 0; i < clients.length; i++) {
							if (Objects.equals(clients[i], radiusClient.getName())) {
								withAttributeConditionMet = true;
								break;
							}
						}
					}
					else {
						withAttributeConditionMet = true;
					}

					// Verify Domain conditional access, if no Domain Conditions condition is met
					Set<RadiusClientCondition> radiusClientDomains = conditions
							.stream()
							.filter(radiusClientCondition -> radiusClientCondition.getType().equals(RadiusClientConditionType.DOMAIN))
							.collect(Collectors.toSet());

					boolean domainConditionMet = radiusClientDomains.isEmpty() || DomainService.isMember(person, radiusClientDomains.stream().map(RadiusClientCondition::getDomain).collect(Collectors.toList()));

					Set<RadiusClientCondition> radiusClientGroups = conditions
							.stream()
							.filter(radiusClientCondition -> radiusClientCondition.getType().equals(RadiusClientConditionType.GROUP))
							.collect(Collectors.toSet());

					boolean groupConditionMet = (radiusClientGroups.isEmpty() || GroupService.memberOfGroup(person, radiusClientGroups.stream().map(RadiusClientCondition::getGroup).collect(Collectors.toList())));

					canBeUsed = (withAttributeConditionMet && domainConditionMet && groupConditionMet);
				}

				if (canBeUsed) {
					if (radiusClient.getNsisLevelRequired().equalOrLesser(person.getNsisLevel())) {
						if (PasswordValidationResult.VALID.equals(passwordService.validatePassword(password, person))) {
	
							if (!person.isLockedByOtherThanPerson()) {
	
								if (requireMfa) {
									List<MfaAuthenticationResponse> challenges = mfaService.authenticateWithCpr(person.getCpr());
	
									if (challenges == null || challenges.size() == 0) {
										log.warn("No suitable MFA clients found for " + username);
										reasonText = "Brugeren har ikke nogen 2-faktor enheder: " + username;
									}
									else {
										log.info("Challenges send to " + challenges.size() + " MFA clients for " + username);
				
										// wait up to 60 seconds for one of them to respond
										int counter = 0;
										
										while (counter < 60) {
											counter++;
					
											boolean responseReceived = false;
											for (MfaAuthenticationResponse challenge : challenges) {
												MfaAuthenticationResponse result = mfaService.getMfaAuthenticationResponse(challenge.getSubscriptionKey(), person);
	
												if (result == null) {
													; // skip this round and try again
												}
												else if (result.isClientRejected()) {
													responseReceived = true;
													reasonText = "2-faktor enhed afviste login forespørgsel";
												}
												else if (result.isClientAuthenticated()) {
													type = RadiusPacket.ACCESS_ACCEPT;
													responseReceived = true;
												}
												
												if (responseReceived) {
													break;
												}
											}
					
											if (responseReceived) {
												break;
											}
					
											// wait 1 second, and try again
											try {
												Thread.sleep(1000);
											}
											catch (InterruptedException ex) {
												; // ignore
											}
										}
										
										if (counter == 60) {
											log.warn("No response within 60 seconds from " + username);
											reasonText = "Timeout (60 sekunder) for " + username;
										}
									}
								}
								else {
									type = RadiusPacket.ACCESS_ACCEPT;
								}
							}
							else {
								log.info("Person with id '" + person.getId() + "' is locked");
								reasonText = "Brugerkontoen er låst for " + username;
							}
						}
						else {
							log.info("Incorrect password.");
							reasonText = "Forkert kodeord for " + username;
						}
					} else {
						log.info("Insufficient NSIS Level: " + person.getNsisLevel() +  " required " + radiusClient.getNsisLevelRequired() + " for user: " + username);
						reasonText = "Utilstrækkeligt NSIS-niveau";
					}
				}
				else {
					log.warn("Radius client cannot be used by person with id " + person.getId());
					reasonText = "Adgang nægtet (RADIUS konfiguration) for brugernavn " + username;
				}
			}
		}
		else {
			log.error("Unknown radius client");
			reasonText = "Ukendt RADIUS klient";
		}
		
		if (type == RadiusPacket.ACCESS_ACCEPT) {
			auditLogger.radiusLoginRequestAccepted(person);
		}
		else {
			auditLogger.radiusLoginRequestRejected(person, reasonText);
		}
		
		// copy claims if available
		List<RadiusAttribute> attributes = new ArrayList<>();
		for (RadiusClientClaim claim : radiusClient.getClaims()) {
			long attributeType = claim.getAttributeId();
            String attribute = advancedRuleService.lookupField(person, claim.getPersonField());
            
            // TODO: make the type configurable
            if (attribute != null && attributeType > 0) {
            	IpAttribute radiusAttribute = new IpAttribute((int)attributeType, attribute);
            	
                attributes.add(radiusAttribute);
            }
		}
		
		RadiusPacket answer = new RadiusPacket(type, accessRequest.getPacketIdentifier(), attributes);
		copyProxyState(accessRequest, answer);

		return answer;
	}

	private void copyProxyState(RadiusPacket request, RadiusPacket answer) {
		List<RadiusAttribute> proxyStateAttrs = request.getAttributes(33);

		for (Iterator<RadiusAttribute> i = proxyStateAttrs.iterator(); i.hasNext();) {
			RadiusAttribute proxyStateAttr = i.next();
			answer.addAttribute(proxyStateAttr);
		}
	}

	private RadiusClient getRadiusClient(InetSocketAddress client) {
		RadiusClient foundRadiusClient = null;
		InetAddress address = client.getAddress();

		if (address instanceof Inet4Address) {
			String ipAddress = ((Inet4Address) address).getHostAddress();

			String[] inetSplit = ipAddress.split("\\.");
			int inetSegment1 = Integer.parseInt(inetSplit[0]);
			int inetSegment2 = Integer.parseInt(inetSplit[1]);
			int inetSegment3 = Integer.parseInt(inetSplit[2]);
			int inetSegment4 = Integer.parseInt(inetSplit[3]);

			List<RadiusClient> radiusClients = radiusClientService.getAllFullyLoaded();
			for (RadiusClient radiusClient : radiusClients) {
				boolean found = false;

				String[] split = radiusClient.getIpAddress().split("\\.");
				int segment1 = Integer.parseInt(split[0]);
				int segment2 = Integer.parseInt(split[1]);
				int segment3 = Integer.parseInt(split[2]);
				int segment4 = Integer.parseInt(split[3].split("/")[0]);
				int afterIp = Integer.parseInt(split[3].split("/")[1]);

				if (afterIp == 16) {
					if (segment1 == inetSegment1 && segment2 == inetSegment2) {
						found = true;
					}					
				}
				else if (afterIp == 24) {
					if (segment1 == inetSegment1 && segment2 == inetSegment2 && segment3 == inetSegment3) {
						found = true;
					}
				}
				else if (afterIp == 32) {
					if (segment1 == inetSegment1 && segment2 == inetSegment2 && segment3 == inetSegment3 && segment4 == inetSegment4) {
						found = true;
					}
				}
				else {
					log.error("The number efter the / in the ip address for the radius client does not equal 16, 24 or 32 - ip address: " + radiusClient.getIpAddress());

					return null;
				}

				if (found) {
					foundRadiusClient = radiusClient;
					break;
				}
			}
			
			if (foundRadiusClient == null) {
				log.warn("Could not find client with ip address " + ipAddress);
			}
		}
		else {
			log.warn("Client address is not an IPv4 address: " + address.getClass().getCanonicalName());
		}

		return foundRadiusClient;
	}
}
