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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class FileReader {

    public static String ReadFile(String path) {
        InputStream resourceAsStream = FileReader.class.getResourceAsStream(path);
        if (resourceAsStream == null) {
            return null;
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(resourceAsStream));
            String l;

            StringBuffer sb = new StringBuffer();
            while ((l = br.readLine()) != null) {
                sb.append(l + "\n");
            }
            return sb.toString();
        } catch (IOException ie) {
            return null;
        }
    }

    public static String ReadFile(String path, String filename) {
        File ooapiFile = new File(path, filename);
        if (!ooapiFile.exists()) {
            return null;
        }

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(ooapiFile)));
            String l;

            StringBuffer sb = new StringBuffer();
            while ((l = br.readLine()) != null) {
                sb.append(l + "\n");
            }
            br.close();
            return sb.toString();
        } catch (IOException ie) {
            return null;
        }
    }
}