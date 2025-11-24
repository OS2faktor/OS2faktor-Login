package dk.digitalidentity.mvc.admin.xlsview;

import java.io.IOException;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;

// this class makes sure to call dispose() as part of being closed, so we do not leave temporary files
public class DisposableSXSSFWorkbook extends SXSSFWorkbook {

	public DisposableSXSSFWorkbook() {
		super();
	}

	@Override
	public void close() throws IOException {
		try {
			dispose();
		}
		catch (Exception _) {
			; // ignore failed dispose()
		}
		finally {
			try {
				super.close();
			} catch (Exception _) {
				// ignore failed close();
			}
		}
	}
}
