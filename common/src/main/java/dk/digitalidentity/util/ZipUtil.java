package dk.digitalidentity.util;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

public class ZipUtil {

	public static byte[] compress(byte[] in) throws Exception {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			try (DeflaterOutputStream defl = new DeflaterOutputStream(out, new Deflater(Deflater.DEFLATED, true))) {
				defl.write(in);
				defl.finish();
		
				return out.toByteArray();
			}
		}
	}

	public static byte[] decompress(byte[] in) throws Exception {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			try (InflaterOutputStream infl = new InflaterOutputStream(out, new Inflater(true))) {
				infl.write(in);
				infl.flush();
		
				return out.toByteArray();
			}
		}
	}
}
