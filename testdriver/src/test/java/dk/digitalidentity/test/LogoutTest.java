package dk.digitalidentity.test;

import dk.digitalidentity.util.ChromeDriverUtil;
import dk.digitalidentity.util.DatabaseUtil;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LogoutTest {
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
	@DisplayName("Test that a logout works, and sends user back to selfservice page")
	@Order(1)
	public void Logout() {
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
		logoutButton.click();

		// Assert that the logout put us on SelfService frontpage
		assertTrue(driver.getCurrentUrl().startsWith(SELF_SERVICE_URL));
		loginButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/login");
		assertNotNull(loginButton);
	}
}
