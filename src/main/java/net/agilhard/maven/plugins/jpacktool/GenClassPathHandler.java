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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;

public class GenClassPathHandler extends AbstractVisitDependencyHandler {

	private List<File> classPathElements = new ArrayList<>();

	private List<String> jarsOnClassPath = new ArrayList<>();

	public GenClassPathHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder, File outputDirectoryJPacktool,
			File outputDirectoryAutomaticJars, File outputDirectoryClasspathJars, File outputDirectoryModules) {
		super(mojo, dependencyGraphBuilder, outputDirectoryJPacktool, outputDirectoryAutomaticJars, outputDirectoryClasspathJars,
				outputDirectoryModules);
	}

	@Override
	protected void handleNonModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {

		getLog().debug("handleNonModJar:" + artifact.getFile());

		boolean isAutomatic = (entry == null || entry.getValue() == null) ? false : entry.getValue().isAutomatic();

		if (isAutomatic) {
			try (JarFile jarFile = new JarFile(artifact.getFile())) {
				Manifest manifest = jarFile.getManifest();
				if (manifest == null) {
					isAutomatic = false;
				} else {
					Attributes mainAttributes = manifest.getMainAttributes();
					isAutomatic = mainAttributes.getValue("Automatic-Module-Name") != null;
				}
			} catch (IOException e) {

				getLog().error("error reading manifest");
				throw new MojoExecutionException("error reading manifest");
			}

		}

		Path path = artifact.getFile().toPath();

		if (Files.isRegularFile(path)) {

			File target = null;
			
			if (! isAutomatic) { // automatic jars are not on classpath

				if (outputDirectoryClasspathJars != null) {
					target = new File(outputDirectoryClasspathJars, artifact.getFile().getName());

					if (!classPathElements.contains(target)) {
						classPathElements.add(target);
						jarsOnClassPath.add(artifact.getFile().getName());
					}
				}
			}
		}

	}

	@Override
	protected void handleModJar(DependencyNode dependencyNode, Artifact artifact,
			Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {

		// module jars are not on classpath

	}

	public List<File> getClassPathElements() {
		return classPathElements;
	}

	public List<String> getJarsOnClassPath() {
		return jarsOnClassPath;
	}

	
}
