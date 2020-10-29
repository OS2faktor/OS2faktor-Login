/*
    Copyright 2010 Nets DanID

    This file is part of OpenOcesAPI.

    OpenOcesAPI is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    OpenOcesAPI is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with OpenOcesAPI; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


    Note to developers:
    If you add code to this file, please take a minute to add an additional
    @author statement below.
 */

package dk.digitalidentity.ooapi.web;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;

import javax.mail.internet.MimeUtility;

import org.springframework.util.ResourceUtils;

public class Signer {
    private final String keystorePath;
    private final String keystorePwd;
    private final String keyAlias;
    private final String certificate;
    private final KeyStore keyStore;

    public Signer(String keystorePath, String keystorePwd) {
        this.keystorePath = keystorePath;
        this.keystorePwd = keystorePwd;
        this.keyStore = loadKeyStore();
        
        try {
    		this.keyAlias = this.keyStore.aliases().nextElement();
            this.certificate = loadCertificate(this.keyAlias);
        }
        catch (Exception ex) {
        	throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    public String getCertificate() {
        return certificate;
    }

    private String loadCertificate(String keyAlias) {
        try {
            Certificate certificateFromKeyStore = keyStore.getCertificate(keyAlias);
            byte[] encodedCertificate = certificateFromKeyStore.getEncoded();
            String base64EncodedCertificate = base64Encode(encodedCertificate);
            return base64EncodedCertificate.replace("\r", "").replace("\n", "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] calculateSignature(byte[] data) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(keyAlias, keystorePwd.toCharArray());
            Signature signer = Signature.getInstance("SHA256withRSA");

            signer.initSign(key);
            signer.update(data);

            return signer.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

	private KeyStore loadKeyStore() {
		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			File key = ResourceUtils.getFile(keystorePath);
	
			try (InputStream in = new FileInputStream(key)) {
				ks.load(in, keystorePwd.toCharArray());
			}
	
			return ks;
		}
		catch (Exception ex) {
            throw new RuntimeException(ex);
		}
	}

    private String base64Encode(byte[] b) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // TODO: this is the only thing we use javax.mail dependency for - can we not find some other
        //       way to do this?
        OutputStream b64os = MimeUtility.encode(baos, "base64");
        b64os.write(b);
        b64os.close();
        return new String(baos.toByteArray(), "US-ASCII");
    }
}
