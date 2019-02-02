package net.agilhard.maven.plugins.jpacktool.base.mojo;

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

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;

public class PackagingResources {
	/**
	 * The character encoding scheme to be applied when filtering resources.
	 */
	@Parameter(defaultValue = "${project.build.sourceEncoding}")
	public String encoding;
	/**
	 * The output directory into which to copy the resources.
	 */
	@Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
	public File outputDirectory;
	/**
	 * The list of resources we want to transfer.
	 */
	@Parameter(defaultValue = "${project.resources}", required = true, readonly = true)
	public List<Resource> resources;
	/**
	 * The list of additional filter properties files to be used along with System
	 * and project properties, which would be used for the filtering.
	 * 
	 */
	@Parameter(defaultValue = "${project.build.filters}", readonly = true)
	public List<String> buildFilters;
	/**
	 * <p>
	 * The list of extra filter properties files to be used along with System
	 * properties, project properties, and filter properties files specified in the
	 * POM build/filters section, which should be used for the filtering during the
	 * current mojo execution.
	 * </p>
	 * <p>
	 * Normally, these will be configured from a plugin's execution section, to
	 * provide a different set of filters for a particular execution. For instance,
	 * starting in Maven 2.2.0, you have the option of configuring executions with
	 * the id's <code>default-resources</code> and
	 * <code>default-testResources</code> to supply different configurations for the
	 * two different types of resources. By supplying <code>extraFilters</code>
	 * configurations, you can separate which filters are used for which type of
	 * resource.
	 * </p>
	 */
	@Parameter
	public List<String> filters;
	/**
	 * If false, don't use the filters specified in the build/filters section of the
	 * POM when processing resources in this mojo execution.
	 * 
	 */
	@Parameter(defaultValue = "true")
	public boolean useBuildFilters;
	/**
	 * Expressions preceded with this string won't be interpolated. Anything else
	 * preceded with this string will be passed through unchanged. For example
	 * {@code \${foo}} will be replaced with {@code ${foo}} but {@code \\${foo}}
	 * will be replaced with {@code \\value of foo}, if this parameter has been set
	 * to the backslash.
	 * 
	 */
	@Parameter
	public String escapeString;
	/**
	 * Overwrite existing files even if the destination files are newer.
	 *
	 */
	@Parameter(defaultValue = "false")
	public boolean overwrite;
	/**
	 * Copy any empty directories included in the Resources.
	 *
	 */
	@Parameter(defaultValue = "false")
	public boolean includeEmptyDirs;
	/**
	 * Additional file extensions to not apply filtering (already defined are : jpg,
	 * jpeg, gif, bmp, png)
	 *
	 */
	@Parameter
	public List<String> nonFilteredFileExtensions;
	/**
	 * Whether to escape backslashes and colons in windows-style paths.
	 *
	 */
	@Parameter(defaultValue = "true")
	public boolean escapeWindowsPaths;
	/**
	 * <p>
	 * Set of delimiters for expressions to filter within the resources. These
	 * delimiters are specified in the form {@code beginToken*endToken}. If no
	 * {@code *} is given, the delimiter is assumed to be the same for start and
	 * end.
	 * </p>
	 * <p>
	 * So, the default filtering delimiters might be specified as:
	 * </p>
	 * 
	 * <pre>
	 * &lt;delimiters&gt;
	 *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
	 *   &lt;delimiter&gt;@&lt;/delimiter&gt;
	 * &lt;/delimiters&gt;
	 * </pre>
	 * <p>
	 * Since the {@code @} delimiter is the same on both ends, we don't need to
	 * specify {@code @*@} (though we can).
	 * </p>
	 *
	 */
	@Parameter
	public LinkedHashSet<String> delimiters;
	/**
	 * Use default delimiters in addition to custom delimiters, if any.
	 *
	 */
	@Parameter(defaultValue = "true")
	public boolean useDefaultDelimiters;
	/**
	 * By default files like {@code .gitignore}, {@code .cvsignore} etc. are
	 * excluded which means they will not being copied. If you need them for a
	 * particular reason you can do that by settings this to {@code false}. This
	 * means all files like the following will be copied.
	 * <ul>
	 * <li>Misc: &#42;&#42;/&#42;~, &#42;&#42;/#&#42;#, &#42;&#42;/.#&#42;,
	 * &#42;&#42;/%&#42;%, &#42;&#42;/._&#42;</li>
	 * <li>CVS: &#42;&#42;/CVS, &#42;&#42;/CVS/&#42;&#42;,
	 * &#42;&#42;/.cvsignore</li>
	 * <li>RCS: &#42;&#42;/RCS, &#42;&#42;/RCS/&#42;&#42;</li>
	 * <li>SCCS: &#42;&#42;/SCCS, &#42;&#42;/SCCS/&#42;&#42;</li>
	 * <li>VSSercer: &#42;&#42;/vssver.scc</li>
	 * <li>MKS: &#42;&#42;/project.pj</li>
	 * <li>SVN: &#42;&#42;/.svn, &#42;&#42;/.svn/&#42;&#42;</li>
	 * <li>GNU: &#42;&#42;/.arch-ids, &#42;&#42;/.arch-ids/&#42;&#42;</li>
	 * <li>Bazaar: &#42;&#42;/.bzr, &#42;&#42;/.bzr/&#42;&#42;</li>
	 * <li>SurroundSCM: &#42;&#42;/.MySCMServerInfo</li>
	 * <li>Mac: &#42;&#42;/.DS_Store</li>
	 * <li>Serena Dimension: &#42;&#42;/.metadata,
	 * &#42;&#42;/.metadata/&#42;&#42;</li>
	 * <li>Mercurial: &#42;&#42;/.hg, &#42;&#42;/.hg/&#42;&#42;</li>
	 * <li>GIT: &#42;&#42;/.git, &#42;&#42;/.gitignore, &#42;&#42;/.gitattributes,
	 * &#42;&#42;/.git/&#42;&#42;</li>
	 * <li>Bitkeeper: &#42;&#42;/BitKeeper, &#42;&#42;/BitKeeper/&#42;&#42;,
	 * &#42;&#42;/ChangeSet, &#42;&#42;/ChangeSet/&#42;&#42;</li>
	 * <li>Darcs: &#42;&#42;/_darcs, &#42;&#42;/_darcs/&#42;&#42;,
	 * &#42;&#42;/.darcsrepo,
	 * &#42;&#42;/.darcsrepo/&#42;&#42;&#42;&#42;/-darcs-backup&#42;,
	 * &#42;&#42;/.darcs-temp-mail
	 * </ul>
	 *
	 */
	@Parameter(defaultValue = "true")
	public boolean addDefaultExcludes;
	/**
	 * <p>
	 * List of plexus components hint which implements
	 * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)}.
	 * They will be executed after the resources copying/filtering.
	 * </p>
	 *
	 */
	@Parameter
	public List<String> mavenFilteringHints;

	/**
	 * stop searching endToken at the end of line
	 *
	 */
	@Parameter(defaultValue = "false")
	public boolean supportMultiLineFiltering;
	/**
	 * Support filtering of filenames folders etc.
	 * 
	 */
	@Parameter(defaultValue = "false")
	public boolean fileNameFiltering;

	/**
	 * You can skip the execution of the plugin if you need to. Its use is NOT
	 * RECOMMENDED, but quite convenient on occasion.
	 * 
	 */
	@Parameter(property = "maven.resources.skip", defaultValue = "false")
	public boolean skip;

	public PackagingResources() {
	}
}
