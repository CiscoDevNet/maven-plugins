/******************************************************************************
 * Copyright (C) 2012-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.device.packages.internal.feature;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.cisco.common.plugin.helpers.BuildHelper;
import com.cisco.common.plugin.helpers.ManifestHelper;
import com.cisco.device.packages.constants.PackagingConstants;

/**
 *
 * @author danijoh2
 */
public class NetworkFeatureValidator {

    public static void validate(MavenProject project, BuildContext context, MavenProjectHelper helper)
            throws MojoExecutionException {

        context.removeMessages(project.getFile());

        File manifestFile = ManifestHelper.getManifest(project);
        if (!manifestFile.exists()) {
            BuildHelper.makeError(context, project.getFile(),
                    "The project is missing the required Manifest file at: " + manifestFile.getAbsolutePath()
                            + ". Please check with the project creator to ensure they have shared all project files.",
                    true);
        } else {
            context.removeMessages(manifestFile);
        }

        Manifest manifest = null;
        try (FileInputStream stream = new FileInputStream(manifestFile)) {
            manifest = new Manifest(stream);
        } catch (Exception e) {
            BuildHelper.makeError(context, project.getFile(),
                    "An error occurred reading the project Manifest file at: " + manifestFile.getAbsolutePath(), e,
                    true);
        }

        String networkCapability = manifest.getMainAttributes().getValue("Network-Capabilities");
        if (StringUtils.isEmpty(networkCapability)) {
            BuildHelper.makeWarning(context, manifestFile,
                    "The project does not specify its network capability, please consider declaring one using the SDK.");
        } else if (networkCapability.contains(",")) {
            BuildHelper.makeWarning(context, manifestFile,
                    "The project specifies more than one network capability, only one is expected by the runtime, the first value will be used: "
                            + networkCapability);
        }

        Map<String, String> externalPartTypes = new HashMap<String, String>();
        Map<String, Attributes> entries = manifest.getEntries();
        for (Entry<String, Attributes> entry : entries.entrySet()) {
            if (entry.getKey().startsWith("maven")) {
                String featureType = entry.getValue().getValue(new Name("Feature-Part-Type"));
                if (StringUtils.isEmpty(featureType)) {
                    BuildHelper.makeWarning(context, manifestFile,
                            "The Project does not declare the Type of reference: " + entry.getKey()
                                    + ". Please consider using the SDK to fix the project configuration.");
                } else {
                    String[] entrySplit = entry.getKey().split(":");
                    externalPartTypes.put(entrySplit[1] + "." + entrySplit[2], featureType);
                }
            }
        }
        for (Object o : project.getDependencies()) {
            Dependency dep = (Dependency) o;
            String depName = dep.getGroupId() + PackagingConstants.COLON + dep.getArtifactId();
            String type = externalPartTypes.get(depName);
            if (type == null) {
                BuildHelper.makeWarning(context, project.getFile(), "Project specifies a <dependency> on " + depName
                        + ", but this dependency information is missing from the project configuration. Please use the SDK to correct the project configuration.");
            }
        }
    }
}
