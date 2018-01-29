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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import com.cisco.device.packages.constants.PackagingConstants;
import com.cisco.device.packages.internal.sdu.SduCreator;

/**
 * Combines one or more Single Deployable Unit (SDU) files built by other
 * projects into a single SDU. When invoked on a project with 2 or more SDU
 * files listed as dependencies, will combine them into a consolidated SDU file.
 * This goal will automatically remove older versions if multiple versions of
 * the same artifact are present across the various SDU files. This goal
 * requires all project dependencies to be downloaded, and is advised to be
 * configured in a stand-alone project with only SDU dependencies for the best
 * build performance. The resulting SDU file will be attached to the project
 * using the SDU classifier/extension.
 *
 * @author Daniel Johnson - danijoh2@cisco.com
 * @since 1.0.0
 */
@Mojo(name = "combine-sdu", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class CombineSDUMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Component
    public MavenProjectHelper mavenProjectHelper;

    /**
     * The name of the resulting SDU file.
     */
    @Parameter(defaultValue = "${project.artifactId}-${project.version}.sdu")
    private String sduName;

    /**
     * The name of the resulting SDU file.
     */
    @Parameter(defaultValue = "${project.build.directory}/dependency")
    private File sduDirectory;

    /**
     * Whether or not the build should fail if the resulting SDU is empty, or no
     * SDU dependencies are found in the project.
     */
    @Parameter(defaultValue = "true")
    private boolean failOnEmpty;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (CollectionUtils.isEmpty(project.getDependencies())) {
            if (failOnEmpty) {
                throw new MojoExecutionException(
                        "Project has no dependencies configured. Please add one or more SDU dependencies to use this goal, or use failOnEmpty=false to ignore this failure.");
            } else {
                getLog().warn("Project has no SDU dependencies, skipping creation of combined SDU.");
            }
        }

        Manifest sduManifest = createProjectManifest();

        Map<String, Artifact> artifactMap = new HashMap<String, Artifact>();
        if (sduDirectory.exists()) {
            for (File sdu : sduDirectory
                    .listFiles(file -> FilenameUtils.isExtension(file.getName(), PackagingConstants.SDU_EXT))) {
                getLog().info("Processing SDU: " + sdu.getAbsolutePath());
                Manifest manifest = null;
                try (ZipFile sduZip = new ZipFile(sdu)) {
                    try {
                        ZipEntry manifestEntry = sduZip.getEntry("META-INF/MANIFEST.MF");
                        if (manifestEntry == null) {
                            throw new MojoExecutionException("SDU is missing MANIFEST: " + sdu.getAbsolutePath() + ".");
                        }
                        manifest = new Manifest(sduZip.getInputStream(manifestEntry));
                    } catch (Exception e) {
                        throw new MojoExecutionException(
                                "An error occurred loading MANIFEST from: " + sdu.getAbsolutePath() + ".", e);
                    }
                    Map<String, Attributes> entries = manifest.getEntries();
                    int i = 0;
                    Attributes attr = entries.get(SduCreator.SDU_MANIFEST_ATTR_LOAD_ORDER + i++);
                    while (attr != null) {
                        String groupId = attr.getValue(SduCreator.SDU_MANIFEST_ATTR_GROUP_ID);
                        String artifactId = attr.getValue(SduCreator.SDU_MANIFEST_ATTR_ARTIFACT_ID);
                        String versionValue = attr.getValue(SduCreator.SDU_MANIFEST_ATTR_VERSION);
                        attr = entries.get(SduCreator.SDU_MANIFEST_ATTR_LOAD_ORDER + i++);

                        ZipEntry entry = sduZip.getEntry(artifactId + PackagingConstants.DASH + versionValue
                                + PackagingConstants.DOT + PackagingConstants.DAR_EXT);
                        try {
                            addArtifactToMap(groupId, artifactId, versionValue, entry, sduZip.getInputStream(entry),
                                    artifactMap, sduManifest);
                        } catch (Exception e) {
                            throw new MojoExecutionException("An error occurred getting input stream from: "
                                    + sdu.getAbsolutePath() + " for: " + entry.getName() + ".", e);
                        }
                    }
                    ZipEntry entry = null;
                    Enumeration<? extends ZipEntry> enumerator = sduZip.entries();
                    while (enumerator.hasMoreElements()) {
                        entry = enumerator.nextElement();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        if (entry.getName().endsWith(PackagingConstants.FEATURE_EXT)
                                || entry.getName().endsWith(PackagingConstants.XDE_EXT)) {
                            // Split into groupId, artifactId, version
                            int lastSlash = entry.getName().lastIndexOf('/');
                            String full = entry.getName().substring(0, lastSlash);
                            lastSlash = full.lastIndexOf('/');
                            String versionValue = full.substring(lastSlash + 1);
                            full = full.substring(0, lastSlash);
                            lastSlash = full.lastIndexOf('/');
                            String artifactId = full.substring(lastSlash + 1);
                            full = full.substring(0, lastSlash);
                            String groupId = full.replaceAll("/", ".");
                            try {
                                addArtifactToMap(groupId, artifactId, versionValue, entry, sduZip.getInputStream(entry),
                                        artifactMap, sduManifest);
                            } catch (Exception e) {
                                throw new MojoExecutionException("An error occurred getting input stream from: "
                                        + sdu.getAbsolutePath() + " for: " + entry.getName() + ".", e);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new MojoExecutionException(
                            "An error occurred loading MANIFEST from: " + sdu.getAbsolutePath() + ".", e);
                }
            }
        }

        if (artifactMap.isEmpty()) {
            if (failOnEmpty) {
                throw new MojoExecutionException("No SDU files were found at " + sduDirectory.getAbsolutePath()
                        + ". Please ensure the maven-dependencies-plugin is configured with copy-dependencies goal.");
            } else {

            }
        }

        File sduFile = new File(project.getBuild().getDirectory() + File.separator + sduName);
        try (FileOutputStream rootStream = new FileOutputStream(sduFile)) {
            try (JarOutputStream sduStream = new JarOutputStream(rootStream, sduManifest)) {
                // Create versions.txt in SDU
                String versionText = project.getGroupId() + "." + project.getArtifactId() + "-" + project.getVersion();
                CRC32 crc = new CRC32();
                byte[] bytes = versionText.getBytes(Charset.forName("UTF-8"));
                crc.update(bytes);
                ZipEntry versionsFile = new ZipEntry("version.txt");
                versionsFile.setMethod(ZipEntry.STORED);
                versionsFile.setSize(bytes.length);
                versionsFile.setCompressedSize(bytes.length);
                versionsFile.setCrc(crc.getValue());
                sduStream.putNextEntry(versionsFile);
                sduStream.write(bytes);

                for (Entry<String, Artifact> artifact : artifactMap.entrySet()) {
                    ZipEntry newEntry = new ZipEntry(artifact.getValue().entry);
                    try {
                        sduStream.putNextEntry(newEntry);
                        IOUtils.copy(artifact.getValue().stream, sduStream, 1024);
                    } catch (Exception e) {
                        getLog().info("Failed to write an entry.", e);
                    }
                }
            } catch (Exception e) {
                throw new MojoFailureException("Failed to create new SDU.", e);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Failed to create new SDU.", e);
        }

        getLog().info("SDU Created: " + sduFile.getAbsolutePath());
        mavenProjectHelper.attachArtifact(project, PackagingConstants.SDU_EXT, PackagingConstants.SDU_EXT, sduFile);
    }

    private Manifest createProjectManifest() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Created-By", "Device Package Plugin by Cisco Systems, Inc.");
        manifest.getMainAttributes().putValue("Version", project.getVersion());
        manifest.getMainAttributes().putValue("Name", project.getGroupId() + "." + project.getArtifactId());
        return manifest;
    }

    private int devicePackageCounter = 0;

    private void addArtifactToMap(String groupId, String artifactId, String versionValue, ZipEntry entry,
            InputStream stream, Map<String, Artifact> artifactMap, Manifest manifest) {

        String key = groupId + "." + artifactId;
        DefaultArtifactVersion versionComparor = new DefaultArtifactVersion(versionValue);

        boolean isDar = entry.getName().endsWith(PackagingConstants.DAR_EXT);

        if (artifactMap.containsKey(key)) {
            DefaultArtifactVersion otherVersionComparor = artifactMap.get(key).version;
            int compared = versionComparor.compareTo(otherVersionComparor);
            if (compared == 0) {
                return;
            }
            if (compared > 0) {
                getLog().info("Overriding " + otherVersionComparor + " with " + versionComparor + " for " + key);
                artifactMap.put(key, new Artifact(versionComparor, entry, stream));
                if (isDar) {
                    for (Attributes attrs : manifest.getEntries().values()) {
                        if (attrs.getValue(SduCreator.SDU_MANIFEST_ATTR_GROUP_ID).equals(groupId)
                                && attrs.getValue(SduCreator.SDU_MANIFEST_ATTR_ARTIFACT_ID).equals(artifactId)) {
                            attrs.putValue(SduCreator.SDU_MANIFEST_ATTR_VERSION, versionValue);
                        }
                    }
                }
            } else {
                getLog().info("Ignoring version " + versionComparor + " as a newer version is already included "
                        + otherVersionComparor + " for " + key);
                return;
            }
        } else {
            artifactMap.put(key, new Artifact(versionComparor, entry, stream));
            if (isDar) {
                Attributes attrs = new Attributes();
                attrs.putValue(SduCreator.SDU_MANIFEST_ATTR_GROUP_ID, groupId);
                attrs.putValue(SduCreator.SDU_MANIFEST_ATTR_ARTIFACT_ID, artifactId);
                attrs.putValue(SduCreator.SDU_MANIFEST_ATTR_VERSION, versionValue);
                manifest.getEntries().put(SduCreator.SDU_MANIFEST_ATTR_LOAD_ORDER + devicePackageCounter++, attrs);
            }
        }
    }

    protected class Artifact {
        DefaultArtifactVersion version;
        ZipEntry entry;
        InputStream stream;

        public Artifact(DefaultArtifactVersion version, ZipEntry entry, InputStream stream) {
            this.version = version;
            this.entry = entry;
            this.stream = stream;
        }
    }
}
