package dk.digitalidentity.util;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

import org.springframework.util.StringUtils;

public class StringResource implements net.shibboleth.utilities.java.support.resource.Resource {

	private String content;
	private String entityId;

	public StringResource(String content, String entityId) {
		this.content = content;
		this.entityId = entityId;
	}

	@Override
	public boolean exists() {
		return !StringUtils.isEmpty(content);
	}

	@Override
	public boolean isReadable() {
		return true;
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public URL getURL() throws IOException {
		return null;
	}

	@Override
	public URI getURI() throws IOException {
		return null;
	}

	@Override
	public File getFile() throws IOException {
		// Create temp file.
		File temp = File.createTempFile(UUID.randomUUID().toString(), ".temp");

		// Delete temp file when program exits.
		temp.deleteOnExit();

		// Write to temp file
		BufferedWriter out = new BufferedWriter(new FileWriter(temp));
		out.write(content);
		out.close();

		return temp;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream(content.getBytes());
	}

	@Override
	public long contentLength() throws IOException {
		return !StringUtils.isEmpty(content) ? content.length() : 0;
	}

	@Override
	public long lastModified() throws IOException {
		return 0;
	}

	@Override
	public net.shibboleth.utilities.java.support.resource.Resource createRelativeResource(String relativePath) throws IOException {
		return null;
	}

	@Override
	public String getFilename() {
		return "spring_saml_metadata.xml";
	}

	@Override
	public String getDescription() {
		return entityId;
	}

}