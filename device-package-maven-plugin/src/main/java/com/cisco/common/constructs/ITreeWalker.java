/********************************************************************************
 * Copyright (c) 2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.common.constructs;

/**
 * An interface for walking through all nodes in a tree in a top-down pattern.
 *
 * @author danijoh2
 *
 */
@FunctionalInterface
public interface ITreeWalker<T> {

    /**
     * Processes the given node in the tree
     *
     * @param node
     *            The current node
     * @return <code>true</code> if further processing of leaf nodes in the
     *         current branch is desired, otherwise <code>false</code> to stop
     *         processing the current branch of the tree. In other words, if an
     *         error occurs while processing a parent item, return
     *         <code>false</code> to skip processing all of its children nodes.
     */
    public boolean processNode(Node<T> node);
}
