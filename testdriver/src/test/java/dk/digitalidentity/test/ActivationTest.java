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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ActivationTest {
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
	@DisplayName("Test that the user is prompted to accept terms and conditions")
	@Order(1)
	public void acceptTermsAndConditions() {
		// Set AD Password
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setAdPassword", String.class);

		// Allow NSIS user activation
		restTemplate.getForEntity(SELF_SERVICE_URL + "/bootstrap/users/setNSISAllowed", String.class);

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

		// Look for checkbox
		ChromeDriverUtil.sleep(2000);
		WebElement checkboxAccept = driver.findElementByClassName("iCheck-helper");
		checkboxAccept.click();

		// Accept terms
		WebElement buttonAccept = driver.findElementById("buttonAccept");
		assertNotNull(buttonAccept);
		buttonAccept.click();

		// Check we are prompted to activate NSIS user
		assertTrue(driver.getCurrentUrl().endsWith("/vilkaar/godkendt"));
	}

	@Test
	@DisplayName("Test that the user can complete a successful login to selfsevice if they decline creating an nsis user")
	@Order(2)
	public void adPasswordLoginNSISPromptNo() {
		acceptTermsAndConditions();

		// Decline creating NSIS user
		WebElement continueLogin = ChromeDriverUtil.findAnchorTagWithHref(driver, "/konto/fortsaetlogin");
		new WebDriverWait(driver, 60).until(ExpectedConditions.elementToBeClickable(continueLogin)).click();

		// this will cause a MFA challenge, which we will "cheat" and accept through the DB
		databaseUtil.acceptNotifications();

		// wait 2 seconds, so the MFA challenge accept is detected by javascript
		ChromeDriverUtil.sleep(2000);

		// verify login succeeded
		WebElement logoutButton = ChromeDriverUtil.findAnchorTagWithHref(driver, "/saml/logout");
		assertNotNull(logoutButton);
	}

	@Test
	@DisplayName("Test that the user is prompted for NemId when accepting NSIS user activation prompt")
	@Order(3)
	public void adPasswordLoginNSISPromptYes() {
		acceptTermsAndConditions();

		// Decline creating NSIS user
		WebElement continueLogin = ChromeDriverUtil.findAnchorTagWithHref(driver, "/konto/aktiver");
		new WebDriverWait(driver, 60).until(ExpectedConditions.elementToBeClickable(continueLogin)).click();

		// Check if user is promted for NemID, we cannot test nemid so test ends here
		List<WebElement> elementsByClassName = driver.findElementsByClassName("panel-heading");
		new WebDriverWait(driver, 60).until(ExpectedConditions.visibilityOf(elementsByClassName.get(0)));
		Optional<WebElement> infoBox = elementsByClassName.stream().filter(webElement -> webElement.getText().endsWith("Yderligere verifikation krævet")).findAny();

		assertTrue(infoBox.isPresent());
	}
}
