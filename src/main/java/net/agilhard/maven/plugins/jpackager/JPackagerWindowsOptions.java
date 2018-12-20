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
     * <p>
     * <code>--win-menu</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public boolean menu;

    /**
     * Start Menu group this application is placed in
     * <p>
     * <code>--win-menu-group &lt;menu group name&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public String menuGroup;

    /**
     * Request to perform an install on a per-user basis.
     * <p>
     * <code>--win-per-user-install</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public boolean perUserInstall;
    
    /**
     * Adds a dialog to enable the user to choose a directory in which the product is installed
     * <p>
     * <code>--win-dir-chooser</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public boolean dirChooser;
    
    /**
     * Name of the application for registry references.
     * The default is the Application Name with only alphanumerics, dots, and dashes (no whitespace)
     * <p>
     * <code>--win-registry-name &lt;registry name&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public String registryName;
    
    /**
     * UUID associated with upgrades for this package
     * <p>
     * <code>--win-upgrade-uuid &lt;id string&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public String upgradeUUID;
    
    /**
     * Creates a desktop shortcut for the application
     * <p>
     * <code>--win-shortcut</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public boolean shortcut;

    /**
     * Creates a console launcher for the application, 
     * should be specified for application which requires console interactions
     * <p>
     * <code>--win-console</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    public boolean console;

    
    /**
     * Installer type of JPackager operation on Windows.
     * <p>
     *  Valid values for &lt;windowsType&gt; &quot;msi&quot;, &quot;exe&quot;,
     *  
     *  If &lt;windowsType&gt; is omitted a .exe Installer Package will be generated.
     * 
     *  If both &lt;windowsType&gt; and  &lt;type&gt; are being 
     *  set the value of the &lt;type&gt;
     *  parameter is being used.
     *  </p>
     */
    @Parameter( defaultValue = "exe", required = false, readonly = false )
    public String windowsType = "exe";

}
