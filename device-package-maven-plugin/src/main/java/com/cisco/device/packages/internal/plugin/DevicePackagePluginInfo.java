/********************************************************************************
 * Copyright (c) 2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.internal.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.cisco.device.packages.mojos.ConfigurePluginMojo;

/**
 * Reads this plugins release information from the pom.properties that is
 * packaged in by the Maven build process. Used by the
 * {@link ConfigurePluginMojo} to read the version of this plugin that is
 * executing when configuring projects to use this plugin.
 *
 * @author danijoh2
 */
public class DevicePackagePluginInfo {

    public static String getVersion() throws IOException {
        try (InputStream stream = DevicePackagePluginInfo.class
                .getResourceAsStream("/META-INF/maven/com.cisco.maven.plugins/device-package-maven-plugin/pom.properties")) { // $NON-NLS-1$
            Properties pluginProperties = new Properties();
            pluginProperties.load(stream);
            return pluginProperties.getProperty("version"); // $NON-NLS-1$
        }
    }
}
