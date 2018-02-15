/******************************************************************************
 * Copyright (c) 2016-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.mojos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import com.cisco.device.packages.internal.sdu.SduCreator;

/**
 * Packages the project and its dependencies into a Single Deployable Unit (SDU)
 * for consumption by suitable platforms.
 *
 * @author danijoh2
 * @since 1.0.0
 */
@Mojo(name = "create-sdu", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE, executionStrategy = "once-per-session")
public class CreateSDUMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Component
    private RepositorySystem repoSystem;

    @Component
    public MavenProjectHelper mavenProjectHelper;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(property = "repositorySystemSession", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins
     * and their dependencies.
     */
    @Parameter(property = "project.remotePluginRepositories", readonly = true)
    private List<RemoteRepository> remoteRepos;

    /**
     * The projects in the reactor for aggregation.
     */
    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * Whether or not to build an SDU for this project.
     */
    @Parameter(property = "createSDU", defaultValue = "false")
    private boolean createSDU;

    /**
     * List of exclusions to ignore when packaging the SDU, in
     * {@code <groupId1>:<artifactId1>,<groupId2>:<artifactId2>,etc.} format.
     */
    @Parameter(property = "exclusions")
    private String exclusions;

    /**
     * When this goal is running on an aggregator POM, this flag determins
     * whether all downstream device package projects of the aggregator will be
     * included in the SDU, or whether only those artifacts found through the
     * dependency hierarchy of the Device Profile projects present in the
     * reactor will be included.
     */
    @Parameter(property = "includeAll", defaultValue = "true")
    private boolean includeAll;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (!createSDU) {
            getLog().debug("Skipping SDU creation as plugin was invoked with createSDU=false.");
            return;
        }

        SduCreator sduCreator = new SduCreator(project, session, repoSystem, repoSession, remoteRepos);
        sduCreator.setLog(getLog());
        sduCreator.setReactorProjects(reactorProjects);
        sduCreator.setIncludeAllReactorProjects(includeAll);
        if (exclusions != null) {
            List<Exclusion> extraExclusions = new ArrayList<Exclusion>();
            for (String exclusion : exclusions.split(",")) {
                String[] coords = exclusion.split(":");
                if (coords.length != 2) {
                    throw new MojoFailureException(
                            "Exclusion does not adhere to expected format. Expected: [<groupId>:<artifactId>], found: "
                                    + exclusion);
                }
                Exclusion exclude = new Exclusion();
                exclude.setGroupId(coords[0]);
                exclude.setArtifactId(coords[1]);
                extraExclusions.add(exclude);
            }
            sduCreator.setExtraExclusions(extraExclusions);
        }

        File sduFile = sduCreator.create();
        try {
            getLog().info("SDU Created: " + sduFile.getCanonicalPath());
            // Attach the .sdu to the project in case of install or deploy goal
            // being used
            mavenProjectHelper.attachArtifact(project, "sdu", "sdu", sduFile);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
