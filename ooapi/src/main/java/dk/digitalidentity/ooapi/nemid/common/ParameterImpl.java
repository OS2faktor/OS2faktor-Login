/**
 * Copyright (c) 2010, DanID A/S
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  - Neither the name of the DanID A/S nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package dk.digitalidentity.ooapi.nemid.common;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

public class ParameterImpl implements Parameter {
	private String name;
	private String value;

	public ParameterImpl(String name, String value) {
		this(name, value, false);
	}

	public ParameterImpl(String name, String value, boolean base64Encode) {
		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("Parameter name must not be null or empty!");
		}

		if (value == null) {
			throw new IllegalArgumentException("Parameter " + name + " value is null!");
		}

		this.name = name;

		try {
			this.value = base64Encode ? new String(Base64.encodeBase64(value.getBytes("UTF-8"))) : value;
		}
		catch (UnsupportedEncodingException e) {
			this.value = new String(Base64.encodeBase64(value.getBytes()));
		}
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "ParameterImpl [name=" + name + ", value=" + value + "]";
	}

	/**
	 * Normalizes the parameters using this algorithm:
	 *
	 * 1. The parameters are sorted alphabetically by their name attribute. The
	 * sorting is case-insensitive.
	 *
	 * 2. Each parameter is concatenated to the result string as a sequence of
	 * name and value pairs, i.e.: (name1) || value1 || (name2) || value2 ||
	 * (name n) || value n
	 *
	 * @param params
	 *
	 * @return A normalized string of parameters name and value pairs
	 */
	public static String normalize(Collection<Parameter> params) {
		SortedMap<String, String> sortedMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		StringBuilder sb = new StringBuilder();

		for (Parameter parameter : params) {
			sortedMap.put(parameter.getName(), parameter.getValue());
		}

		for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
			// Exclude the Digest and SignedDigest parameters from the
			// normalized parameter string
			if (entry.getKey().equalsIgnoreCase("Params_Digest") || entry.getKey().equalsIgnoreCase("Digest_Signature")) {
				continue;
			}
			sb.append(entry.getKey()).append(entry.getValue());
		}

		return sb.toString();
	}

	public static String computeDigest(String normalizedParameters) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(normalizedParameters.getBytes("UTF-8"));
			return new String(Base64.encodeBase64(hash));
		}
		catch (Exception e) {
			throw new RuntimeException("Unable to compute digest!", e);
		}
	}

	public static JSONObject toJson(Collection<Parameter> params) {
		JSONObject json = new JSONObject();
		for (Parameter p : params) {
			try {
				json.put(p.getName(), p.getValue());
			}
			catch (JSONException e) {
				e.printStackTrace();
				throw new RuntimeException("Unable to create JSON");
			}
		}
		
		return json;
	}
}
