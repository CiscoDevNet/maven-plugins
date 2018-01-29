/******************************************************************************
 * Copyright (c) 2016-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 *****************************************************************************/
package com.cisco.common.constructs;

import java.util.Collection;

/**
 * A tree can be used to group objects where parent/child relationship is
 * important.
 *
 * @author danijoh2
 *
 * @param <T>
 *            The data type stored in the tree
 */
public interface ITree<T> {

    /**
     * Gets the root nodes of the tree.
     *
     * @return The root nodes.
     */
    public Collection<Node<T>> getRoots();

    /**
     * Attempts to find the given data in the tree.
     *
     * @param data
     *            The data to find
     * @return The Node containing the data if found, otherwise null.
     */
    public Node<T> find(T data);

    /**
     * Adds the given data as a root node in the tree.
     *
     * @param data
     *            The data to add
     * @return The root node containing the data, as added to the tree.
     */
    public Node<T> addRoot(T data);

    /**
     * Compares two data elements
     *
     * @param a
     *            First data element
     * @param b
     *            Second data element
     * @return true if the two objects are equal, otherwise false.
     */
    public boolean compareEqual(T a, T b);

    /**
     * Helper method to walk over the elements in the tree depth-first. This
     * method will continue walking over the tree as long as the callback
     * returns true. The callback can return false to stop the processing of the
     * rest of the sub-tree of the current node. Implementations may choose to
     * multi-thread the processing of the tree.
     *
     * @param callback
     *            - The callback class to allow to process the data of each
     *            node.
     */
    public void walkTree(ITreeWalker<T> callback);

    /**
     * Helper method to walk over the elements in the sub-tree depth-first.This
     * method will continue walking over the sub-tree as long as the callback
     * returns true. The callback can return false to stop the processing of the
     * rest of the sub-tree of the current node.
     *
     * @param subTreeRootNode
     *            - The starting node to process, will process this node, and
     *            then all children in a top-down manner.
     * @param callback
     *            - The callback class to allow to process the data of each
     *            node.
     */
    public void walkSubTree(Node<T> subTreeRootNode, ITreeWalker<T> callback);
}
