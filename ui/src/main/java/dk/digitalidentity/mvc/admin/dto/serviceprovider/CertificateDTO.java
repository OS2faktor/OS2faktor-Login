package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.opensaml.security.credential.UsageType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificateDTO {
    private String usageType;
    private String expiryDate;
    private Date expiryDateAsDate;
    private String subject;

    public CertificateDTO(UsageType usageType, X509Certificate certificate) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");

        this.usageType = usageType.toString();
        this.expiryDateAsDate = certificate.getNotAfter();        
        this.expiryDate = simpleDateFormat.format(certificate.getNotAfter());

        X500Principal subjectX500Principal = certificate.getSubjectX500Principal();
        if (subjectX500Principal != null) {
            this.subject = subjectX500Principal.getName();
        }
    }
}
