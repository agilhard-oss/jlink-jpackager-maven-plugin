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
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;

import net.agilhard.maven.plugins.jpacktool.base.mojo.AbstractToolMojo;

public class CollectArtifactsToLinkHandler extends AbstractVisitDependencyHandler {

	private List<Artifact> elements = new ArrayList<>();

	public CollectArtifactsToLinkHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder) {
		super(mojo, dependencyGraphBuilder);
	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("generate-classpath");
		super.execute();
	}

	@Override
	protected void handleNonModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void handleModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {
		// TODO Auto-generated method stub
		
	}
	
	protected void handleDependencyNode(final DependencyNode dependencyNode) {
		Artifact artifact = dependencyNode.getArtifact();
		String type = artifact.getType();
		if ( "jar".equals(type) || "jmod".equals(type) ) {
			elements.add(artifact);
		}
	}

	public List<Artifact> getElements() {
		return elements;
	}

	
}
