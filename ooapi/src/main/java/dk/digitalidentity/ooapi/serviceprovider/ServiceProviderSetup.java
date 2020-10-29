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
package dk.digitalidentity.ooapi.serviceprovider;

import dk.digitalidentity.ooapi.environment.Environments;
import dk.digitalidentity.ooapi.environment.Environments.Environment;
import dk.digitalidentity.ooapi.validation.PartitionedCrlRevocationChecker;
import dk.digitalidentity.ooapi.validation.RevocationChecker;

public class ServiceProviderSetup {
	private static RevocationChecker currentChecker = PartitionedCrlRevocationChecker.getInstance();

	static {
		resetSettings();
	}

	public static void resetSettings() {
		currentChecker = PartitionedCrlRevocationChecker.getInstance();
	}
	
	public static void setPartitionedCrlRevocationChecker() {
		currentChecker = PartitionedCrlRevocationChecker.getInstance();
	}

	public static RevocationChecker getCurrentChecker() {
		return currentChecker;
	}

    public static void setEnvironmentToOcesIiProduction() {
        Environments.setEnvironments(Environment.OCESII_DANID_ENV_PROD);
    }
    
    public static void setEnvironmentToOcesIiPP() {
    	Environments.setEnvironments(Environment.OCESII_DANID_ENV_PREPROD);
    }

    public static void setEnvironmentToOcesIiExternalTest() {
        Environments.setEnvironments(Environment.OCESII_DANID_ENV_EXTERNALTEST);
    }
}
