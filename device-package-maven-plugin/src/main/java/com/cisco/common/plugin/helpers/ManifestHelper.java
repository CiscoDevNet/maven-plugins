/******************************************************************************
 * Copyright (C) 2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.common.plugin.helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.maven.project.MavenProject;

/**
 *
 * @author danijoh2
 */
public class ManifestHelper {

    public static String PROJECT_MANIFEST_PATH = "/src/main/resources/META-INF/MANIFEST.MF"; // $NON-NLS-1$

    /**
     * Gets the manifest file for the given project, but does not verify that
     * the file exists.
     *
     * @param project
     *            - The project to read from
     * @return - The manifest file reference, which may or may not exist.
     */
    public static File getManifest(MavenProject project) {
        return new File(project.getBasedir(), PROJECT_MANIFEST_PATH);
    }

    /**
     * Ensures MANIFEST.MF has empty line at end, to avoid maven-jar-plugin from
     * thinking it is a corrupt file and generating its own. Does nothing if no
     * project Manifest is found.
     *
     * @param project
     *            - The Project to check
     *
     * @return - {@code true} if a change was made, otherwise {@code false}.
     */
    public static boolean fixManifest(MavenProject project) {

        File manifest = getManifest(project);
        if (manifest.exists()) {
            return enforceEmptyLineAtEnd(manifest);
        }
        return false;
    }

    public static boolean enforceEmptyLineAtEnd(File manifest) {

        InputStream fis = null;
        BufferedReader reader = null;
        String lastLine = null;
        try {
            fis = new FileInputStream(manifest);
            reader = new BufferedReader(new InputStreamReader(fis));
            String line = null;
            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }
        } catch (Exception e) {

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {

                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {

                }
            }
        }
        if (lastLine == null || !lastLine.isEmpty()) {
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(new FileWriter(manifest, true), true);
                writer.println();
            } catch (Exception e) {

            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception e) {

                    }
                }
            }
            return true;
        }

        return false;
    }
}
