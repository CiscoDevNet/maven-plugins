/******************************************************************************
 * Copyright (c) 2011-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.internal.sdu;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import com.cisco.common.constructs.Node;
import com.cisco.device.packages.constants.PackagingConstants;
import com.cisco.device.packages.internal.plugin.DependencyTree;

/**
 * Can be used to create an SDU from a collection of Maven projects during a
 * Maven build. Expects at least the packaging phase to have been run prior to
 * invocation.
 *
 * @author danijoh2
 */
public class SduCreator {

    public static final String SDU_MANIFEST_ATTR_GROUP_ID = "xmp_groupId"; // $NON-NLS-1$
    public static final String SDU_MANIFEST_ATTR_ARTIFACT_ID = "xmp_artifactId"; // $NON-NLS-1$
    public static final String SDU_MANIFEST_ATTR_VERSION = "xmp_version"; // $NON-NLS-1$
    public static final String SDU_MANIFEST_ATTR_TYPE = "xmp_type"; // $NON-NLS-1$
    public static final String SDU_MANIFEST_ATTR_LOAD_ORDER = "devicePackage"; // $NON-NLS-1$

    private MavenProject project;
    private MavenSession mavenSession;
    private List<MavenProject> reactorProjects;
    List<Exclusion> extraExclusions;

    private RepositorySystem repoSystem;
    private RepositorySystemSession repoSession;
    private List<RemoteRepository> remoteRepos;

    /**
     * Can be used to specify a different name for the resulting SDU file.
     */
    private String sduName;

    private boolean includeAllReactorProjects = true;

    private Log log;

    private Map<String, String> dependencyVersionMap = new HashMap<String, String>();

    public SduCreator(MavenProject project, MavenSession mavenSession, RepositorySystem repoSystem,
            RepositorySystemSession repoSession, List<RemoteRepository> remoteRepos) {
        this.project = project;
        this.mavenSession = mavenSession;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
        this.remoteRepos = remoteRepos;
    }

    public void setSduName(String name) {
        this.sduName = name;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void setExtraExclusions(List<Exclusion> exclusions) {
        this.extraExclusions = exclusions;
    }

    public void setReactorProjects(List<MavenProject> reactorProjects) {
        this.reactorProjects = reactorProjects;
    }

    public void setIncludeAllReactorProjects(boolean includeAll) {
        this.includeAllReactorProjects = includeAll;
    }

    public File create() throws MojoExecutionException, MojoFailureException {

        Set<MavenProject> projects = new HashSet<MavenProject>();
        projects.add(project);
        if (PackagingConstants.POM_EXT.equals(project.getPackaging())) {
            projects.addAll(getSubProjects(project, reactorProjects));
        } else if (PackagingConstants.ALL_DP_PACKAGING.contains(project.getPackaging())) {
            if (project.getArtifact() == null || project.getArtifact().getFile() == null
                    || !project.getArtifact().getFile().exists()) {
                throw new MojoExecutionException(
                        "SDU Creation requires at least goal 'package' of the current project to be executed.");
            }
        }

        if (projects.isEmpty()) {
            log.warn("No projects found to create SDU from, skipping SDU creation.");
            return null;
        }

        Set<Artifact> artifacts = getArtifacts(projects);
        if (artifacts.isEmpty()) {
            if (extraExclusions != null) {
                StringBuffer excludesBuffer = new StringBuffer();
                Iterator<Exclusion> iter = extraExclusions.iterator();
                while (iter.hasNext()) {
                    Exclusion ex = iter.next();
                    excludesBuffer.append(ex.getGroupId()).append(":").append(ex.getArtifactId());
                    if (iter.hasNext()) {
                        excludesBuffer.append(", ");
                    }
                }
                throw new MojoFailureException(
                        "No artifacts found to include in the SDU. Check your project dependencies and use of exclusions. Received the following excludes: ["
                                + excludesBuffer.toString() + "]");
            } else {
                throw new MojoFailureException(
                        "No artifacts found to include in the SDU. Check your project dependencies and use of dependency exclusions.");
            }
        }

        Map<File, String> files = createSDUFileMap(artifacts);

        File baseDir = new File(project.getBuild().getDirectory());
        if (!baseDir.exists()) {
            // Create the "target" directory to place the .sdu into
            baseDir.mkdir();
        }

        // Check if user supplied name for .sdu file
        if (sduName == null) {
            // They did not, default to <artifactId>-<version>.sdu
            sduName = project.getArtifactId() + PackagingConstants.DASH + project.getVersion();
            if (!sduName.endsWith(PackagingConstants.DOT)) {
                sduName += PackagingConstants.DOT;
            }
            sduName += PackagingConstants.SDU_EXT;
        }

        File sdu = null;
        try {
            sdu = createSDU(files, createManifest(artifacts), baseDir, sduName);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        return sdu;
    }

    protected Set<MavenProject> getProjectRoots(Set<MavenProject> projects) {
        Set<MavenProject> projectRoots = new HashSet<MavenProject>();
        for (MavenProject project : projects) {
            boolean isDependOn = false;
            for (MavenProject otherProject : projects) {
                if (project == otherProject) {
                    continue;
                }
                isDependOn = hasDependency(otherProject, project);
                if (isDependOn) {
                    break;
                }
            }
            if (!isDependOn) {
                projectRoots.add(project);
            }
        }
        return projectRoots;
    }

    protected boolean hasDependency(MavenProject rootProject, MavenProject dependentProject) {
        if (rootProject.getDependencies() != null) {
            for (Dependency dep : rootProject.getDependencies()) {
                if (dep.getGroupId().equals(dependentProject.getGroupId())
                        && dep.getArtifactId().equals(dependentProject.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Set<MavenProject> getSubProjects(MavenProject project, List<MavenProject> reactorProjects) {
        Set<MavenProject> subProjects = new HashSet<MavenProject>();
        if (reactorProjects != null) {
            for (MavenProject subProject : reactorProjects) {
                if (isModule(project, subProject)) {
                    if (PackagingConstants.DAR_EXT.equals(subProject.getPackaging()) || (includeAllReactorProjects
                            && (PackagingConstants.FEATURE_EXT.equals(subProject.getPackaging())
                                    || PackagingConstants.XDE_EXT.equals(subProject.getPackaging())))) {
                        subProjects.add(subProject);
                    }
                    subProjects.addAll(getSubProjects(subProject, reactorProjects));
                }
            }
        }
        return subProjects;
    }

    protected boolean isModule(MavenProject project, MavenProject subProject) {
        for (String module : project.getModules()) {
            File moduleFile = new File(project.getBasedir(), module);
            if (subProject.getBasedir().equals(moduleFile) || subProject.getFile().equals(moduleFile)) {
                return true;
            }
        }
        return false;
    }

    protected Set<Artifact> getArtifacts(Set<MavenProject> projects)
            throws MojoExecutionException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<Artifact>();
        for (MavenProject proj : projects) {
            if (PackagingConstants.ALL_DP_PACKAGING.contains(proj.getPackaging())) {
                Artifact artifact = proj.getArtifact();
                while (artifact.getFile() == null || !artifact.getFile().exists()) {
                    if (mavenSession.getResult().hasExceptions()) {
                        throw new MojoExecutionException(
                                "Stopping build of " + project.getId() + " because of upstream build failures.");
                    }
                    BuildSummary projectSummary = mavenSession.getResult().getBuildSummary(proj);
                    if (projectSummary instanceof BuildFailure) {
                        throw new MojoExecutionException(
                                "Failed to package SDU because of a build failure in project " + proj.getId());
                    } else if (projectSummary instanceof BuildSuccess) {
                        break;
                    }

                    // Need to use the artifact in the reactor, wait until it is
                    // ready if we have to
                    info(project.getId() + " is waiting on project in reactor to finish: " + proj.getId());
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {

                    }
                }
                artifacts.add(artifact);
            }

            addAllDependencyArtifacts(proj.getModel(), null, artifacts);
        }

        return artifacts;
    }

    private void addAllDependencyArtifacts(Model model, List<Exclusion> exclusions, Set<Artifact> artifacts)
            throws MojoExecutionException {

        if (model.getDependencies() == null) {
            return;
        }

        Properties properties = getAllProperties(model);
        for (Dependency dep : model.getDependencies()) {
            if (!PackagingConstants.ALL_DP_PACKAGING.contains(dep.getType())) {
                continue;
            }

            // We can't process standard exclusions for the parent device
            // profile, because in order to load a parent package in the runtime
            // we will need its original feature dependencies present. We can
            // process any extra exclusions passed explicitly though.
            if (!PackagingConstants.DAR_EXT.equals(dep.getType()) || exclusions == null) {
                exclusions = extraExclusions;
            } else if (extraExclusions != null) {
                exclusions.addAll(extraExclusions);
            }

            if (exclusions != null) {
                boolean skip = false;
                for (Exclusion exclusion : exclusions) {
                    if (matches(exclusion, dep)) {
                        log.debug("Ignoring dependency " + dep + " because of exclusion " + "{groupId="
                                + exclusion.getGroupId() + ", artifactId=" + exclusion.getArtifactId()
                                + "} from project {groupId=" + model.getGroupId() + ", artifactId="
                                + model.getArtifactId() + "}");
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
            }

            String version = resolve(properties, dep.getVersion());
            boolean found = false;
            for (Artifact artifact : artifacts) {
                if (artifact.getArtifactId().equals(dep.getArtifactId())
                        && artifact.getGroupId().equals(dep.getGroupId()) && artifact.getVersion().equals(version)
                        && artifact.getType().equals(dep.getType())) {
                    found = true;
                    break;
                }
            }

            MavenProject reactorProject = null;
            for (MavenProject buildProject : reactorProjects) {
                if (buildProject.getGroupId().equals(dep.getGroupId())
                        && buildProject.getArtifactId().equals(dep.getArtifactId())
                        && buildProject.getVersion().equals(version)) {
                    reactorProject = buildProject;
                    break;
                }
            }

            if (!found) {
                if (reactorProject == null) {
                    // Not in the reactor, need to get it from repository
                    org.eclipse.aether.artifact.Artifact art = new org.eclipse.aether.artifact.DefaultArtifact(
                            dep.getGroupId(), dep.getArtifactId(), dep.getType(), version);
                    art = getArtifact(art);

                    // danijoh2 - We must specify a non-null classifier,
                    // otherwise the contructor tries to
                    // access the artifact handler (null) in the case the
                    // classifier is specified as null.
                    String classifier = dep.getClassifier() == null ? dep.getType() : dep.getClassifier();
                    Artifact artifact = new org.apache.maven.artifact.DefaultArtifact(art.getGroupId(),
                            art.getArtifactId(), art.getVersion(), dep.getScope(), dep.getType(), classifier, null);
                    artifact.setFile(art.getFile());
                    artifacts.add(artifact);
                } else {
                    Artifact artifact = reactorProject.getArtifact();
                    while (artifact.getFile() == null || !artifact.getFile().exists()) {
                        if (mavenSession.getResult().hasExceptions()) {
                            throw new MojoExecutionException(
                                    "Stopping build of " + project.getId() + " because of upstream build failures.");
                        }
                        BuildSummary projectSummary = mavenSession.getResult().getBuildSummary(reactorProject);
                        if (projectSummary instanceof BuildFailure) {
                            throw new MojoExecutionException(
                                    "Failed to package SDU because of a build failure in project "
                                            + reactorProject.getId());
                        } else if (projectSummary instanceof BuildSuccess) {
                            break;
                        }

                        // Need to use the artifact in the reactor, wait until
                        // it is ready if we have to
                        info(project.getId() + " is waiting on project in reactor to finish: "
                                + reactorProject.getId());
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {

                        }
                    }
                    artifacts.add(artifact);
                }
            }

            Model depModel = null;
            if (reactorProject != null) {
                depModel = reactorProject.getModel();
            } else {
                depModel = getModelForDependency(dep, properties);
            }

            addAllDependencyArtifacts(depModel, dep.getExclusions(), artifacts);
        }
    }

    private boolean matches(Exclusion exclusion, Dependency dep) {
        if (!StringUtils.isEmpty(exclusion.getGroupId())) {
            if (exclusion.getGroupId().contains("*")) {
                String pattern = exclusion.getGroupId().replaceAll("\\*", ".*");
                if (!dep.getGroupId().matches(pattern)) {
                    return false;
                }
            } else {
                if (!dep.getGroupId().equals(exclusion.getGroupId())) {
                    return false;
                }
            }
        }
        if (!StringUtils.isEmpty(exclusion.getArtifactId())) {
            if (exclusion.getArtifactId().contains("*")) {
                String pattern = exclusion.getArtifactId().replaceAll("\\*", ".*");
                if (!dep.getArtifactId().matches(pattern)) {
                    return false;
                }
            } else {
                if (!dep.getArtifactId().equals(exclusion.getArtifactId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private Properties getAllProperties(Model depModel) throws MojoExecutionException {

        Properties properties = depModel.getProperties();
        if (properties == null) {
            properties = new Properties();
        }

        Parent parent = depModel.getParent();

        // Add project.version property
        String version = depModel.getVersion();
        if (version == null || version.isEmpty()) {
            if (parent != null) {
                version = parent.getVersion();
            }
        }
        if (version != null && !version.isEmpty()) {
            properties.put("project.version", version);
        }

        while (parent != null) {
            Model parentModel = getParentModel(parent);
            Properties props = parentModel.getProperties();
            if (props != null) {
                properties.putAll(props);
            }
            parent = parentModel.getParent();
        }

        return properties;
    }

    private String resolve(Properties properties, String value) {

        while (value.contains("${")) {
            int start = value.indexOf("${");
            int end = value.indexOf("}");
            if (start == -1 || end == -1 || end <= start) {
                break;
            }

            String orig = value.substring(start, end + 1);
            String key = orig.substring(start + 2, orig.length() - 1);
            if (!properties.containsKey(key)) {
                break;
            }

            String resolved = properties.getProperty(key);
            value = value.replace(orig, resolved);
        }
        if (value.contains("${")) {
            info("Failed to resolve a property in the given value: " + value);
        }
        return value;
    }

    protected Map<File, String> createSDUFileMap(Set<Artifact> artifacts) {

        Map<File, String> files = new TreeMap<File, String>();
        for (Artifact art : artifacts) {

            File file = art.getFile();
            if (file == null || !file.exists()) {
                info("No file found for " + art.getGroupId() + PackagingConstants.DOT + art.getArtifactId()
                        + PackagingConstants.DASH + art.getVersion());
                continue;
            }

            // Replace .jar extension with the correct one (.dar, .feature,
            // etc.)
            String fileName = file.getName();
            if (fileName.endsWith(PackagingConstants.JAR_EXT)) {
                String ext = repoSession.getArtifactTypeRegistry().get(art.getType()).getExtension();
                if (ext.equals(PackagingConstants.JAR_EXT)) {
                    continue;
                }
                fileName = fileName.substring(0, fileName.length() - PackagingConstants.JAR_EXT.length()) + ext;
            }

            if (fileName.endsWith(PackagingConstants.DAR_EXT)) {
                // Device Profile, place at root of the .sdu
                files.put(file, fileName);
            } else {
                // Feature or XDE, get path ACPM expects
                fileName = getACPMPath(art) + fileName;
                files.put(file, fileName);
            }
            // Save the version for when we create the manifest entries
            dependencyVersionMap.put(art.getGroupId() + PackagingConstants.DOT + art.getArtifactId(),
                    art.getBaseVersion());
        }

        for (String path : files.values()) {
            info("Adding: " + path);
        }
        return files;
    }

    protected File createSDU(Map<File, String> files, Manifest mf, File baseDir, String name)
            throws IOException, MojoExecutionException {

        File sduFile = new File(baseDir, name);
        if (sduFile.exists()) {
            // .sdu file already exists, delete it
            info("Deleting " + sduFile.getCanonicalPath());
            if (!sduFile.delete()) {
                throw new IOException("Failed to delete the already existing file " + sduFile.getCanonicalPath());
            }
        }

        // Create the .sdu with the manifest
        try (FileOutputStream jarStream = new FileOutputStream(sduFile)) {
            try (JarOutputStream sduJar = new JarOutputStream(jarStream, mf)) {
                int read;
                byte[] buffer = new byte[1024];
                CRC32 crc = new CRC32();

                // Create versions.txt in SDU
                String versionText = project.getGroupId() + "." + project.getArtifactId() + "-" + project.getVersion();
                byte[] bytes = versionText.getBytes(Charset.forName("UTF-8"));
                crc.update(bytes);
                ZipEntry versionsFile = new ZipEntry("version.txt");
                versionsFile.setMethod(ZipEntry.STORED);
                versionsFile.setSize(bytes.length);
                versionsFile.setCompressedSize(bytes.length);
                versionsFile.setCrc(crc.getValue());
                sduJar.putNextEntry(versionsFile);
                sduJar.write(bytes);

                // Add all device package artifacts
                Set<File> keys = files.keySet();
                for (File file : keys) {

                    String newPath = files.get(file);
                    // Create Parent folder structure
                    if (newPath.contains(JarSeparator)) {
                        createParent(sduJar, newPath.substring(0, newPath.lastIndexOf(JarSeparator)));
                    }

                    // Gather CRC
                    BufferedInputStream in = null;
                    try {
                        in = new BufferedInputStream(new FileInputStream(file), 1024);
                        crc.reset();
                        while ((read = in.read(buffer)) != -1) {
                            crc.update(buffer, 0, read);
                        }
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                    }

                    // Copy the file into the .sdu
                    ZipEntry entry = new ZipEntry(newPath);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setCompressedSize(file.length());
                    entry.setSize(file.length());
                    entry.setCrc(crc.getValue());
                    sduJar.putNextEntry(entry);

                    in = null;
                    try {
                        in = new BufferedInputStream(new FileInputStream(file), 1024);
                        while ((read = in.read(buffer)) != -1) {
                            sduJar.write(buffer, 0, read);
                        }
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                    }
                }
            }
        }

        return sduFile;
    }

    /** This is the separator that JAR files require **/
    private static String JarSeparator = "/"; // $NON-NLS-1$

    /**
     * This is the artifact path in the jar that ACPM requires
     */
    private String getACPMPath(Artifact art) {
        return art.getGroupId().replace(".", JarSeparator) + JarSeparator + art.getArtifactId() + JarSeparator
                + art.getBaseVersion() + JarSeparator;
    }

    /**
     * Since we are adding files to the JAR 'randomly' we will keep track of
     * what folders we have already created in the JAR so we do not accidently
     * overwrite them with a new entry.
     */
    Set<String> jarMap = new HashSet<String>();

    /**
     * Creates all the parent folders for a file path.
     */
    private void createParent(JarOutputStream jarSDU, String path) throws IOException {

        if (path == null || path.isEmpty())
            return;

        // Recursively create parent folder
        if (path.contains(JarSeparator)) {
            createParent(jarSDU, path.substring(0, path.lastIndexOf(JarSeparator)));
        }

        // The path MUST end with the sepatrator character
        if (!path.endsWith(JarSeparator)) {
            path += JarSeparator;
        }

        // If we have already added it then return
        if (jarMap.contains(path)) {
            return;
        }

        // And the JAR entry
        JarEntry entry = new JarEntry(path);
        jarSDU.putNextEntry(entry);
        jarSDU.closeEntry();
        // And add it to the map.
        jarMap.add(path);
    }

    /**
     * Creates the manifest for the SDU. Traverses over the list of device
     * profile projects to specify the load order in the manifest.
     *
     * @param deviceProfileProjects
     *            The device profile projects that are part of this build
     * @return The Manifest
     * @throws MojoExecutionException
     */
    private Manifest createManifest(Set<Artifact> artifacts) throws MojoExecutionException {

        /** Header info **/
        Manifest mf = new Manifest();
        Attributes mfAttr = mf.getMainAttributes();
        mfAttr.putValue("Manifest-Version", "1.0");
        mfAttr.putValue("Created-By", "Cisco Systems, Inc. XMP");

        /** Specify device profile load order for ACPM **/
        Map<String, Attributes> entries = mf.getEntries();
        DependencyTree dependencyTree = createDependencyTree(artifacts);
        for (Node<Model> root : dependencyTree.getRoots()) {
            addEntry(entries, root);
        }

        return mf;
    }

    /**
     * Counter used by the following function to keep track of how many device
     * profiles have been added to the manifest file already.
     */
    private int numEntries = 0;

    /**
     * Recursively checks for any dependencies on a device profile in a POM. If
     * one is found it will be added to the list of entries with a number
     * associating it with the order it needs to be loaded by ACPM. The highest
     * order (parent) profile will be marked as devicePackage0 signifying it
     * should be loaded first, and the lowest order (child) profile will be
     * marked as devicePackageN where N is the number of parent device profiles
     * that were found.
     *
     * @param project
     *            - The projects to process
     *
     * @throws MojoExecutionException
     *             - If any error occurs. Such as not being able to load the
     *             model for a parent dependency from Nexus or not having
     *             permissions to read the file.
     */
    private DependencyTree createDependencyTree(Set<Artifact> artifacts) throws MojoExecutionException {

        DependencyTree dependencyTree = new DependencyTree();

        for (Artifact artifact : artifacts) {
            Model model = getModel(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            Properties properties = getAllProperties(model);
            if (model.getPackaging().equals(PackagingConstants.POM_EXT)) {
                for (Dependency dep : model.getDependencies()) {
                    if (PackagingConstants.ALL_DP_PACKAGING.contains(dep.getType())) {
                        recursiveAddModelToTree(dependencyTree, getModelForDependency(dep, properties));
                    }
                }
            } else {
                recursiveAddModelToTree(dependencyTree, model);
            }
        }

        return dependencyTree;
    }

    private Node<Model> recursiveAddModelToTree(DependencyTree dependencyTree, Model model)
            throws MojoExecutionException {

        Node<Model> returnNode = dependencyTree.find(model);
        if (returnNode != null) {
            return returnNode;
        }

        Node<Model> parentNode = null;

        Properties properties = getAllProperties(model);
        List<Dependency> deps = model.getDependencies();
        for (Dependency dep : deps) {
            if (PackagingConstants.ALL_DP_PACKAGING.contains(dep.getType())) {
                String version = dep.getVersion();
                if (version == null || version.startsWith("[") || version.startsWith("(")) {
                    String id = dep.getGroupId() + PackagingConstants.DOT + dep.getArtifactId();
                    // We saved a map of all the device package versions, lets
                    if (dependencyVersionMap != null && dependencyVersionMap.containsKey(id)) {
                        String resolvedVersion = dependencyVersionMap.get(id);
                        dep.setVersion(resolvedVersion);
                    }
                }
                Model depModel = getModelForDependency(dep, properties);
                if (depModel == null) {
                    String id = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion() + ":pom";
                    error("Could not find model for " + id + ". SDU manifest may be incomplete.");
                    continue;
                }
                parentNode = dependencyTree.find(depModel);
                if (parentNode == null) {
                    parentNode = recursiveAddModelToTree(dependencyTree, depModel);
                }
            }
        }

        return parentNode == null ? dependencyTree.addRoot(model) : parentNode.addChild(model);
    }

    private void addEntry(Map<String, Attributes> entries, Node<Model> node) throws MojoExecutionException {

        Model model = node.getData();
        Attributes attr = new Attributes();
        String groupId = model.getGroupId();
        if (StringUtils.isEmpty(groupId)) {
            if (model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
        }
        String artifactId = model.getArtifactId();
        String version = model.getVersion();
        if (StringUtils.isEmpty(version)) {
            if (model.getParent() != null) {
                version = model.getParent().getVersion();
            }
        }
        if (StringUtils.isEmpty(groupId) || StringUtils.isEmpty(artifactId) || StringUtils.isEmpty(version)) {
            throw new MojoExecutionException("An error occurred resolving groupId, artifactId, or version of model: "
                    + model + ". groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version);
        }
        attr.putValue(SDU_MANIFEST_ATTR_GROUP_ID, groupId);
        attr.putValue(SDU_MANIFEST_ATTR_ARTIFACT_ID, artifactId);
        attr.putValue(SDU_MANIFEST_ATTR_VERSION, version);
        attr.putValue(SDU_MANIFEST_ATTR_TYPE, node.getData().getPackaging());
        entries.put(SDU_MANIFEST_ATTR_LOAD_ORDER + numEntries++, attr);

        for (Node<Model> childNode : node.getChildren()) {
            addEntry(entries, childNode);
        }
    }

    /**
     * Attempts to load the maven model for a dependency by querying Aether for
     * the pom file from the nexus repository and then loading it as a POM.
     *
     * @param dep
     *            - The dependency
     *
     * @return - The POM
     * @throws MojoExecutionException
     *             - If any error occurs
     */
    private Model getModelForDependency(Dependency dep, Properties properties) throws MojoExecutionException {
        return getModel(dep.getGroupId(), dep.getArtifactId(), resolve(properties, dep.getVersion()));
    }

    /**
     * Uses Aether to find the pom file that is in the nexus repository for this
     * dependency.
     *
     * @param dep
     *            - The dependency
     *
     * @return - The pom file, null if it was not found.
     * @throws MojoExecutionException
     *             - If any error occurs
     */
    private Model getParentModel(Parent parent) throws MojoExecutionException {
        return getModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    private Model getModel(String groupId, String artifactId, String version) throws MojoExecutionException {

        if (reactorProjects != null) {
            Model modelInReactor = reactorProjects.parallelStream()
                    .filter(project -> project.getGroupId().equals(groupId)
                            && project.getArtifactId().equals(artifactId) && project.getVersion().equals(version))
                    .map(project -> project.getModel()).findFirst().orElse(null);
            if (modelInReactor != null) {
                debug("Artifact found in reactor: " + groupId + ":" + artifactId + ":" + version + ":pom");
                return modelInReactor;
            }
        }
        org.eclipse.aether.artifact.Artifact art = new org.eclipse.aether.artifact.DefaultArtifact(groupId, artifactId,
                PackagingConstants.POM_EXT, version);
        return readModel(getArtifact(art).getFile());
    }

    /**
     * Attempts to load the maven model for a dependency by querying Aether for
     * the pom file from the nexus repository and then loading it as a POM.
     *
     * @param dep
     *            - The dependency
     *
     * @return - The POM
     * @throws MojoExecutionException
     *             - If any error occurs
     */
    private Model readModel(File pomFile) throws MojoExecutionException {
        Model model = null;
        if (pomFile != null && pomFile.exists()) {
            DefaultModelReader reader = new DefaultModelReader();
            try {
                model = reader.read(pomFile, null);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return model;
    }

    /**
     * Gets the artifact from the Nexus using Aether
     *
     * @param artifactName
     *            - Artifact name in format groupId:artifactId:type:version
     *
     * @return - The resulting artifact file
     */
    private org.eclipse.aether.artifact.Artifact getArtifact(org.eclipse.aether.artifact.Artifact artifact)
            throws MojoExecutionException {

        if (artifact.getVersion().startsWith("[") || artifact.getVersion().startsWith("(")) {
            VersionRangeRequest versionRequest = new VersionRangeRequest();
            versionRequest.setArtifact(artifact);
            versionRequest.setRepositories(remoteRepos);
            try {
                VersionRangeResult result = repoSystem.resolveVersionRange(repoSession, versionRequest);

                Iterator<Version> iter = result.getVersions().iterator();
                Version v = null;
                while (iter.hasNext()) {
                    v = iter.next();
                }
                artifact = new org.eclipse.aether.artifact.DefaultArtifact(artifact.getGroupId(),
                        artifact.getArtifactId(), artifact.getExtension(), v.toString());
            } catch (VersionRangeResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        try {
            ArtifactRequest request = new ArtifactRequest(artifact, remoteRepos, null);
            ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
            return result.getArtifact();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void debug(String msg) {
        if (log != null) {
            log.debug(msg);
        }
    }

    private void info(String msg) {
        if (log != null) {
            log.info(msg);
        }
    }

    private void error(String msg) {
        if (log != null) {
            log.error(msg);
        }
    }
}