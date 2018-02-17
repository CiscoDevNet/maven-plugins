/******************************************************************************
 * Copyright (C) 2011-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.common.constructs;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A basic tree implementation for storing data in a hierarchical fashion.
 *
 * @author danijoh2
 *
 * @param <T>
 *            - The type of objects that will be stored in the tree.
 */
public abstract class AbstractTree<T> implements ITree<T> {

    Collection<Node<T>> roots = new ArrayList<Node<T>>();

    /**
     * {@inheritDoc}
     */
    public Collection<Node<T>> getRoots() {
        return roots;
    }

    /**
     * {@inheritDoc}
     */
    public Node<T> find(T data) {

        for (Node<T> root : roots) {
            Node<T> node = internalFind(root, data);
            if (node != null) {
                return node;
            }
        }

        return null;
    }

    private Node<T> internalFind(Node<T> currentNode, T data) {

        if (compareEqual(currentNode.getData(), data)) {
            return currentNode;
        }

        for (Node<T> node : currentNode.getChildren()) {
            Node<T> returnNode = internalFind(node, data);
            if (returnNode != null) {
                return returnNode;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Node<T> addRoot(T data) {
        Node<T> node = new Node<T>(data, null);
        roots.add(node);
        return node;
    }

    /**
     * {@inheritDoc}
     */
    public abstract boolean compareEqual(T a, T b);

    /**
     * {@inheritDoc}
     */
    public void walkTree(final ITreeWalker<T> callback) {
        roots.stream().forEach(root -> walkSubTree(root, callback));
    }

    /**
     * {@inheritDoc}
     */
    public void walkSubTree(Node<T> startNode, ITreeWalker<T> callback) {

        if (callback.processNode(startNode) && startNode.hasChildren()) {
            startNode.getChildren().stream().forEach(child -> walkSubTree(child, callback));
        }
    }
}