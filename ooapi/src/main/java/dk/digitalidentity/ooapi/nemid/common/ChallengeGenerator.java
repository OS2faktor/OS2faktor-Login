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

import javax.servlet.http.HttpSession;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class ChallengeGenerator {
	private static final String CHALLENGE_SESSION_KEY = "dk.os2faktor.nemid.challenge";

	public static String generateChallenge(HttpSession session) {
		String challenge = generateChallenge();

		session.setAttribute(CHALLENGE_SESSION_KEY, challenge);

		return challenge;
	}
	
	public static String getChallenge(HttpSession httpSession) {
		String challenge = (String) httpSession.getAttribute(ChallengeGenerator.CHALLENGE_SESSION_KEY);

		if (challenge == null || challenge.equals("")) {
			throw new RuntimeException("Session has no challenge");
		}

// if this method is called twice (double post I guess), then the 2nd attempt will fail, and there is no
// reason to remove the challenge as such... it allows for replay of the entire NemID login, fair enough,
// but we already have IP-locking on the session, so it should not matter
//		httpSession.removeAttribute(ChallengeGenerator.CHALLENGE_SESSION_KEY);

		return challenge;
	}

	private static String generateChallenge() {
		String challenge;

		try {
			challenge = "" + SecureRandom.getInstance("SHA1PRNG").nextLong();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		
		return challenge;
	}
}
