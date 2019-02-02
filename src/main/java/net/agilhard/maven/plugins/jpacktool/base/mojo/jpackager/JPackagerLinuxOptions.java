package net.agilhard.maven.plugins.jpacktool.base.mojo.jpackager;

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
 * Linux Options for JPackager.
 * 
 * @author Bernd Eilers
 *
 */
public class JPackagerLinuxOptions {

    /**
     * Name for Linux bundle.
     * <p>
     * <code>--linux-bundle-name &lt;bundle name&gt;</code>
     * </p>
     */
    @Parameter(required = false, readonly = false)
    public String bundleName;

    /**
     * Required packages or capabilities for the application.
     * <p>
     * <code>--linux-package-deps &lt;list&gt;</code>
     * </p>
     */
    @Parameter(required = false, readonly = false)
    public String packageDeps;

    /**
     * Type of the license (&quot;License: &lt;value&gt;&quot; of the RPM .spec).
     * <p>
     * <code>--linux-rpm-license-type &lt;type string&gt;</code>
     * </p>
     */
    @Parameter(required = false, readonly = false)
    public String rpmLicenseType;

    /**
     * Maintainer for .deb bundle.
     * <p>
     * <code>--linux-deb-maintainer &lt;email address&gt;</code>
     * </p>
     */
    @Parameter(required = false, readonly = false)
    public String debMaintainer;

    /**
     * Installer type of JPackager operation on Linux.
     * <p>
     * Valid values for &lt;linuxType&gt; &quot;rpm&quot; and &quot;deb&quot;,
     * 
     * If &lt;linuxType&gt; is omitted, all supported types of installable packages
     * for Linux will be generated.
     * 
     * If both &lt;linuxType&gt; and &lt;type&gt; are being set the value of the
     * &lt;type&gt; parameter is being used.
     * </p>
     */
    @Parameter(required = false, readonly = false, defaultValue="rpm")
    public String linuxType;

}
