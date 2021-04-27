package dk.digitalidentity.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.util.ChromeDriverUtil;
import dk.digitalidentity.util.DatabaseUtil;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoginTest {
	private static final String SELF_SERVICE_URL = "https://localhost:8808";

	@Qualifier("DevRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private DatabaseUtil databaseUtil;
	
	@Autowired
	private ChromeDriver driver;
	
	// Runs before each test
	@Before
	public void before() {
		// this performs a clean flyway migration
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/db/clean", String.class);

		// make sure test data is available
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/init", String.class);

		// Make sure clients are available for the created user
		databaseUtil.resetClients();
	}
	
	// Runs after each test
	@After
	public void after() {

	}
	
	@Test
	@DisplayName("Test normal login with NSIS password with no previous session")
	@Order(1)
	public void login() {
		// Set password on user 'bsg' and accept terms and conditions
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setPassword", String.class);
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setNSISAllowed", String.class);
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setApprovedConditions", String.class);

		// Access self service
		driver.get(SELF_SERVICE_URL + "/");
		
		// click login
		WebElement loginButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/login");
		loginButton.click();
		
		// select "erhvervsidentitet" tab
		WebElement tab = ChromeDriverUtil.findAnchorTagWithHref(driver, "#tab-1");
		tab.click();
		
		// fill out form
		driver.findElementById("username").sendKeys("bsg");
		driver.findElementById("password").sendKeys("Test123456");
		
		// click login
		WebElement button = ChromeDriverUtil.findButtonWithText(driver, "Login");
		button.click();
		
		// this will cause a MFA challenge, which we will "cheat" and accept through the DB
		databaseUtil.acceptNotifications();

		// wait 2 seconds, so the MFA challenge accept is detected by javascript
		ChromeDriverUtil.sleep(2000);
		
		// verify login succeeded
		WebElement logoutButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/logout");
		assertNotNull(logoutButton);
	}

	@Test
	@DisplayName("Test normal login with NSIS password with previous session missing MFA level")
	@Order(2)
	public void stepUpLogin() {
		// Setup. Will result in logged in with NSIS Substantial
		login();

		// Delete 2FA so step up is required
		databaseUtil.deleteMFALevel();

		// Provoke login
		driver.get(SELF_SERVICE_URL + "/saml/login");

		// Check if we skipped Password login
		String currentUrl = driver.getCurrentUrl();
		assertTrue(currentUrl != null && currentUrl.endsWith("/sso/saml/mfa/123-123-123-123"));
	}

	@Test
	@DisplayName("Test that a non nsis user does not see the NSIS activation prompt")
	@Order(3)
	public void adPasswordLoginNonNSISAccount() {
		// Remove nsis user, set adPassword to test activation
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setAdPassword", String.class);

		// Access self service
		driver.get(SELF_SERVICE_URL + "/");

		// click login
		WebElement loginButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/login");
		loginButton.click();

		// select "erhvervsidentitet" tab
		WebElement tab = ChromeDriverUtil.findAnchorTagWithHref(driver, "#tab-1");
		tab.click();

		// fill out form (NonNsisUser)
		driver.findElementById("username").sendKeys("nnu");
		driver.findElementById("password").sendKeys("Test123456");

		// click login
		WebElement button = ChromeDriverUtil.findButtonWithText(driver, "Login");
		button.click();

		// Look for checkbox
		ChromeDriverUtil.sleep(2000);
		WebElement checkboxAccept = driver.findElementByClassName("iCheck-helper");
		checkboxAccept.click();

		// Accept terms
		WebElement buttonAccept = driver.findElementById("buttonAccept");
		assertNotNull(buttonAccept);
		buttonAccept.click();

		// this will cause a MFA challenge, which we will "cheat" and accept through the DB
		databaseUtil.acceptNotifications();

		// wait 2 seconds, so the MFA challenge accept is detected by javascript
		ChromeDriverUtil.sleep(2000);

		// verify login succeeded
		WebElement logoutButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/logout");
		assertNotNull(logoutButton);
	}
}
