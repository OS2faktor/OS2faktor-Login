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
package dk.digitalidentity.ooapi.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import dk.digitalidentity.ooapi.exceptions.InternalException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClient {

    public static byte[] doPostOCSPRequest(byte[] bs, String responderURL) throws InternalException {
        try {
            HttpURLConnection conn = postRequest(bs, responderURL);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.warn("http response code from OCSP request was: " + conn.getResponseCode());
                throw new InternalException();
            }

            int len = conn.getContentLength();

            byte[] respData = readResponse(conn, len);
            return respData;
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    public static X509Certificate downloadCertificate(String location) {
		if (location == null) {
			throw new IllegalArgumentException("location is null");
		}
		if (!location.toLowerCase().startsWith("http://")) {
			throw new IllegalArgumentException("location excepted to have the prefix 'http://'");
		}
        InputStream inStream = null;
        try {
            URL url = new URL(location);
            inStream = url.openStream();
            return X509CertificateFactory.getInstance().generateCertificate(inStream);
        } catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (CertificateException e) {
            throw new IllegalStateException(e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // Ignore in silence
                }
            }
        }
    }

    private static HttpURLConnection postRequest(byte[] bs, String responderURL) throws IOException {
        HttpURLConnection conn = setupHttpConnectionForPost(bs.length, responderURL);
        OutputStream os = null;
        try {
            os = conn.getOutputStream();
            os.write(bs);
            conn.connect();
            return conn;
        } finally {
            if (os != null) {
                os.close();
            }
        }
    }

    private static byte[] readResponse(HttpURLConnection conn, int len) throws IOException {
        InputStream is = null;
        try {
            is = conn.getInputStream();
            byte[] respData = new byte[len];
            int bRead = 0;

            do {
                bRead += is.read(respData, bRead, Math.min(1024, len - bRead));
            } while (bRead != len);

            return respData;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } finally {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection setupHttpConnectionForPost(int contentLength, String responderURL) throws IOException {
        HttpURLConnection conn;
        conn = (HttpURLConnection) new URL(responderURL).openConnection();
        conn.setAllowUserInteraction(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Length", "" + contentLength);
        conn.setRequestProperty("Content-Type", "application/ocsp-request");
        return conn;
    }
}
