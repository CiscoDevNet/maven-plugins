/******************************************************************************
 * Copyright (c) 2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.constants;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author danijoh2
 */
public class PackagingConstants {

    public static final String SDU_EXT = "sdu"; // $NON-NLS-1$
    public static final String DAR_EXT = "dar"; // $NON-NLS-1$
    public static final String FEATURE_EXT = "feature"; // $NON-NLS-1$
    public static final String XDE_EXT = "xar"; // $NON-NLS-1$
    public static final String JAR_EXT = "jar"; // $NON-NLS-1$
    public static final String POM_EXT = "pom"; // $NON-NLS-1$
    public static final String DOT = "."; // $NON-NLS-1$
    public static final String DASH = "-"; // $NON-NLS-1$
    public static final String COLON = ":"; // $NON-NLS-1$

    public static final List<String> ALL_DP_PACKAGING = Arrays.asList(
            new String[] { PackagingConstants.DAR_EXT, PackagingConstants.FEATURE_EXT, PackagingConstants.XDE_EXT });
}
