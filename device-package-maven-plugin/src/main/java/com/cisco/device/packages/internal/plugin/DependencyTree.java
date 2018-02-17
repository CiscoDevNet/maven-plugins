/******************************************************************************
 * Copyright (C) 2016-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
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