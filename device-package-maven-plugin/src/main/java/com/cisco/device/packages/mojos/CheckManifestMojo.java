/******************************************************************************
 * Copyright (C) 2016-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.device.packages.mojos;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.cisco.common.plugin.helpers.ManifestHelper;

/**
 * Ensures the project manifest has an empty line at the end, otherwise certain
 * versions of the {@code maven-jar-plugin} may think the file is corrupt.
 *
 * @author danijoh2
 * @since 1.0.0
 */
@Mojo(name = "check-manifest", requiresProject = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class CheckManifestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (ManifestHelper.fixManifest(project)) {
            getLog().warn(
                    "Project Manifest file did not have an empty line, which is an expectation of all Manifest files: "
                            + ManifestHelper.getManifest(project).getAbsolutePath()
                            + ". The manifest has been fixed and should be committed back to source control, if applicable.");
        }
    }
}
