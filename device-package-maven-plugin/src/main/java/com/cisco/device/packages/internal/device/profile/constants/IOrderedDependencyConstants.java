/******************************************************************************
 * Copyright (c) 2009-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.internal.device.profile.constants;

/**
 *
 * @author danijoh2
 */
public interface IOrderedDependencyConstants {

    public static final String RESOURCES_PATH = "src/main/resources";

    public static final String ORDERED_FEATURE_FILE_NAME = RESOURCES_PATH + '/' + ".orderedFeatures"; //$NON-NLS-1$

    public static final String CDP_DIRECTORY = RESOURCES_PATH + '/' + "cdp"; //$NON-NLS-1$

    public static final String DEP_ROOT_NODE = "orderedFeatureList"; //$NON-NLS-1$

    public static final String DEPENDENCY_NODE = "dependency"; //$NON-NLS-1$

    public static final String GROUP_ID_NODE = "groupId"; //$NON-NLS-1$

    public static final String ARTIFACT_ID_NODE = "artifactId"; //$NON-NLS-1$

    public static final String VERSION_NODE = "version"; //$NON-NLS-1$

    public static final String TYPE_NODE = "type"; //$NON-NLS-1$

    public static final String OVERRIDE = "override"; //$NON-NLS-1$

    public static final String REPLACE = "replace"; // $NON-NLS-N1$

    public static final String REPLACEE = "replacee"; // $NON-NLS-N1$

}
