package net.agilhard.maven.plugins.jpacktool.mojo.handler;

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
import java.util.Map.Entry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;

import net.agilhard.maven.plugins.jpacktool.mojo.base.AbstractToolMojo;

public abstract class AbstractVisitDependencyHandler extends AbstractDependencyHandler {

	public class HandleDependencyRootVisitor implements DependencyNodeVisitor {

		public MojoExecutionException mojoExecutionException;
		public MojoFailureException mojoFailureException;

		/**
		 * Starts the visit to the specified dependency node.
		 *
		 * @param node the dependency node to visit
		 * @return <code>true</code> to visit the
		 *         specifiedAbstractEndVIsitDependencyHandler dependency node's
		 *         children, <code>false</code> to skip the.resolvePat specified
		 *         dependency node's children and proceed to its next sibling
		 */
		public boolean visit(final DependencyNode node) {
			String type = node.getArtifact().getType();
			boolean b = !node.toNodeString().endsWith(":test");
			if ( excludedArtifacts != null ) {
				b = b && (!excludedArtifacts.contains(node.getArtifact()));
			}
			
			if (b) {
				if ("jar".equals(type) || "jmod".equals(type)) {
					try {
						handleDependencyNode(node);
					} catch (final MojoExecutionException e) {
						getLog().error("endVisit -> MojoExecutionException", e);
						mojoExecutionException = e;
					} catch (final MojoFailureException e) {
						getLog().error("endVisit -> MojoFailureException", e);
						mojoFailureException = e;
					}
				}
				if ("jmod".equals(type)) {
				}
			}
			return b;
		}

		/**
		 * Ends the visit to to the specified dependency
		 * node.AbstractEndVIsitDependencyHandler, outputDirectoryJPacktool, outputDirectoryAutomaticJars,
				outputDirectoryClasspathJars, outputDirectoryModules, excludedArtifacts, classpathArtifacts
		 *
		 * @param node the dependency node to visit
		 * @retur outputDirectoryJPacktool,
				copyAutomaticJars ? outputDirectoryAutomaticJars : null,
				copyClassPathJars ? outputDirectoryClasspathJars : null, copyModuleJars ? outputDirectoryModules : null,
				excludedArtifacts, classpathArtifacts,n <code>true</code> to visit the specified dependency node's next
		 *         sibling, <code>false</code> to skip the specified dependency node's
		 *         next siblings and proceed to its parent
		 */
		@Override
		public boolean endVisit(final DependencyNode node) {
			return true;
		}
	}

	public AbstractVisitDependencyHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder) {
		super(mojo, dependencyGraphBuilder);
	}

	@Override
	protected abstract void handleNonModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException;

	@Override
	protected abstract void handleModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException;

	protected void handleDependencyRoot(final DependencyNode dependencyNode)
			throws MojoExecutionException, MojoFailureException {
		HandleDependencyRootVisitor visitor = new HandleDependencyRootVisitor();
		dependencyNode.accept(visitor);
		if (visitor.mojoExecutionException != null) {
			throw visitor.mojoExecutionException;
		}
		if (visitor.mojoFailureException != null) {
			throw visitor.mojoFailureException;
		}
	}

}
