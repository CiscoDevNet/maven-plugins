/******************************************************************************
 * Copyright (C) 2012-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.device.packages.internal.device.profile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cisco.common.plugin.helpers.BuildHelper;
import com.cisco.common.plugin.helpers.ManifestHelper;
import com.cisco.device.packages.constants.PackagingConstants;
import com.cisco.device.packages.internal.device.profile.constants.IOrderedDependencyConstants;

/**
 *
 * @author danijoh2
 */
public class DeviceProfileValidator {

    public static void validate(MavenProject project, BuildContext context, MavenProjectHelper helper)
            throws MojoExecutionException {

        context.removeMessages(project.getFile());

        File orderedFeaturesFile = new File(project.getBasedir(),
                IOrderedDependencyConstants.ORDERED_FEATURE_FILE_NAME);
        if (!orderedFeaturesFile.exists()) {
            BuildHelper.makeError(context, project.getFile(), IOrderedDependencyConstants.ORDERED_FEATURE_FILE_NAME
                    + " is missing. Please check with the project creator to ensure they have shared all project files.",
                    true);
        }
        context.removeMessages(orderedFeaturesFile);

        File manifestFile = ManifestHelper.getManifest(project);
        if (manifestFile.exists()) {
            context.removeMessages(manifestFile);
        }

        List<String> orderedDeps = new ArrayList<String>();
        Map<String, String> overrides = new HashMap<String, String>();
        Document doc = null;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(orderedFeaturesFile);
        } catch (Exception e) {
            BuildHelper.makeError(context, orderedFeaturesFile,
                    "Failed to parse " + IOrderedDependencyConstants.ORDERED_FEATURE_FILE_NAME + " file.", true);
        }
        Element docElement = doc.getDocumentElement();
        if (docElement == null) {
            BuildHelper.makeError(context, orderedFeaturesFile,
                    "Failed to parse " + IOrderedDependencyConstants.ORDERED_FEATURE_FILE_NAME + " file.", true);
        }

        NodeList docList = docElement.getElementsByTagName(IOrderedDependencyConstants.DEP_ROOT_NODE);
        if (docList != null) {
            Element root = (Element) docList.item(0);
            if (root != null) {
                NodeList deps = root.getChildNodes();
                for (int i = 0; i < deps.getLength(); i++) {
                    Node node = deps.item(i);
                    if (!(node instanceof Element)
                            || !node.getNodeName().equals(IOrderedDependencyConstants.DEPENDENCY_NODE)) {
                        continue;
                    }
                    Element dep = (Element) node;
                    String depName = getIdFromDep(dep);
                    if (!dep.hasAttribute(IOrderedDependencyConstants.OVERRIDE)) {
                        orderedDeps.add(depName);
                    } else {
                        Element override = (Element) dep
                                .getElementsByTagName(IOrderedDependencyConstants.DEPENDENCY_NODE).item(0);
                        if (override == null) {
                            BuildHelper.makeWarning(context, orderedFeaturesFile,
                                    "Dependency " + depName + " is marked for override, but no override is given.");
                        }
                        String overrideName = getIdFromDep(override);
                        overrides.put(depName, overrideName);
                    }
                }
            }
        }

        File cdpDir = new File(project.getBasedir(), IOrderedDependencyConstants.CDP_DIRECTORY);

        List<?> dependencies = project.getDependencies();
        if ((dependencies == null || dependencies.isEmpty()) && !cdpDir.exists()) {
            BuildHelper.makeWarning(context, project.getFile(),
                    "No dependencies or configuration parts were found in the project, at least one of either is expected.");
        }

        Dependency parent = null;
        for (Object o : dependencies) {
            if (o instanceof Dependency) {
                Dependency dep = (Dependency) o;
                String depName = getIdFromDep(dep);
                if (PackagingConstants.DAR_EXT.equals(dep.getType())) {
                    if (parent == null) {
                        parent = dep;
                        List<Exclusion> exclusions = dep.getExclusions();
                        if (exclusions == null || exclusions.isEmpty()) {
                            if (!overrides.isEmpty()) {
                                BuildHelper.makeWarning(context, project.getFile(),
                                        ".orderedFeatures configuration declares feature overrides, but no exclusions were found on the Parent Profile <dependency> element declared in the POM on element: "
                                                + depName);
                            }
                        } else {
                            for (String override : overrides.keySet()) {
                                boolean found = false;
                                for (Exclusion exclusion : exclusions) {
                                    String exclusionName = exclusion.getGroupId() + PackagingConstants.DOT
                                            + exclusion.getArtifactId();
                                    if (override.equals(exclusionName)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    BuildHelper.makeWarning(context, project.getFile(),
                                            "Project configuration contains an override for feature: '" + override
                                                    + "', but no <exclusion> is declared in the POM on parent dependency: '"
                                                    + depName
                                                    + "' Please use the SDK to correct the project configuration.");
                                }
                            }
                        }
                    } else {
                        BuildHelper.makeError(context, project.getFile(),
                                "POM contains two or more parent device profile ('dar') dependency references, this configuration is not allowed. First parent reference: '"
                                        + getIdFromDep(parent) + "', second parent reference: '" + depName + "'.",
                                true);
                    }
                } else if (PackagingConstants.FEATURE_EXT.equals(dep.getType())) {
                    boolean found = false;
                    for (String orderedDep : orderedDeps) {
                        if (orderedDep.equals(depName)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        orderedDeps.remove(depName);
                    } else {
                        for (String override : overrides.values()) {
                            if (override.equals(depName)) {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        BuildHelper.makeWarning(context, orderedFeaturesFile,
                                "Project configuration is missing the execution order details for feature: '" + depName
                                        + "'. Please use the SDK to correct the project configuration.");
                    }
                }
            }
        }

        for (String orderedDep : orderedDeps) {
            BuildHelper.makeWarning(context, project.getFile(),
                    "Project configuration contains a reference to Network Feature: '" + orderedDep
                            + "', but there is no matching <dependency> declared in the POM. Please use the SDK to correct the project configuration.");
        }
    }

    private static String getIdFromDep(Element dep) {
        String groupId = dep.getElementsByTagName(IOrderedDependencyConstants.GROUP_ID_NODE).item(0).getTextContent();
        String artifactId = dep.getElementsByTagName(IOrderedDependencyConstants.ARTIFACT_ID_NODE).item(0)
                .getTextContent();
        return getId(groupId, artifactId);
    }

    private static String getIdFromDep(Dependency dep) {
        return getId(dep.getGroupId(), dep.getArtifactId());
    }

    private static String getId(String groupId, String artifactId) {
        return groupId + PackagingConstants.COLON + artifactId;
    }
}
