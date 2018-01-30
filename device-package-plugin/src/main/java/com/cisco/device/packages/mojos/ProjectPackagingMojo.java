/******************************************************************************
 * Copyright (c) 2009-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.mojos;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.cisco.device.packages.constants.PackagingConstants;

/**
 * Default package goal for Device Package projects (Device Profiles, Network
 * Features, and XDE projects), to package the project contents into a JAR file
 * with the project appropriate file extension expected by suitable
 * platforms.<br>
 * <br>
 * This plugin executes the {@code maven-jar-plugin} directly to handle the bulk
 * of the project packaging, with special configuration to handle using the
 * existing Project Manifest if one is found, instead of having the
 * maven-jar-plugin generate its own:
 *
 * <pre>
 * {@code <configuration>
 *   <archive>
 *     <manifestFile>src/main/resources/META-INF/MANIFEST.MF</manifestFile>
 *   </archive>
 * <configuration>}
 * </pre>
 *
 * If you experience a problem with the way the maven-jar-plugin is packaging
 * the project contents, you can override the version is invoked using the
 * {@link #mavenJarPluginVersion} property. If you have a very special use case,
 * you can explicitly configure the maven-jar-plugin to execute in your POM's
 * {@code <build><plugins>} configuration. If this plugin already sees a main
 * project artifact attached, it will skip execution of the maven-jar-plugin
 * altogether.
 *
 * @author danijoh2
 * @since 1.0.0
 */
@Mojo(name = "package", requiresProject = true, defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ProjectPackagingMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Component
    public MavenProjectHelper helper;

    @Component
    protected BuildPluginManager pluginManager;

    @Parameter(property = "manifest", defaultValue = "${project.build.outputDirectory}/META-INF/MANIFEST.MF")
    private String manifest;

    /**
     * Whether or not to create an SDU file from this project.
     */
    @Parameter(property = "createSDU", defaultValue = "false")
    private boolean createSDU;

    /**
     * This plugin executes the maven-jar-plugin to do the bulk of the project
     * packaging, with special configuration to handle using the existing
     * Project Manifest if one is found, instead of having the maven-jar-plugin
     * generate its own. If you experience a problem with the plugin you can
     * override the version that is used with this property.<br>
     * <br>
     * If you have a very special use case, you can explicitly configure the
     * maven-jar-plugin to execute in your POM's {@code <build><plugins>}
     * configuration. If this plugin already sees a main project artifact
     * attached, it will skip execution of the maven-jar-plugin altogether.
     */
    @Parameter(property = "mavenJarPluginVersion", defaultValue = "3.0.2")
    private String mavenJarPluginVersion;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (project.getArtifact() != null && project.getArtifact().getFile() != null
                && project.getArtifact().getFile().exists()) {
            getLog().info("The project artifact already exists: " + project.getArtifact().getFile().getAbsolutePath()
                    + ", skipping packaging.");
        } else {
            // @formatter:off
            Xpp3Dom config = configuration();
            if (new File(manifest).exists()) {
                config = configuration(
                        element(name("archive"), // $NON-NLS-1$
                                element(name("manifestFile"), manifest) // $NON-NLS-1$
                        )
                    );
            }
            executeMojo(
                    plugin(
                        groupId("org.apache.maven.plugins"), // $NON-NLS-1$
                        artifactId("maven-jar-plugin"), // $NON-NLS-1$
                        version(mavenJarPluginVersion)
                    ),
                    goal("jar"), // $NON-NLS-1$
                    config,
                    executionEnvironment(
                        project,
                        session,
                        pluginManager
                    )
                );
            // @formatter:on
        }

        File compiledJar = project.getArtifact().getFile();
        // Maybe a bit of a hack, but to date all projects are using the same
        // file extension as their project packaging name.
        if (FilenameUtils.isExtension(compiledJar.getName(), project.getPackaging())) {
            getLog().debug("The project artifact is already using the expected file extension");
            return;
        }

        String desiredFileExtension = PackagingConstants.DOT + project.getPackaging();
        File renamedFile = new File(compiledJar.getParentFile(),
                FilenameUtils.removeExtension(compiledJar.getName()) + desiredFileExtension);
        try {
            getLog().debug("Renaming project artifact to use '" + desiredFileExtension + "' extension: "
                    + renamedFile.getAbsolutePath());
            FileUtils.copyFile(compiledJar, renamedFile);
            project.getArtifact().setFile(renamedFile);
            compiledJar.delete();
        } catch (Exception e) {
            getLog().warn("Failed to rename project artifact file to use '" + desiredFileExtension + "' extension:"
                    + compiledJar.getAbsolutePath());
        }
    }
}
