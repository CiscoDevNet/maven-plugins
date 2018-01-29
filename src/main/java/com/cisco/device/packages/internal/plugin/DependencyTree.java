/******************************************************************************
 * Copyright (c) 2016-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.device.packages.internal.plugin;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;

import com.cisco.common.constructs.AbstractTree;

/**
 *
 * @author danijoh2
 */
public class DependencyTree extends AbstractTree<Model> {

    public boolean compareEqual(Model a, Model b) {
        return a == b ? true
                : StringUtils.equals(a.getGroupId(), b.getGroupId())
                        && (StringUtils.equals(a.getArtifactId(), b.getArtifactId())
                                && StringUtils.equals(a.getVersion(), b.getVersion())
                                && StringUtils.equals(a.getPackaging(), b.getPackaging()));
    }
}