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
import java.io.File;

/**
 * Mac Options for JPackager.
 * @author bei
 *
 */
public class JPackagerMacOptions 
{

    /**
     * Request that the bundle be signed
     * <code>--mac-sign</code>
     */
    @Parameter( required = false, readonly = false )
    boolean sign;
    
    /**
     * Name of the application as it appears in the Menu Bar.
     * This can be different from the application name. 
     * This name must be less than 16 characters long 
     * and be suitable for displaying in the menu bar and the application Info window.
     * Defaults to the application name.
     * <code>-mac-bundle-name &lt;name string&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String bundleName;
    
    /**
     * An identifier that uniquely identifies the application for MacOSX (and on the Mac App Store). 
     * May only use alphanumeric (A-Z,a-z,0-9), hyphen (-), and period (.) characters.
     * <code>--mac-bundle-identifier &lt;ID string&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String bundleIdentifier;
    
    /**
     * Mac App Store Categories. 
     * Note that the key is the string shown to the user and the value is the ID of the category.
     * <code>--mac-app-store-category &lt;category string&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String appStoreCategory;
    
    /**
     * File location of a custom mac app store entitlements file
     * <code>--mac-app-store-entitlements &lt;file path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    File appStoreEntitlements;

    /**
     * When signing the application bundle, 
     * this value is prefixed to all components that need to be signed
     * that don't have an existing bundle identifier.
     * <code>--mac-bundle-signing-prefix &lt;prefix string&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String bundleSigningPrefix;
    
    /**
     * User name portion of the typical &quot;Mac Developer ID Application: &quot; signing key
     * <code>--mac-signing-key-user-name &lt;user name&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    String sigingKeyUserName;
    
    /**
     * Location of the keychain to use. If not specified, the standard keychains are used.
     * <code>--mac-signing-keychain &lt;file path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    File signingKeychain;
}
