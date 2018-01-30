/********************************************************************************
 * Copyright (c) 2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.internal.plugin;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;

import com.cisco.common.constructs.AbstractTree;

/**
 *
 * @author danijoh2
 */
public class ReactorHierarchyTree extends AbstractTree<MavenProject> {

    @Override
    public boolean compareEqual(MavenProject p1, MavenProject p2) {
        return p1 == p2 ? true
                : StringUtils.equals(p1.getGroupId(), p2.getGroupId())
                        && StringUtils.equals(p1.getArtifactId(), p2.getArtifactId())
                        && StringUtils.equals(p1.getVersion(), p2.getVersion())
                        && StringUtils.equals(p1.getPackaging(), p2.getPackaging());
    }
}