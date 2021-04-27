package dk.digitalidentity.config;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChromeDriverConfiguration {

    @Value("${chromedriver.bin}")
    private String chromeDriver;

	@SuppressWarnings("deprecation")
	@Bean
	public ChromeDriver devChromeDriver() {
        System.setProperty("webdriver.chrome.driver", chromeDriver);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("window-size=1024,768");

        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
        capabilities.setCapability(ChromeOptions.CAPABILITY, options);
        capabilities.setJavascriptEnabled(true);
        
        return new ChromeDriver(capabilities);
	}
}
