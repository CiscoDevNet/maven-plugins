/******************************************************************************
 * Copyright (C) 2013-2018 Cisco and/or its affiliates. All rights reserved.
 *
 * This source code is distributed under the terms of the MIT license.
 *****************************************************************************/
package com.cisco.common.plugin.helpers;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 *
 * @author danijoh2
 */
public class BuildHelper {

    /**
     * Private constructor as this is a static helper class.
     */
    private BuildHelper() {
    }

    /**
     * Produces a project error message. If run in an IDE this will produce an
     * error marker on the affected file, if this is run as a Maven CLI build
     * the error marker has no affect, but if throwErrors is set to
     * <code>true</code> this will produce an exception that will fail the Maven
     * build with the given message.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The error message.
     * @param throwErrors
     *            - <code>true</code> to raise an exception which will fail the
     *            build at the current point, <code>false</code> to simply tag
     *            the file with an error marker.
     *
     * @throws MojoExecutionException
     *             - If <code>throwErrors=true</code> this exception will be
     *             thrown.
     */
    public static void makeError(BuildContext buildContext, File file, String message, boolean throwErrors)
            throws MojoExecutionException {
        makeError(buildContext, file, message, null, throwErrors);
    }

    /**
     * Produces a project error message. If run in an IDE this will produce an
     * error marker on the affected file, if this is run as a Maven CLI build
     * the error marker has no affect, but if throwErrors is set to
     * <code>true</code> this will produce an exception that will fail the Maven
     * build with the given message.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The error message.
     * @param e
     *            - The cause, can be <code>null</code>.
     * @param throwErrors
     *            - <code>true</code> to raise an exception which will fail the
     *            build at the current point, <code>false</code> to simply tag
     *            the file with an error marker.
     *
     * @throws MojoExecutionException
     *             - If <code>throwErrors=true</code> this exception will be
     *             thrown.
     */
    public static void makeError(BuildContext buildContext, File file, String message, Throwable e, boolean throwErrors)
            throws MojoExecutionException {
        makeError(buildContext, file, message, 0, 0, e, throwErrors);
    }

    /**
     * Produces a project error message. If run in an IDE this will produce an
     * error marker on the affected file, if this is run as a Maven CLI build
     * the error marker has no affect, but if throwErrors is set to
     * <code>true</code> this will produce an exception that will fail the Maven
     * build with the given message.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The error message.
     * @param lineNumber
     *            - The line number of the file the error is seen on.
     *            <code>0</code> if unknown, <code>1</code> for the first line.
     * @param columnNumber
     *            - The column number of the file the error is seen on.
     *            <code>0</code> if unknown, <code>1</code> for the first
     *            column.
     * @param e
     *            - The cause, can be <code>null</code>.
     * @param throwErrors
     *            - <code>true</code> to raise an exception which will fail the
     *            build at the current point, <code>false</code> to simply tag
     *            the file with an error marker.
     *
     * @throws MojoExecutionException
     *             - If <code>throwErrors=true</code> this exception will be
     *             thrown.
     */
    public static void makeError(BuildContext buildContext, File file, String message, int lineNumber, int columnNumber,
            Throwable e, boolean throwErrors) throws MojoExecutionException {
        buildContext.addMessage(file, lineNumber, columnNumber, e == null ? message : message + ": " + e.getMessage(),
                BuildContext.SEVERITY_ERROR, e);
        if (throwErrors) {
            throw new MojoExecutionException(message, e);
        }
    }

    /**
     * Produces a project warning message. If run in an IDE this will produce a
     * warning marker on the affected file, if this is run as a Maven CLI build
     * it will produce a warning message in the Maven build log.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The warning message.
     */
    public static void makeWarning(BuildContext buildContext, File file, String message) {
        makeWarning(buildContext, file, message, null);
    }

    /**
     * Produces a project warning message. If run in an IDE this will produce a
     * warning marker on the affected file, if this is run as a Maven CLI build
     * it will produce a warning message in the Maven build log.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The warning message.
     * @param e
     *            - The cause, can be <code>null</code>.
     */
    public static void makeWarning(BuildContext buildContext, File file, String message, Throwable e) {
        makeWarning(buildContext, file, message, 0, 0, e);
    }

    /**
     * Produces a project warning message. If run in an IDE this will produce a
     * warning marker on the affected file, if this is run as a Maven CLI build
     * it will produce a warning message in the Maven build log.
     *
     * @param buildContext
     *            - The current maven build context.
     * @param file
     *            - The file that the error is seen in.
     * @param message
     *            - The warning message.
     * @param lineNumber
     *            - The line number of the file the error is seen on.
     *            <code>0</code> if unknown, <code>1</code> for the first line.
     * @param columnNumber
     *            - The column number of the file the error is seen on.
     *            <code>0</code> if unknown, <code>1</code> for the first
     *            column.
     * @param e
     *            - The cause, can be <code>null</code>.
     */
    public static void makeWarning(BuildContext buildContext, File file, String message, int lineNumber,
            int columnNumber, Throwable e) {
        buildContext.addMessage(file, lineNumber, columnNumber, e == null ? message : message + ": " + e.getMessage(),
                BuildContext.SEVERITY_WARNING, e);
    }
}
