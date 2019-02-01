
package net.agilhard.maven.plugins.jpacktool.mojo.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

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
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import net.agilhard.maven.plugins.jpacktool.base.handler.CollectJarsHandler;
import net.agilhard.maven.plugins.jpacktool.base.handler.GenerateClassPathHandler;
import net.agilhard.maven.plugins.jpacktool.base.handler.GenerateJDepsHandler;

/**
 * @author bei
 *
 */
@Mojo(name = "jpacktool-prepare", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true)
public class JPackToolPrepareMojo extends AbstractDependencyJarsMojo<GenerateJDepsHandler> {

	@Parameter(defaultValue = "true", required = true, readonly = false)
	protected boolean generateAutomaticJdeps;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	protected boolean generateClassPathJdeps;

	@Parameter(defaultValue = "true", required = true, readonly = false)
	protected boolean generateModuleJdeps;

	@Parameter(defaultValue = "false", required = true, readonly = false)
	protected boolean showAllDeps;

	/**
	 * Flag if --ignore-missing-deps should be used on the jdeps calls to analyze
	 * module dependencies
	 */
	@Parameter(defaultValue = "true")
	protected boolean ignoreMissingDeps;

	
	@Parameter(defaultValue = "false")
	protected boolean useListDeps;

	/**
	 * Name of the generated properties file in the <code>target</code> directory. This
	 * will not change the name of the installed/deployed file.
	 */
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	protected String finalName;

	/**
	 * The jdeps Java Tool Executable.
	 */
	private String jdepsExecutable;

	protected Map<String, Object> model = new HashMap<>();

	protected GenerateClassPathHandler genClassPathHandler;

	protected void putModel(String key, Object value) {
		model.put(key, value);
	}

	/**
	 * Hook for derived classes to execute something after the JDeps Handler has finisihed.
	 * 
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	public void executeAfterJDeps() throws MojoExecutionException, MojoFailureException {
		// do nothing
	}
	
	/** {@inheritDoc} */
	@Override
	public void executeToolMain() throws MojoExecutionException, MojoFailureException {

		if ( getToolchain() == null ) {
			double v = getJavaVersion();

			if ( ignoreMissingDeps && (v == 9.0 || v == 10.0 || v == 11.0) ){
				this.getLog().warn("ignoreMissingDeps=true can not be used with java version "+v+" disabling it.");
				ignoreMissingDeps = false;
			}
			
			if ( (!useListDeps) && ( v == 9.0 ) ) {
				this.getLog().warn("useListDeps=false can not be used with java version "+v+" enableing it.");
				useListDeps = true;

			}
		}
		
		if (!outputDirectoryAutomaticJars.exists()) {
			if (!outputDirectoryAutomaticJars.mkdirs()) {
				throw new MojoExecutionException("directory can not be created:" + outputDirectoryAutomaticJars);
			}
		}
		if (!outputDirectoryClasspathJars.exists()) {
			if (!outputDirectoryClasspathJars.mkdirs()) {
				throw new MojoExecutionException("directory can not be created:" + outputDirectoryClasspathJars);
			}
		}
		if (!outputDirectoryModules.exists()) {
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

		super.executeToolMain();

		executeAfterJDeps();

		GenerateJDepsHandler handler = getHandler();

		
		
		Properties props = this.project.getProperties();

		String pfx = this.jpacktoolPropertyPrefix;
		props.put(pfx + ".used", Boolean.TRUE);

		if (showAllDeps) {
			for (String nodeString : handler.getNodeStrings()) {
				getLog().info("--------------------");
				getLog().info("Dependencies for " + nodeString);

				getLog().info("Modules Dependencies:" + (handler.getAllModulesMap().get(nodeString) == null ? ""
						: String.join(",", handler.getAllModulesMap().get(nodeString))));
				getLog().info(
						"System Modules Dependencies:" + (handler.getLinkedSystemModulesMap().get(nodeString) == null ? ""
								: String.join(",", handler.getLinkedSystemModulesMap().get(nodeString))));
				getLog().info("Linked Modules Dependencies:" + (handler.getLinkedModulesMap().get(nodeString) == null ? ""
						: String.join(",", handler.getLinkedModulesMap().get(nodeString))));
				getLog().info(
						"Automatic Modules Dependencies:" + (handler.getAutomaticModulesMap().get(nodeString) == null ? ""
								: String.join(",", handler.getAutomaticModulesMap().get(nodeString))));
			}
		}
		putModel("allModulesMap", handler.getAllModulesMap());
		putModel("linkedSystemModulesMap", handler.getLinkedSystemModulesMap());
		putModel("linkedModulesMap", handler.getLinkedModulesMap());
		putModel("automaticModulesMap", handler.getAutomaticModulesMap());

		putModel("nodeStrings", handler.getNodeStrings());

		if ( verbose || showAllDeps ) {
			getLog().info("--------------------");
			if ( showAllDeps ) {
				getLog().info("All Modules Dependencies:" + String.join(",", handler.getAllModules()));
			}
			getLog().info("Linked System Modules Dependencies:" + String.join(",", handler.getLinkedSystemModules()));
			getLog().info("Linked Modules Dependencies:" + String.join(",", handler.getLinkedModules()));
			getLog().info("Automatic Modules Dependencies:" + String.join(",", handler.getAutomaticModules()));
			if ( showAllDeps ) {
				getLog().info("Jars on Classpath:" + String.join(",", handler.getJarsOnClassPath()));
			}
		}
		
		putModel("allModules", handler.getAllModules());
		putModel("linkedSystemModules", handler.getLinkedSystemModules());
		putModel("linkedModules", handler.getLinkedModules());
		putModel("automaticModules", handler.getAutomaticModules());
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

		
		File propertiesFile = this.getArtifactFile(buildDirectory, this.finalName, "jpacktool_jdeps", "properties");

		try (FileOutputStream fout = new FileOutputStream(propertiesFile); PrintStream pout=new PrintStream(fout)) {
			
			pout.println("all_deps=" + String.join(",", handler.getAllModules()));
			pout.println("linked_system_deps="+ String.join(",", handler.getLinkedSystemModules()));
			pout.println("linked_deps=" + String.join(",", handler.getLinkedModules()));
			pout.println("automatic_deps=" + String.join(",", handler.getAutomaticModules()));
			pout.println("classpath_jars=" + String.join(",", handler.getJarsOnClassPath()));
	
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public GenerateClassPathHandler creatGenClassPathHandler() {
		return new GenerateClassPathHandler(this, dependencyGraphBuilder);
	}

	public CollectJarsHandler createCopyHandler() {
		return new CollectJarsHandler(this, dependencyGraphBuilder);
	}

	@Override
	public GenerateJDepsHandler createHandler() throws MojoExecutionException, MojoFailureException {
		return new GenerateJDepsHandler(this, dependencyGraphBuilder, jdepsExecutable, generateAutomaticJdeps, generateClassPathJdeps,
				generateModuleJdeps, this.genClassPathHandler.getClassPathElements(),
				this.genClassPathHandler.getJarsOnClassPath(), ignoreMissingDeps, useListDeps);

	}

}
