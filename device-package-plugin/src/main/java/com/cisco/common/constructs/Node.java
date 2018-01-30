/******************************************************************************
 * Copyright (c) 2011-2018 by Cisco Systems, Inc. and/or its affiliates.
 * All rights reserved.
 *
 * This software is made available under the CISCO SAMPLE CODE LICENSE
 * Version 1.1. See LICENSE.TXT at the root of this project for more information.
 *
 ********************************************************************************/
package com.cisco.common.constructs;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a Node in a Tree, allows up/down traversal to both parent items in
 * the tree, and child items in the tree.
 *
 * @author danijoh2
 *
 * @param <T>
 *            - The type of data that will be stored in the Node.
 */
public class Node<T> {

    private T data;
    private Node<T> parent;
    private Collection<Node<T>> children;

    public Node(T data, Node<T> parent) {
        this.data = data;
        this.parent = parent;
        children = new ArrayList<Node<T>>();
    }

    public T getData() {
        return data;
    }

    public Node<T> getParent() {
        return parent;
    }

    public Collection<Node<T>> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public Node<T> addChild(T child) {
        Node<T> childNode = new Node<T>(child, this);
        children.add(childNode);
        return childNode;
    }

    public void addChild(Node<T> child) {
        child.parent = this;
        children.add(child);
    }
}