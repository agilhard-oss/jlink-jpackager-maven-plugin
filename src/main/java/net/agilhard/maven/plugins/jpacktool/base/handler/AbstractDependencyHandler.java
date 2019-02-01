package net.agilhard.maven.plugins.jpacktool.base.handler;

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
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;

import net.agilhard.maven.plugins.jpacktool.mojo.base.AbstractToolMojo;
import net.agilhard.maven.plugins.jpacktool.mojo.base.ArtifactParameter;

public abstract class AbstractDependencyHandler {

	protected File outputDirectoryJPacktool;

	protected File outputDirectoryAutomaticJars;

	protected File outputDirectoryClasspathJars;

	protected File outputDirectoryModules;

	protected List<ArtifactParameter> excludedArtifacts;

	protected List<ArtifactParameter> classpathArtifacts;

	
	public HashSet<String> handledNodes;
	final AbstractToolMojo mojo;
	DependencyGraphBuilder dependencyGraphBuilder;

	public AbstractDependencyHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder) {
		this.mojo = mojo;
		this.handledNodes = new HashSet<>();
		this.dependencyGraphBuilder = dependencyGraphBuilder;
		this.outputDirectoryJPacktool = mojo.getOutputDirectoryJPacktool();
		this.outputDirectoryAutomaticJars = mojo.getOutputDirectoryAutomaticJars();
		this.outputDirectoryClasspathJars = mojo.getOutputDirectoryClasspathJars();
		this.outputDirectoryModules = mojo.getOutputDirectoryModules();
		this.excludedArtifacts = mojo.getExcludedArtifacts();
		this.classpathArtifacts = mojo.getClasspathArtifacts();
	}

	public Log getLog() {
		return mojo.getLog();
	}

	protected abstract void handleNonModJar(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException;

	protected void handleNonModJarIfNotAlreadyHandled(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {
		String key = dependencyNode.toNodeString();

		if (!handledNodes.contains(key)) {
			handledNodes.add(key);
			handleNonModJar(dependencyNode, artifact, entry);
		}

	}

	protected abstract void handleModJar(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException;

	protected void handleModJarIfNotAlreadyHandled(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {
		String key = dependencyNode.toNodeString();

		if (!handledNodes.contains(key)) {
			handledNodes.add(key);
			handleModJar(dependencyNode, artifact, entry);
		}

	}

	protected void handleOther(final DependencyNode dependencyNode) throws MojoExecutionException, MojoFailureException  {
		// do nothing
	}

	protected void handleDependencyNode(final DependencyNode dependencyNode)
			throws MojoExecutionException, MojoFailureException {

		Artifact artifact = dependencyNode.getArtifact();
		String type = artifact.getType();

		if ("jar".equals(type) || "jmod".equals(type)) {

			final File file = artifact.getFile();

			final ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(file);

			final Toolchain toolchain = mojo.getToolchain();
			if (toolchain != null && toolchain instanceof DefaultJavaToolChain) {
				request.setJdkHome(new File(((DefaultJavaToolChain) toolchain).getJavaHome()));
			}
			ResolvePathsResult<File> resolvePathsResult;
			try {
				resolvePathsResult = mojo.getLocationManager().resolvePaths(request);
			} catch (final IOException e) {
				this.getLog().error("handleDependencyNode -> IOException", e);
				throw new MojoExecutionException("handleDependencyNode: IOException", e);
			}

			if ( (resolvePathsResult.getPathElements().entrySet().size() == 0)
					|| ( (classpathArtifacts != null) && classpathArtifacts.contains(artifact)) ) {

				this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, null);

			} else {

				for (final Map.Entry<File, JavaModuleDescriptor> entry : resolvePathsResult.getPathElements()
						.entrySet()) {
					if (entry.getValue() == null) {
						this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, entry);
					} else if (entry.getValue().isAutomatic()) {
						this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, entry);
					} else {
						this.handleModJarIfNotAlreadyHandled(dependencyNode, artifact, entry);
					}
				}
			}
			
		} else if ( (artifact.getClassifier() != null) &&
				(artifact.getClassifier().startsWith("jpacktool"))) {
	
			handleOther(dependencyNode);
		}
		
	}

	protected abstract void handleDependencyRoot(final DependencyNode dependencyNode)
			throws MojoExecutionException, MojoFailureException;

	public void execute() throws MojoExecutionException, MojoFailureException {
		// No need to filter our search. We want to resolve all artifacts.
		execute(mojo.getProject(), null);
	}
	
	public void execute(MavenProject project, ArtifactFilter artifactFilter) throws MojoExecutionException, MojoFailureException {

		final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(
				mojo.getSession().getProjectBuildingRequest());

		buildingRequest.setProject(project);

		this.getLog().debug("building dependency graph for project " + project.getArtifact());

		try {

			final DependencyNode dependencyNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);

			this.handleDependencyRoot(dependencyNode);

		} catch (final DependencyGraphBuilderException e) {
			throw new MojoExecutionException("Could not resolve dependencies for project: " + project, e);
		}
	}
}
