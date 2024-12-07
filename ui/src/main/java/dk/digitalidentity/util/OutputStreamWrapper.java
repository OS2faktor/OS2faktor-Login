package dk.digitalidentity.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import lombok.Getter;
import lombok.Setter;

// hack-class, used by HttpServletResponseOutputStreamWrapper

@Getter
@Setter
public class OutputStreamWrapper extends ServletOutputStream {
	private ByteArrayOutputStream byteArrayOutputStream;
	private WriteListener writeListener;
	private boolean ready;
	
	public OutputStreamWrapper() {
		this.byteArrayOutputStream = new ByteArrayOutputStream();
		this.ready = false;
	}

	@Override
	public boolean isReady() {
		return ready;
	}

	@Override
	public void setWriteListener(WriteListener listener) {
		this.writeListener = listener;
	}

	@Override
	public void write(int b) throws IOException {
		this.ready = false;
		byteArrayOutputStream.write(b);
		this.ready = true;
	}
}
