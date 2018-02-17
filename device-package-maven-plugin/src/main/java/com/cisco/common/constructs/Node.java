/******************************************************************************
 * Copyright (C) 2011-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
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