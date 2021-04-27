package dk.digitalidentity.util;

import java.util.List;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class ChromeDriverUtil {

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (Exception ex) {
			;
		}
	}

	public static WebElement findButtonWithText(ChromeDriver driver, String text) {
		List<WebElement> buttons = driver.findElementsByTagName("button");
		for (WebElement button : buttons) {
			String textValue = button.getText();
			if (text.equals(textValue)) {
				return button;
			}
		}
		
		return null;
	}

	public static WebElement findAnchorTagWithHref(ChromeDriver driver, String href) {
		List<WebElement> anchorTags = driver.findElementsByTagName("a");
		
		for (WebElement anchorTag : anchorTags) {
			String hrefValue = anchorTag.getAttribute("href");
			
			if (hrefValue.endsWith(href)) {
				return anchorTag;
			}
		}
		
		return null;
	}
}
