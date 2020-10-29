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
package dk.digitalidentity.ooapi.securitypackage;

import dk.digitalidentity.ooapi.exceptions.InternalException;
import dk.digitalidentity.ooapi.securitypackage.exceptions.ChallengeDoesNotMatchException;
import dk.digitalidentity.ooapi.signatures.OpensignAbstractSignature;
import dk.digitalidentity.ooapi.signatures.SignatureProperty;

public class ChallengeVerifier {
    static void verifyChallenge(OpensignAbstractSignature abstractSignature, String challenge) throws InternalException {
        SignatureProperty signatureChallenge = abstractSignature.getSignatureProperties().get("challenge");
        if (signatureChallenge==null || signatureChallenge.getValue()==null || !signatureChallenge.getValue().equals(challenge)) {
            throw new ChallengeDoesNotMatchException("Challenge does not match expected value or expected value is null, expected="+challenge+", actual="+(signatureChallenge==null?"null":signatureChallenge.getValue()));
        }
    }
}
