/********************************************************************************
 * Copyright (c) 2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.mojos;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.cisco.common.plugin.helpers.BuildHelper;
import com.cisco.device.packages.constants.PackagingConstants;
import com.cisco.device.packages.internal.device.profile.DeviceProfileValidator;
import com.cisco.device.packages.internal.feature.NetworkFeatureValidator;

/**
 * Validates Device Package project types (Device Profile, Network Feature, or
 * XDE projects) to ensure the project contents will work in suitable platforms.
 *
 * @author danijoh2
 * @since 1.0.0
 */
@Mojo(name = "validate", requiresProject = true, defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class ValidateProjectMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Component
    private BuildContext buildContext;

    @Component
    private MavenProjectHelper helper;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (PackagingConstants.DAR_EXT.equals(project.getPackaging())) {
            DeviceProfileValidator.validate(project, buildContext, helper);
        } else if (PackagingConstants.FEATURE_EXT.equals(project.getPackaging())) {
            NetworkFeatureValidator.validate(project, buildContext, helper);
        } else if (PackagingConstants.XDE_EXT.equals(project.getPackaging())) {
            // No XDE project validation in place yet
        } else {
            BuildHelper.makeWarning(buildContext, project.getFile(),
                    "This goal is not intended for projects with '"
                            + StringUtils.defaultString(project.getPackaging(), PackagingConstants.JAR_EXT)
                            + "' packaging, skipping.");
        }
    }
}
