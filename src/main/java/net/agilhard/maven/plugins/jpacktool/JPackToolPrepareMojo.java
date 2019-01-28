
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @author bei
 *
 */
@Mojo(name = "jpacktool-prepare", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true)
public class JPackToolPrepareMojo extends AbstractDependencyJarsMojo<JPackToolHandler> {

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean copyAutomaticJars;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean copyClassPathJars;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean copyModuleJars;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean generateAutomaticJdeps;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean generateClassPathJdeps;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	private boolean generateModuleJdeps;
	/**
	 * The jdeps Java Tool Executable.
	 */
	private String jdepsExecutable;

	private Map<String, Object> model = new HashMap<>();

	private GenClassPathHandler genClassPathHandler;

	private void putModel(String key, Object value) {
		model.put(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		if ((!outputDirectoryAutomaticJars.exists()) && copyAutomaticJars) {
			if (!outputDirectoryAutomaticJars.mkdirs()) {
				throw new MojoExecutionException("directory can not be created:" + outputDirectoryAutomaticJars);
			}
		}
		if ((!outputDirectoryClasspathJars.exists()) && copyClassPathJars) {
			if (!outputDirectoryClasspathJars.mkdirs()) {
				throw new MojoExecutionException("directory can not be created:" + outputDirectoryClasspathJars);
			}
		}
		if ((!outputDirectoryModules.exists()) && copyModuleJars) {
			if (!outputDirectoryModules.mkdirs()) {
				throw new MojoExecutionException("directory can not be created:" + outputDirectoryModules);
			}
		}

		try {
			jdepsExecutable = this.getToolExecutable("jdeps");
		} catch (IOException e) {
			throw new MojoFailureException("i/o error", e);
		}

		// copy jars first
		CollectJarsHandler collectJarsHandler = createCopyHandler();
		collectJarsHandler.execute();

		this.genClassPathHandler = creatGenClassPathHandler();
		this.genClassPathHandler.execute();

		super.execute();

		JPackToolHandler handler = getHandler();

		Properties props = this.project.getProperties();

		String pfx = this.jpacktoolPropertyPrefix;
		props.put(pfx + ".used", Boolean.TRUE);

		for (String nodeString : handler.getNodeStrings()) {
			getLog().info("--------------------");
			getLog().info("Dependencies for " + nodeString);

			getLog().info("Dependency Modules:" + (handler.getAllModulesMap().get(nodeString) == null ? ""
					: String.join(",", handler.getAllModulesMap().get(nodeString))));
			getLog().info(
					"Dependency System Modules:" + (handler.getLinkedSystemModulesMap().get(nodeString) == null ? ""
							: String.join(",", handler.getLinkedSystemModulesMap().get(nodeString))));
			getLog().info("Dependency Linked Modules:" + (handler.getLinkedModulesMap().get(nodeString) == null ? ""
					: String.join(",", handler.getLinkedModulesMap().get(nodeString))));
			getLog().info(
					"Dependency Automatic Modules:" + (handler.getAutomaticModulesMap().get(nodeString) == null ? ""
							: String.join(",", handler.getAutomaticModulesMap().get(nodeString))));
		}

		putModel("allModulesMap", handler.getAllModulesMap());
		putModel("linkedSystemModulesMap", handler.getLinkedSystemModulesMap());
		putModel("linkedModulesMap", handler.getLinkedModulesMap());
		putModel("automaticModulesMap", handler.getAutomaticModulesMap());

		putModel("nodeStrings", handler.getNodeStrings());

		getLog().info("--------------------");

		getLog().info("All Modules:" + String.join(",", handler.getAllModules()));
		putModel("allModules", handler.getAllModules());

		getLog().info("Linked System Modules:" + String.join(",", handler.getLinkedSystemModules()));
		putModel("linkedSystemModules", handler.getLinkedSystemModules());

		getLog().info("Linked Modules:" + String.join(",", handler.getLinkedModules()));
		putModel("linkedModules", handler.getLinkedModules());

		getLog().info("Automatic Modules:" + String.join(",", handler.getAutomaticModules()));
		putModel("automaticModules", handler.getAutomaticModules());

		getLog().info("Jars on Classpath:" + String.join(",", handler.getJarsOnClassPath()));
		putModel("jarsOnClassPath", handler.getJarsOnClassPath());

		props.put(pfx + ".model", model);

		if (handler.getWarnings().size() > 0) {
			getLog().warn("--------------------");
			getLog().warn("Warnings from jdep calls");
			getLog().warn("--------------------");
			for (String warn : handler.getWarnings()) {
				getLog().warn(warn);
			}
		}
		if (handler.getErrors().size() > 0) {
			getLog().error("--------------------");
			getLog().error("Errors from jdep calls");
			getLog().error("--------------------");
			for (String err : handler.getErrors()) {
				getLog().error(err);
			}

			throw new MojoFailureException("errors on jdep calls");
		}

	}

	public GenClassPathHandler creatGenClassPathHandler() {
		return new GenClassPathHandler(this, dependencyGraphBuilder, outputDirectoryJPacktool,
				outputDirectoryAutomaticJars, outputDirectoryClasspathJars, outputDirectoryModules, excludedArtifacts);
	}

	public CollectJarsHandler createCopyHandler() {
		return new CollectJarsHandler(this, dependencyGraphBuilder, outputDirectoryJPacktool,
				outputDirectoryAutomaticJars, outputDirectoryClasspathJars, outputDirectoryModules, excludedArtifacts);
	}

	@Override
	public JPackToolHandler createHandler() throws MojoExecutionException, MojoFailureException {
		return new JPackToolHandler(this, dependencyGraphBuilder, outputDirectoryJPacktool,
				copyAutomaticJars ? outputDirectoryAutomaticJars : null,
				copyClassPathJars ? outputDirectoryClasspathJars : null, copyModuleJars ? outputDirectoryModules : null,
				excludedArtifacts, jdepsExecutable, generateAutomaticJdeps, generateClassPathJdeps, generateModuleJdeps,
				this.genClassPathHandler.getClassPathElements(), this.genClassPathHandler.getJarsOnClassPath());

	}

}
