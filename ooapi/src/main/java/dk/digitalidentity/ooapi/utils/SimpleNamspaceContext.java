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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;

public class SimpleNamspaceContext implements NamespaceContext {
	private Map<String, String> map = new HashMap<String, String>();
	
	public SimpleNamspaceContext() {
		map.put("ds", org.apache.xml.security.utils.Constants.SignatureSpecNS);
		map.put("openoces", "http://www.openoces.org/2006/07/signature#");
	}
	
	public String getNamespaceURI(String prefix) {
		return map.get(prefix);
	}

	public String getPrefix(String namespaceURI) {
		for (String k : map.keySet()) {
			if (namespaceURI.equals(map.get(k))) {
				return k;
			}
		}
		return null;
	}

	@Override
	public Iterator<String> getPrefixes(String s) {
		return map.keySet().iterator();
	}
}
