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
package dk.digitalidentity.ooapi.validation;

public class ErrorCodeChecker {
	
	public static enum ErrorCodes {
			APP001,APP002,
			APP003,APP004,APP005,APP007,APP008, APP009,APP010,
			SRV001,SRV002,SRV003,SRV004,SRV005,SRV006,SRV007,SRV008,SRV009,SRV010,
			CAN001,CAN002,CAN003,CAN004,
			AUTH001,AUTH003,AUTH004,AUTH005,AUTH006,AUTH007,AUTH008,AUTH009,AUTH010,AUTH011,AUTH012,AUTH013,AUTH014,AUTH015,AUTH016,AUTH017,
			LOCK001,LOCK002,LOCK003,
			OCES001,OCES002,OCES003,OCES004,OCES005,OCES006
	}

	public static boolean isError(String text) {
		if (text == null) {
			throw new IllegalArgumentException("text cannot be null");
		}
		
		for (int i = 0; i < ErrorCodes.values().length; i++) {
			if (ErrorCodes.values()[i].toString().equalsIgnoreCase(text.trim())) {
				return true;
			}
		} 
		return false;
	}

	public static String extractError(String text) {
		return isError(text) ? ErrorCodes.valueOf(text.trim()).toString() : null;			
	}

	private boolean hasError;
	private String error;
		
	public ErrorCodeChecker(String text) {
		hasError = isError(text);
		error = extractError(text);
	}
	
	public boolean hasError() {
		return hasError;
	}
	
	public String extractError() {
		return error;
	}
}
