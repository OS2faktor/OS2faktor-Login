/*
    Copyright 2005 Carsten Raskgaard
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

/* $Id: NonOcesCertificateException.java,v 1.2 2005/08/04 21:37:50 cara Exp $ */

package dk.digitalidentity.ooapi.exceptions;

/**
 * Thrown when the given certificate is not compatible with the OOAPI.
 */
@SuppressWarnings("serial")
public class NonOcesCertificateException extends RuntimeException {
    public NonOcesCertificateException() {
        super();
    }

    public NonOcesCertificateException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public NonOcesCertificateException(String arg0) {
        super(arg0);
    }

    public NonOcesCertificateException(Throwable arg0) {
        super(arg0);
    }
}
