/*
 * Licensed to the University Corporation for Advanced Internet Development,
 * Inc. (UCAID) under one or more contributor license agreements.  See the
 * NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The UCAID licenses this file to You under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensaml.security.crypto;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.crypto.Mac;

import net.shibboleth.utilities.java.support.logic.Constraint;

import org.apache.commons.codec.binary.Hex;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// NOTE: copied from opensaml security 3.4.6 to supress unwanted ERROR logs

/**
 * A utility class for computing and verifying raw signatures and MAC values.
 */
public final class SigningUtil {

    /** Constructor. */
    private SigningUtil() {
    }


    /**
     * Compute the signature or MAC value over the supplied input.
     * 
     * It is up to the caller to ensure that the specified algorithm ID and isMAC flag are consistent with the type of
     * signing key supplied in the signing credential.
     * 
     * @param signingCredential the credential containing the signing key
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param isMAC flag indicating whether the operation to be performed is a signature or MAC computation
     * @param input the input over which to compute the signature
     * @return the computed signature or MAC value
     * @throws SecurityException throw if the computation process results in an error
     */
    @Nonnull public static byte[] sign(@Nonnull final Credential signingCredential,
            @Nonnull final String jcaAlgorithmID, final boolean isMAC, @Nonnull final byte[] input)
            throws SecurityException {
        final Logger log = getLogger();

        final Key signingKey = CredentialSupport.extractSigningKey(signingCredential);
        if (signingKey == null) {
            log.error("No signing key supplied in signing credential for signature computation");
            throw new SecurityException("No signing key supplied in signing credential");
        }

        if (isMAC) {
            return signMAC(signingKey, jcaAlgorithmID, input);
        } else if (signingKey instanceof PrivateKey) {
            return sign((PrivateKey) signingKey, jcaAlgorithmID, input);
        } else {
            log.error("No PrivateKey present in signing credential for signature computation");
            throw new SecurityException("No PrivateKey supplied for signing");
        }
    }

    /**
     * Compute the raw signature value over the supplied input.
     * 
     * It is up to the caller to ensure that the specified algorithm ID is consistent with the type of signing key
     * supplied.
     * 
     * @param signingKey the private key with which to compute the signature
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param input the input over which to compute the signature
     * @return the computed signature value
     * @throws SecurityException thrown if the signature computation results in an error
     */
    @Nonnull public static byte[] sign(@Nonnull final PrivateKey signingKey, @Nonnull final String jcaAlgorithmID,
            @Nonnull final byte[] input) throws SecurityException {
        Constraint.isNotNull(signingKey, "Private key cannot be null");
        Constraint.isNotNull(jcaAlgorithmID, "JCA algorithm ID cannot be null");
        Constraint.isNotNull(input, "Input data to sign cannot be null");

        final Logger log = getLogger();
        log.debug("Computing signature over input using private key of type {} and JCA algorithm ID {}", signingKey
                .getAlgorithm(), jcaAlgorithmID);

        try {
            final Signature signature = Signature.getInstance(jcaAlgorithmID);
            signature.initSign(signingKey);
            signature.update(input);
            final byte[] rawSignature = signature.sign();
            log.debug("Computed signature: {}", Hex.encodeHex(rawSignature));
            return rawSignature;
        } catch (final GeneralSecurityException e) {
            log.error("Error during signature generation", e);
            throw new SecurityException("Error during signature generation", e);
        }
    }

    /**
     * Compute the Message Authentication Code (MAC) value over the supplied input.
     * 
     * It is up to the caller to ensure that the specified algorithm ID is consistent with the type of signing key
     * supplied.
     * 
     * @param signingKey the key with which to compute the MAC
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param input the input over which to compute the MAC
     * @return the computed MAC value
     * @throws SecurityException thrown if the MAC computation results in an error
     */
    @Nonnull public static byte[] signMAC(@Nonnull final Key signingKey, @Nonnull final String jcaAlgorithmID,
            @Nonnull final byte[] input) throws SecurityException {
        Constraint.isNotNull(signingKey, "Secret key cannot be null");
        Constraint.isNotNull(jcaAlgorithmID, "JCA algorithm ID cannot be null");
        Constraint.isNotNull(input, "Input data to sign cannot be null");

        final Logger log = getLogger();
        log.debug("Computing MAC over input using key of type {} and JCA algorithm ID {}", signingKey.getAlgorithm(),
                jcaAlgorithmID);

        try {
            final Mac mac = Mac.getInstance(jcaAlgorithmID);
            mac.init(signingKey);
            mac.update(input);
            final byte[] rawMAC = mac.doFinal();
            log.debug("Computed MAC: {}", Hex.encodeHexString(rawMAC));
            return rawMAC;
        } catch (final GeneralSecurityException e) {
            log.error("Error during MAC generation", e);
            throw new SecurityException("Error during MAC generation", e);
        }
    }

    /**
     * Verify the signature value computed over the supplied input against the supplied signature value.
     * 
     * It is up to the caller to ensure that the specified algorithm ID and isMAC flag are consistent with the type of
     * verification credential supplied.
     * 
     * @param verificationCredential the credential containing the verification key
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param isMAC flag indicating whether the operation to be performed is a signature or MAC computation
     * @param signature the computed signature value received from the signer
     * @param input the input over which the signature is computed and verified
     * @return true iff the signature value computed over the input using the supplied key and algorithm ID is identical
     *         to the supplied signature value
     * @throws SecurityException thrown if the signature computation or verification process results in an error
     */
    public static boolean verify(@Nonnull final Credential verificationCredential,
            @Nonnull final String jcaAlgorithmID, final boolean isMAC, @Nonnull final byte[] signature,
            @Nonnull final byte[] input) throws SecurityException {
        final Logger log = getLogger();

        final Key verificationKey = CredentialSupport.extractVerificationKey(verificationCredential);
        if (verificationKey == null) {
            log.error("No verification key supplied in verification credential for signature verification");
            throw new SecurityException("No verification key supplied in verification credential");
        }

        if (isMAC) {
            return verifyMAC(verificationKey, jcaAlgorithmID, signature, input);
        } else if (verificationKey instanceof PublicKey) {
            return verify((PublicKey) verificationKey, jcaAlgorithmID, signature, input);
        } else {
            log.error("No PublicKey present in verification credential for signature verification");
            throw new SecurityException("No PublicKey supplied for signature verification");
        }
    }

    /**
     * Verify the signature value computed over the supplied input against the supplied signature value.
     * 
     * It is up to the caller to ensure that the specified algorithm ID is consistent with the type of verification key
     * supplied.
     * 
     * @param verificationKey the key with which to compute and verify the signature
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param signature the computed signature value received from the signer
     * @param input the input over which the signature is computed and verified
     * @return true if the signature value computed over the input using the supplied key and algorithm ID is identical
     *         to the supplied signature value
     * @throws SecurityException thrown if the signature computation or verification process results in an error
     */
    public static boolean verify(@Nonnull final PublicKey verificationKey, @Nonnull final String jcaAlgorithmID,
            @Nonnull final byte[] signature, @Nonnull final byte[] input) throws SecurityException {
        Constraint.isNotNull(verificationKey, "Public key cannot be null");
        Constraint.isNotNull(jcaAlgorithmID, "JCA algorithm ID cannot be null");
        Constraint.isNotNull(signature, "Signature data to verify cannot be null");
        Constraint.isNotNull(input, "Input data to verify cannot be null");

        final Logger log = getLogger();
        log.debug("Verifying signature over input using public key of type {} and JCA algorithm ID {}", verificationKey
                .getAlgorithm(), jcaAlgorithmID);

        try {
            final Signature sig = Signature.getInstance(jcaAlgorithmID);
            sig.initVerify(verificationKey);
            sig.update(input);
            return sig.verify(signature);
        } catch (final GeneralSecurityException e) {
        	// NOTE : removed, not needed as the exception is thrown
            //log.error("Error during signature verification", e);
            throw new SecurityException("Error during signature verification", e);
        }
    }

    /**
     * Verify the Message Authentication Code (MAC) value computed over the supplied input against the supplied MAC
     * value.
     * 
     * It is up to the caller to ensure that the specified algorithm ID is consistent with the type of verification key
     * supplied.
     * 
     * @param verificationKey the key with which to compute and verify the MAC
     * @param jcaAlgorithmID the Java JCA algorithm ID to use
     * @param signature the computed MAC value received from the signer
     * @param input the input over which the MAC is computed and verified
     * @return true iff the MAC value computed over the input using the supplied key and algorithm ID is identical to
     *         the supplied MAC signature value
     * @throws SecurityException thrown if the MAC computation or verification process results in an error
     */
    public static boolean verifyMAC(@Nonnull final Key verificationKey, @Nonnull final String jcaAlgorithmID,
            @Nonnull final byte[] signature, @Nonnull final byte[] input) throws SecurityException {
        Constraint.isNotNull(verificationKey, "Secret key cannot be null");
        Constraint.isNotNull(jcaAlgorithmID, "JCA algorithm ID cannot be null");
        Constraint.isNotNull(signature, "Signature data to verify cannot be null");
        Constraint.isNotNull(input, "Input data to verify cannot be null");

        final Logger log = getLogger();
        log.debug("Verifying MAC over input using key of type {} and JCA algorithm ID {}", verificationKey
                .getAlgorithm(), jcaAlgorithmID);

        // Java JCA/JCE Mac interface doesn't have a verification op,
        // so have to compute the Mac and compare the byte arrays manually.

        final byte[] computed = signMAC(verificationKey, jcaAlgorithmID, input);
        return Arrays.equals(computed, signature);
    }
    
    /**
     * Get an SLF4J Logger.
     * 
     * @return a Logger instance
     */
    @Nonnull private static Logger getLogger() {
        return LoggerFactory.getLogger(SigningUtil.class);
    }
}