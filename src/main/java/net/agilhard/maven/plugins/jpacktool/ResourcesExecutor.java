package net.agilhard.maven.plugins.jpacktool;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;

import net.agilhard.maven.plugins.jpacktool.filter.JPacktoolMavenFileFilter;

/**
 * Copy resources for the main source code to the main output directory. Always
 * uses the project.build.resources element to specify the resources to copy.
 *
 * @author <a href="michal.maczka@dimatics.com">Michal Maczka</a>
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @author Andreas Hoheneder
 * @author William Ferguson
 * @author Bernd Eilers
 */
public class ResourcesExecutor implements Contextualizable {

	/**
	 *
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	/**
	 *
	 */
	@Component(role = MavenResourcesFiltering.class, hint = "default")
	protected MavenResourcesFiltering mavenResourcesFiltering;

	@Component(role = MavenFileFilter.class, hint = "default")
	protected MavenFileFilter mavenFileFilter;
	
	/**
	 * @since 2.4
	 */
	public List<MavenResourcesFiltering> mavenFilteringComponents;

	/**
	 *
	 */
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	protected MavenSession session;

	/**
	 * @since 2.4
	 */
	private PlexusContainer plexusContainer;

	protected PackagingResources data;

	private AbstractPackageToolMojo mojo;

    protected Map<String, Object> templateMap;
	
	ResourcesExecutor(AbstractPackageToolMojo mojo, PackagingResources packagingResources, Map<String, Object> templateMap) {
		this.mojo = mojo;
		this.session = mojo.session;
		this.project = mojo.project;
		this.mavenResourcesFiltering = mojo.mavenResourcesFiltering;
		this.mavenFileFilter = mojo.mavenFileFilter;
		this.data = packagingResources;
		this.templateMap = templateMap;
	}

	public Log getLog() {
		return mojo.getLog();
	}

	/** {@inheritDoc} */
	public void contextualize(Context context) throws ContextException {
		plexusContainer = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
	}

	/** {@inheritDoc} */
	public void execute() throws MojoExecutionException {
		if (isSkip()) {
			getLog().info("Skipping the execution.");
			return;
		}

		if ( mavenFileFilter instanceof JPacktoolMavenFileFilter ) {
			((JPacktoolMavenFileFilter)mavenFileFilter).setTemplateMap(templateMap);
		} 
		
		if (StringUtils.isEmpty(data.encoding) && isFilteringEnabled(getResources())) {
			getLog().warn("File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
					+ ", i.e. build is platform dependent!");
			getLog().warn("Please take a look into the FAQ: https://maven.apache.org/general.html#encoding-warning");
		}

		try {

			List<String> combinedFilters = getCombinedFiltersList();

			MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(getResources(),
					getOutputDirectory(), project, data.encoding, combinedFilters, Collections.<String>emptyList(), session);

			mavenResourcesExecution.setEscapeWindowsPaths(data.escapeWindowsPaths);

			// never include project build filters in this call, since we've already
			// accounted for the POM build filters
			// above, in getCombinedFiltersList().
			mavenResourcesExecution.setInjectProjectBuildFilters(false);

			mavenResourcesExecution.setEscapeString(data.escapeString);
			mavenResourcesExecution.setOverwrite(data.overwrite);
			mavenResourcesExecution.setIncludeEmptyDirs(data.includeEmptyDirs);
			mavenResourcesExecution.setSupportMultiLineFiltering(data.supportMultiLineFiltering);
			mavenResourcesExecution.setFilterFilenames(data.fileNameFiltering);
			mavenResourcesExecution.setAddDefaultExcludes(data.addDefaultExcludes);

			// Handle subject of MRESOURCES-99
			Properties additionalProperties = addSeveralSpecialProperties();
			mavenResourcesExecution.setAdditionalProperties(additionalProperties);

			// if these are NOT set, just use the defaults, which are '${*}' and '@'.
			mavenResourcesExecution.setDelimiters(data.delimiters, data.useDefaultDelimiters);

			if (data.nonFilteredFileExtensions != null) {
				mavenResourcesExecution.setNonFilteredFileExtensions(data.nonFilteredFileExtensions);
			}
			mavenResourcesFiltering.filterResources(mavenResourcesExecution);

			executeUserFilterComponents(mavenResourcesExecution);
		} catch (MavenFilteringException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	/**
	 * This solves https://issues.apache.org/jira/browse/MRESOURCES-99.<br/>
	 * BUT:<br/>
	 * This should be done different than defining those properties a second time,
	 * cause they have already being defined in Maven Model Builder (package
	 * org.apache.maven.model.interpolation) via BuildTimestampValueSource. But
	 * those can't be found in the context which can be got from the maven
	 * core.<br/>
	 * A solution could be to put those values into the context by Maven core so
	 * they are accessible everywhere. (I'm not sure if this is a good idea). Better
	 * ideas are always welcome.
	 * 
	 * The problem at the moment is that maven core handles usage of properties and
	 * replacements in the model, but does not the resource filtering which needed
	 * some of the properties.
	 * 
	 * @return the new instance with those properties.
	 */
	private Properties addSeveralSpecialProperties() {
		// String timeStamp = new MavenBuildTimestamp().formattedTimestamp();
		Properties additionalProperties = new Properties();
		// additionalProperties.put( "maven.build.timestamp", timeStamp );
		if (project.getBasedir() != null) {
			additionalProperties.put("project.baseUri", project.getBasedir().getAbsoluteFile().toURI().toString());
		}

		return additionalProperties;
	}

	/**
	 * @param mavenResourcesExecution {@link MavenResourcesExecution}
	 * @throws MojoExecutionException  in case of wrong lookup.
	 * @throws MavenFilteringException in case of failure.
	 * @since 2.5
	 */
	protected void executeUserFilterComponents(MavenResourcesExecution mavenResourcesExecution)
			throws MojoExecutionException, MavenFilteringException {

		if (data.mavenFilteringHints != null) {
			for (String hint : data.mavenFilteringHints) {
				try {
					// CHECKSTYLE_OFF: LineLength
					mavenFilteringComponents.add((MavenResourcesFiltering) plexusContainer
							.lookup(MavenResourcesFiltering.class.getName(), hint));
					// CHECKSTYLE_ON: LineLength
				} catch (ComponentLookupException e) {
					throw new MojoExecutionException(e.getMessage(), e);
				}
			}
		} else {
			getLog().debug("no use filter components");
		}

		if (mavenFilteringComponents != null && !mavenFilteringComponents.isEmpty()) {
			getLog().debug("execute user filters");
			for (MavenResourcesFiltering filter : mavenFilteringComponents) {
				filter.filterResources(mavenResourcesExecution);
			}
		}
	}

	/**
	 * @return The combined filters.
	 */
	protected List<String> getCombinedFiltersList() {
		if (data.filters == null || data.filters.isEmpty()) {
			return data.useBuildFilters ? data.buildFilters : null;
		} else {
			List<String> result = new ArrayList<String>();

			if (data.useBuildFilters && data.buildFilters != null && !data.buildFilters.isEmpty()) {
				result.addAll(data.buildFilters);
			}

			result.addAll(data.filters);

			return result;
		}
	}

	/**
	 * Determines whether filtering has been enabled for any resource.
	 *
	 * @param theResources The set of resources to check for filtering, may be
	 *                     <code>null</code>.
	 * @return <code>true</code> if at least one resource uses filtering,
	 *         <code>false</code> otherwise.
	 */
	private boolean isFilteringEnabled(Collection<Resource> theResources) {
		if (theResources != null) {
			for (Resource resource : theResources) {
				if (resource.isFiltering()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return {@link #data.resources}
	 */
	public List<Resource> getResources() {
		return data.resources;
	}

	/**
	 * @param resources set {@link #data.resources}
	 */
	public void setResources(List<Resource> resources) {
		this.data.resources = resources;
	}

	/**
	 * @return {@link #data.outputDirectory}
	 */
	public File getOutputDirectory() {
		return data.outputDirectory;
	}

	/**
	 * @param outputDirectory the output folder.
	 */
	public void setOutputDirectory(File outputDirectory) {
		this.data.outputDirectory = outputDirectory;
	}

	/**
	 * @return {@link #data.overwrite}
	 */
	public boolean isOverwrite() {
		return data.overwrite;
	}

	/**
	 * @param overwrite true to overwrite false otherwise.
	 */
	public void setOverwrite(boolean overwrite) {
		this.data.overwrite = overwrite;
	}

	/**
	 * @return {@link #data.includeEmptyDirs}
	 */
	public boolean isIncludeEmptyDirs() {
		return data.includeEmptyDirs;
	}

	/**
	 * @param includeEmptyDirs true/false.
	 */
	public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
		this.data.includeEmptyDirs = includeEmptyDirs;
	}

	/**
	 * @return {@link #data.filters}
	 */
	public List<String> getFilters() {
		return data.filters;
	}

	/**
	 * @param filters The filters to use.
	 */
	public void setFilters(List<String> filters) {
		this.data.filters = filters;
	}

	/**
	 * @return {@link #data.delimiters}
	 */
	public LinkedHashSet<String> getDelimiters() {
		return data.delimiters;
	}

	/**
	 * @param delimiters The delimiters to use.
	 */
	public void setDelimiters(LinkedHashSet<String> delimiters) {
		this.data.delimiters = delimiters;
	}

	/**
	 * @return {@link #data.useDefaultDelimiters}
	 */
	public boolean isUseDefaultDelimiters() {
		return data.useDefaultDelimiters;
	}

	/**
	 * @param useDefaultDelimiters true to use {@code ${*}}
	 */
	public void setUseDefaultDelimiters(boolean useDefaultDelimiters) {
		this.data.useDefaultDelimiters = useDefaultDelimiters;
	}

	/**
	 * @return {@link #data.skip}
	 */
	public boolean isSkip() {
		return data.skip;
	}

}