package net.agilhard.maven.plugins.jpackager;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Windows Options for JPackager
 * @author Bernd Eilers
 *
 */
public class JPackagerWindowsOptions 
{

    /**
     * Adds the application to the system menu
     * <code>--win-menu</code>
     */
    @Parameter( required = false, readonly = false )
    boolean menu;

    /**
     * Start Menu group this application is placed in
     * <code>--win-menu-group &lt;menu group name&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String menuGroup;

    /**
     * Request to perform an install on a per-user basis
     * <code>--win-per-user-install/code>
     */
    @Parameter( required = false, readonly = false )
    boolean perUserInstall;
    
    /**
     * Adds a dialog to enable the user to choose a directory in which the product is installed
     * <code>--win-dir-chooser</code>
     */
    @Parameter( required = false, readonly = false )
    boolean dirChooser;
    
    /**
     *  Name of the application for registry references.
     *  The default is the Application Name with only alphanumerics, dots, and dashes (no whitespace)
     * <code>--win-registry-name &lt;registry name&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String registryName;
    
    /**
     *  UUID associated with upgrades for this package
     * <code>--win-upgrade-uuid &lt;id string&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String upgradeUUID;
    
    /**
     * Creates a desktop shortcut for the application
     * <code>--win-shortcut</code>
     */
    @Parameter( required = false, readonly = false )
    boolean shortcut;

    /**
     * Creates a console launcher for the application, 
     * should be specified for application which requires console interactions
     * <code>--win-console</code>
     */
    @Parameter( required = false, readonly = false )
    boolean console;

}
