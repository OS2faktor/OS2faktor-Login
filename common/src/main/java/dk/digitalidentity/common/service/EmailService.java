package dk.digitalidentity.common.service;

import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.PreencodedMimeBodyPart;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

	@Autowired
	private CommonConfiguration configuration;

	public boolean sendMessage(String email, String subject, String message) {
		return sendMessage(email, subject, message, null);
	}

	public boolean sendMessage(String email, String subject, String message, List<InlineImageDTO> inlineImages) {
		if (!configuration.getEmail().isEnabled()) {
			log.warn("email server is not configured - not sending email to " + email);
			return false;
		}

		Transport transport = null;

		log.info("Sending email: '" + subject + "' to " + email);

		try {
			Properties props = System.getProperties();
			props.put("mail.transport.protocol", "smtps");
			props.put("mail.smtp.port", 25);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.starttls.required", "true");
			Session session = Session.getDefaultInstance(props);

			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(configuration.getEmail().getFrom(), configuration.getEmail().getFromName()));
			msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
			msg.setSubject(subject, "UTF-8");
			msg.setHeader("Content-Type", "text/html; charset=UTF-8");

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setContent(message, "text/html; charset=UTF-8");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			// adds inline image attachments
			if (inlineImages != null && inlineImages.size() > 0) {
				for (InlineImageDTO inlineImageDTO : inlineImages) {
					if (inlineImageDTO.isBase64()) {
						MimeBodyPart imagePart = new PreencodedMimeBodyPart("base64");
						String src = inlineImageDTO.getSrc();
						String dataType = StringUtils.substringBetween(src, "data:", ";base64,");
						String base64EncodedFileContent = src.replaceFirst("data:.*;base64,", "");

						imagePart.setContent(base64EncodedFileContent, dataType);
						imagePart.setFileName(inlineImageDTO.getCid());
						imagePart.setHeader("Content-ID", "<" + inlineImageDTO.getCid() + ">");
						imagePart.setDisposition(MimeBodyPart.INLINE);
						imagePart.setDisposition(Part.ATTACHMENT);

						multipart.addBodyPart(imagePart);
					}
				}
			}

			msg.setContent(multipart);

			transport = session.getTransport();
			transport.connect(configuration.getEmail().getHost(), configuration.getEmail().getUsername(), configuration.getEmail().getPassword());
			transport.addTransportListener(new TransportErrorHandler());
			transport.sendMessage(msg, msg.getAllRecipients());
		}
		catch (Exception ex) {
			log.error("Failed to send email", ex);

			return false;
		}
		finally {
			try {
				if (transport != null) {
					transport.close();
				}
			}
			catch (Exception ex) {
				log.warn("Error occured while trying to terminate connection", ex);
			}
		}

		return true;
	}
}
