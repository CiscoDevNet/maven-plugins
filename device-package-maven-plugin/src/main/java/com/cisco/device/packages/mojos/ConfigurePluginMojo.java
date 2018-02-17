/******************************************************************************
 * Copyright (C) 2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.device.packages.mojos;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import com.cisco.common.constructs.Node;
import com.cisco.device.packages.constants.PackagingConstants;
import com.cisco.device.packages.internal.plugin.DevicePackagePluginInfo;
import com.cisco.device.packages.internal.plugin.ReactorHierarchyTree;

/**
 * Configures project POM's with this plugin declared as a build plugin. <br>
 * <ul>
 * <li>For Device Profile, Network Feature, and XDE Projects, this plugin will
 * be configured under the {@code <build><plugins>} section, without</li>
 * <li>For an aggregator POM, this plugin will be configured to to create an
 * aggregated SDU of all device package related artifacts listed as downstream
 * {@code <modules>}.</li>
 * <li>This plugin will determine the root-most Parent POM's where the version
 * of the plugin to execute will be centrally managed.</li>
 * </ul>
 * <br>
 * The exact project configuration applied can be tweaked through the various
 * goal parameters.
 *
 * @author danijoh2
 * @since 1.0.0
 */
@Mojo(name = "configure", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, aggregator = true, requiresProject = true, requiresDirectInvocation = true, executionStrategy = "once-per-session")
public class ConfigurePluginMojo extends AbstractMojo {

    private static final String INDENT = "  ==> "; // $NON-NLS-1$

    // @formatter:off
    private static final String PLUGIN_GROUP_ID = "com.cisco.maven.plugins"; // $NON-NLS-1$
    private static final String PLUGIN_ARTIFACT_ID = "device-package-maven-plugin"; // $NON-NLS-1$
    private static final List<String> OLD_PLUGIN_ARTIFACT_IDS = Arrays.asList(new String[] {
            "xmp-maven-dar-plugin", // $NON-NLS-1$
            "xmp-maven-feature-plugin", // $NON-NLS-1$
            "xmp-maven-xar-plugin", // $NON-NLS-1$
            "sdu-maven-plugin" }); // $NON-NLS-1$
    // @formatter:on

    @Component
    public MavenProjectHelper mavenProjectHelper;

    /**
     * The projects in the reactor for aggregation.
     */
    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * Whether or not the aggregator POM should be configured to build an
     * aggregated SDU of all sub-module project artifacts. This is only
     * supported when invoked on the top level POM of a multi-module reactor
     * where the project packaging is {@code 'pom'}, and this POM includes other
     * projects in the reactor as {@code <modules>}.
     */
    @Parameter(property = "buildRootSDU", required = false)
    private boolean buildRootSDU = true;

    /**
     * When {@code buildRootSDU=true}, configures whether or not all child
     * sub-modules of the aggregator project should be included. {@code true} to
     * include all modules (default), or {@code false} to only include those
     * artifacts found through the dependency hierarchy of the Device Profile
     * projects present in the reactor.
     */
    @Parameter(property = "includeAllModules", required = false)
    private boolean includeAllModules = true;

    /**
     * Whether or not the {@code createSDU} parameter of the
     * {@link CreateSDUMojo} should be configured to always run. By default SDU
     * creation is skipped, but can be configured to always run by settings this
     * flag to @{code true}.
     */
    @Parameter(property = "perProjectSDU", required = false)
    private boolean perProjectSDU = false;

    /**
     * Whether or not you intent to manage this plugin configuration centrally.
     * The expectation is that a single Parent POM would configure the version
     * of this plugin to use across all projects, and to set the
     * {@code <extensions>true</extensions>} parameter. By setting this to
     * {@code false} the required {@code extensions} configuration will be added
     * to all projects explicitly, and if the {@code pluginVersion} parameter is
     * declared, it will be used to set the plugin version in all projects
     * explicitly.
     */
    @Parameter(property = "managePluginCentrally", required = false)
    private boolean managePluginCentrally = true;

    /**
     * When the plugin version and configuration is being managed centrally in a
     * Parent POM, setting this option to {@code true} will cause the plugin to
     * be configured as a {@code <build><plugins><plugin>} just once in your
     * Parent POM(s), and the configuration ommitted entirely from all
     * downstream projects.
     */
    @Parameter(property = "executePluginCentrally", required = false)
    private boolean executePluginCentrally = false;

    /**
     * By default, it is expected that you configure your projects Parent POM to
     * declare the plugin version in the {@code pluginManagement} section of the
     * POM, to make the project easier to maintain when needing to move to a
     * newer version of this plugin. If you choose, you can pass an explicit
     * version to this goal to set the version explicitly within every projects
     * build configuration. You must also set
     * {@code managePluginCentrally=false} to use this parameter.<br>
     * If your project does not have a parent POM, the plugin version will be
     * configured directly in the project. <br>
     * By default this will use the version of this running plugin, but can be
     * overridden.
     */
    @Parameter(property = "pluginVersion", required = false)
    private String pluginVersion;

    MavenXpp3Reader reader = new MavenXpp3Reader();
    MavenXpp3Writer writer = new MavenXpp3Writer();

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (StringUtils.isEmpty(pluginVersion)) {
            try {
                pluginVersion = DevicePackagePluginInfo.getVersion();
            } catch (IOException e) {
                getLog().error(
                        "Failed to identify the running plugin version, plugin version configuration will be missing. Please contact the plugin developer for assistance.");
            }
        }

        if (reactorProjects.size() > 1) {
            getLog().info("Configuring reactor projects to use the device-package-maven-plugin...");
        } else {
            getLog().info("Configuring project to use the device-package-maven-plugin...");
        }

        ReactorHierarchyTree parentHierarchy = new ReactorHierarchyTree();
        for (MavenProject reactorProject : reactorProjects) {
            configureBuildPlugin(reactorProject, false);
            if (managePluginCentrally) {
                addParentInfo(reactorProject, parentHierarchy);
            }
        }

        if (managePluginCentrally) {
            Collection<Node<MavenProject>> parentPOMRoots = parentHierarchy.getRoots();
            for (Node<MavenProject> parentRoot : parentPOMRoots) {
                while (parentRoot.hasChildren() && parentRoot.getChildren().size() == 1) {
                    parentRoot = parentRoot.getChildren().iterator().next();
                }
                configureBuildPlugin(parentRoot.getData(), true);
            }
        }
    }

    private void configureBuildPlugin(MavenProject reactorProject, boolean isRootParentPom)
            throws MojoFailureException {

        boolean isRootAggregator = isRootAggregator(reactorProject);
        boolean isDevicePackageStyleProject = PackagingConstants.ALL_DP_PACKAGING
                .contains(reactorProject.getPackaging());
        if (!isRootAggregator && !isDevicePackageStyleProject && !isRootParentPom) {
            return;
        }
        // Even if we don't need to configure it, we still need to look for old
        // config to clean-up
        boolean isParentInReactor = isParentInReactor(reactorProject);
        boolean shouldConfigurePlugin = (isRootAggregator && buildRootSDU) || (isRootParentPom && managePluginCentrally)
                || (isDevicePackageStyleProject && (!isParentInReactor || !executePluginCentrally));

        String name = reactorProject.getGroupId() + ":" + reactorProject.getArtifactId();
        getLog().info("Processing project " + name + "...");

        // Read the project model
        Model model = null;
        try (FileReader stream = new FileReader(reactorProject.getFile())) {
            model = reader.read(stream);
        } catch (Exception e) {
            throw new MojoFailureException("Failure to parse POM: " + reactorProject.getFile().getAbsolutePath(), e);
        }

        // Create a new build section
        Build build = model.getBuild();
        if (build != null) {
            // Clean-up any of the old build plugins
            if (CollectionUtils.emptyIfNull(build.getPlugins()).removeIf(p -> "com.cisco.xmp.sdk".equals(p.getGroupId())
                    && OLD_PLUGIN_ARTIFACT_IDS.contains(p.getArtifactId()))) {
                getLog().info(INDENT + "Removed stale <build><plugins> configuration.");
            }
            if (build.getPluginManagement() != null) {
                if (CollectionUtils.emptyIfNull(build.getPluginManagement().getPlugins())
                        .removeIf(p -> "com.cisco.xmp.sdk".equals(p.getGroupId())
                                && OLD_PLUGIN_ARTIFACT_IDS.contains(p.getArtifactId()))) {
                    getLog().info(INDENT + "Removed stale <build><pluginManagement> configuration.");
                }
            }
        } else if (shouldConfigurePlugin) {
            getLog().info(INDENT + "Build section added.");
            build = new Build();
            model.setBuild(build);
        } else {
            // If no build config was cleaned up, and we shouldn't be
            // configuring the plugin, we can return immediately. Otherwise we
            // must always fall through so changes are actually written to disk.
            return;
        }

        if (shouldConfigurePlugin) {
            if (isRootAggregator || isDevicePackageStyleProject) {
                // Only configure as a <build><plugins><plugin> in the
                // aggregator, or device package projects
                Plugin plugin = findDevicePackagePlugin(build.getPlugins());

                if (!isParentInReactor || !managePluginCentrally) {
                    getLog().info(INDENT + "Configured plugin version as " + pluginVersion);
                    plugin.setExtensions(true);
                    plugin.setVersion(StringUtils.trimToNull(pluginVersion));
                    if (perProjectSDU || (isRootAggregator && buildRootSDU)) {
                        getLog().info(INDENT + "Configured plugin with createSDU=true");
                        plugin.setConfiguration(MojoExecutor.configuration(MojoExecutor.element("createSDU", "true")));
                    }
                } else if (!StringUtils.isEmpty(plugin.getVersion())) {
                    getLog().info(INDENT
                            + "Version removed from plugin configuration because it should be managed centrally by the parent POM.");
                    plugin.setVersion(null);
                }

                if (isRootAggregator && buildRootSDU) {
                    if (plugin.getExecutions() == null) {
                        plugin.setExecutions(new ArrayList<>());
                    }
                    PluginExecution createSduExecution = plugin.getExecutions().stream()
                            .filter(ex -> CollectionUtils.emptyIfNull(ex.getGoals()).contains("create-sdu")).findFirst()
                            .orElse(null);
                    if (createSduExecution == null) {
                        getLog().info(INDENT + "Plugin execution 'create-aggregated-sdu' added.");
                        createSduExecution = new PluginExecution();
                        createSduExecution.setId("create-aggregated-sdu");
                        createSduExecution.setGoals(Arrays.asList(new String[] { "create-sdu" }));
                        plugin.getExecutions().add(createSduExecution);
                    }
                }
            } else if (isRootParentPom) {
                // Configure as a <build><pluginManagement><plugin> in parent
                // POMs
                Plugin plugin = null;
                if (executePluginCentrally) {
                    if (build.getPlugins() == null) {
                        build.setPlugins(new ArrayList<>());
                    }
                    plugin = findDevicePackagePlugin(build.getPlugins());
                } else {
                    if (build.getPluginManagement() == null) {
                        build.setPluginManagement(new PluginManagement());
                    }
                    plugin = findDevicePackagePlugin(build.getPluginManagement().getPlugins());
                }
                if (StringUtils.isEmpty(plugin.getVersion())) {
                    getLog().info(INDENT + "Configured plugin version as " + pluginVersion);
                    plugin.setVersion(StringUtils.trimToNull(pluginVersion));
                }
                plugin.setExtensions(true);
                if (perProjectSDU) {
                    getLog().info(INDENT + "Configured plugin with createSDU=true");
                    plugin.setConfiguration(MojoExecutor.configuration(MojoExecutor.element("createSDU", "true")));
                }
            }
        }

        try (FileWriter stream = new FileWriter(reactorProject.getFile())) {
            writer.write(stream, model);
        } catch (IOException e) {
            getLog().error(e);
        }
    }

    private Plugin findDevicePackagePlugin(List<Plugin> plugins) {
        Optional<Plugin> plugin = CollectionUtils.emptyIfNull(plugins).stream()
                .filter(p -> PLUGIN_GROUP_ID.equals(p.getGroupId()) && PLUGIN_ARTIFACT_ID.equals(p.getArtifactId()))
                .findFirst();
        if (plugin.isPresent()) {
            getLog().info(INDENT + "Existing plugin definition found, verifying configuration...");
            return plugin.get();
        }
        getLog().info(INDENT + "Added plugin declaration");
        Plugin buildPlugin = new Plugin();
        buildPlugin.setGroupId(PLUGIN_GROUP_ID);
        buildPlugin.setArtifactId(PLUGIN_ARTIFACT_ID);
        plugins.add(buildPlugin);
        return buildPlugin;
    }

    private boolean isParentInReactor(MavenProject reactorProject) {
        return reactorProject.hasParent() && reactorProjects.contains(reactorProject.getParent());
    }

    private MavenProject getParentInReactor(MavenProject reactorProject) {
        return !reactorProject.hasParent() ? null
                : reactorProjects.stream().filter(p -> p == reactorProject.getParent()).findFirst().orElse(null);
    }

    private boolean isRootAggregator(MavenProject reactorProject) {
        return reactorProject.isExecutionRoot() && PackagingConstants.POM_EXT.equals(reactorProject.getPackaging())
                && CollectionUtils.isNotEmpty(reactorProject.getModules());
    }

    /**
     * Given a reactor project, adds all its parent project details to the
     * reactor hierarchy tree.
     *
     * @param reactorProject
     * @param parentProjectTree
     * @return
     */
    private Node<MavenProject> addParentInfo(MavenProject reactorProject, ReactorHierarchyTree parentProjectTree) {

        MavenProject parentProject = getParentInReactor(reactorProject);
        if (parentProject != null) {
            Node<MavenProject> parentNode = parentProjectTree.find(parentProject);
            if (parentNode != null) {
                return parentNode;
            } else {
                parentNode = addParentInfo(parentProject, parentProjectTree);
            }
            return parentNode == null ? parentProjectTree.addRoot(parentProject) : parentNode.addChild(parentProject);
        }
        return null;
    }
}
