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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.util.cli.Commandline;

import net.agilhard.maven.plugins.jpacktool.base.mojo.AbstractToolMojo;
import net.agilhard.maven.plugins.jpacktool.base.mojo.ExecuteCommand;

public class GenerateJDepsHandler extends AbstractEndVisitDependencyHandler {

	/**
	 * The jdeps Java Tool Executable.
	 */
	private String jdepsExecutable;

	private boolean generateAutomaticJdeps;

	private boolean generateClassPathJdeps;

	private boolean generateModuleJdeps;

	private List<File> classPathElements;

	private List<String> jarsOnClassPath;

	private List<String> warnings = new ArrayList<>();

	private List<String> errors = new ArrayList<>();

	protected List<String> systemModules = new ArrayList<>();;

	protected List<String> linkedSystemModules = new ArrayList<>();;

	private List<String> allModules = new ArrayList<>();

	private List<String> linkedModules = new ArrayList<>();

	private List<String> automaticModules = new ArrayList<>();

	private List<String> nodeStrings = new ArrayList<>();

	private Map<String, List<String>> allModulesMap = new HashMap<>();

	private Map<String, List<String>> linkedModulesMap = new HashMap<>();;

	private Map<String, List<String>> automaticModulesMap = new HashMap<>();;

	private Map<String, List<String>> linkedSystemModulesMap = new HashMap<>();;

	private boolean ignoreMissingDeps;

	private boolean useListDeps;

	public GenerateJDepsHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder,
			String jdepsExecutable, boolean generateAutomaticJdeps, boolean generateClassPathJdeps,
			boolean generateModuleJdeps, List<File> classPathElements, List<String> jarsOnClassPath,
			boolean ignoreMissingDeps, boolean useListDeps) throws MojoExecutionException {

		super(mojo, dependencyGraphBuilder);

		this.ignoreMissingDeps = ignoreMissingDeps;
		this.useListDeps = useListDeps;
		this.jdepsExecutable = jdepsExecutable;

		this.generateAutomaticJdeps = generateAutomaticJdeps;
		this.generateClassPathJdeps = generateClassPathJdeps;
		this.generateModuleJdeps = generateModuleJdeps;
		this.classPathElements = classPathElements;
		this.jarsOnClassPath = jarsOnClassPath;

		this.systemModules = mojo.getSystemModules();

	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("generate-jdeps");
		super.execute();
	}

	/**
	 * Convert a list of elements into a colon separted string.
	 * 
	 * @param elements The list of elements
	 * @throws MojoFailureException if i/o error occured during execution
	 *
	 * @return The string with the element list which is separated by {@code :}.
	 */
	protected String getPathSeparatedList(final Collection<File> elements) throws MojoFailureException {
		final StringBuilder sb = new StringBuilder();
		for (final File element : elements) {
			if (sb.length() > 0) {
				sb.append(File.pathSeparatorChar);
			}
			try {
				sb.append(element.getCanonicalPath());
			} catch (IOException e) {
				throw new MojoFailureException("error getting path");
			}
		}
		return sb.toString();
	}

	protected Commandline createJDepsCommandLine(File sourceFile) throws MojoFailureException {
		final Commandline cmd = new Commandline();

		if (this.classPathElements.size() > 0) {
			cmd.createArg().setValue("--class-path");
			String s = this.getPathSeparatedList(this.classPathElements);
			cmd.createArg().setValue(s);
		}

		if ((outputDirectoryAutomaticJars != null) || (outputDirectoryModules != null)) {
			cmd.createArg().setValue("--module-path");
			StringBuilder sb = new StringBuilder();
			if (outputDirectoryModules != null) {
				try {
					sb.append(outputDirectoryModules.getCanonicalPath());
				} catch (IOException e) {
					throw new MojoFailureException("error getting path");
				}
			}
			if (outputDirectoryAutomaticJars != null) {
				if (outputDirectoryModules != null) {
					sb.append(File.pathSeparator);
				}
				try {
					sb.append(outputDirectoryAutomaticJars.getCanonicalPath());
				} catch (IOException e) {
					throw new MojoFailureException("error getting path");
				}
			}
			cmd.createArg().setValue(sb.toString());

		}

		if (useListDeps) {
			cmd.createArg().setValue("--list-deps");
		} else {
			cmd.createArg().setValue("--print-module-deps");
		}

		if (ignoreMissingDeps) {
			cmd.createArg().setValue("--ignore-missing-deps");
		}

		try {
			String s = sourceFile.getCanonicalPath();
			cmd.createArg().setValue(s);
		} catch (IOException e) {
			throw new MojoFailureException("error getting path");
		}

		return cmd;
	}

	protected void generateJdeps(String nodeString, File sourceFile, boolean automaticDep)
			throws MojoExecutionException, MojoFailureException {

		/*
		 * try { Files.move(targetFile.toPath(), sourceFile.toPath(), REPLACE_EXISTING);
		 * } catch (IOException e) { throw new MojoFailureException("error moving file",
		 * e); }
		 */
		Commandline cmd = this.createJDepsCommandLine(sourceFile);

		cmd.setExecutable(jdepsExecutable);
		String name = sourceFile.getName();
		int i = name.lastIndexOf('-');
		name = name.substring(0, i) + ".jdeps";

		File file = new File(sourceFile.getParent(), name);

		try (FileOutputStream fout = new FileOutputStream(file)) {
			executeCommand(cmd, fout);
		} catch (IOException ioe) {
			getLog().error("error creating .jdeps file");
		} catch (MojoExecutionException mee) {
			throw mee;
		}

		List<String> deps = new ArrayList<>();
		List<String> automaticDeps = new ArrayList<>();
		List<String> linkedDeps = new ArrayList<>();
		List<String> linkedSystemDeps = new ArrayList<>();

		// fill with empty values first in case there is an error later

		allModulesMap.put(nodeString, deps);
		automaticModulesMap.put(nodeString, automaticDeps);
		linkedModulesMap.put(nodeString, linkedDeps);
		linkedSystemModulesMap.put(nodeString, linkedSystemDeps);

		try (FileReader fr = new FileReader(file); BufferedReader br = new BufferedReader(fr)) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("Warning:")) {
					if (!warnings.contains(line)) {
						if (line.startsWith("Warning: split package:")) {
							String e[] = line.split(" ");
							if (e.length == 6) {
								int i1=e[4].lastIndexOf(File.separatorChar);
								if ( i1 == -1 ) {
									i1=0;
								}
								String a1 = e[4].substring(i1);
								int i2=e[5].lastIndexOf(File.separatorChar);
								if ( i2 == -1 ) {
									i2=0;
								}
								String a2 = e[5].substring(i2);
								if (!a1.equals(a2)) {
									warnings.add("e.length=" + e.length);
									warnings.add("a1=" + a1);
									warnings.add("a2=" + a2);
									warnings.add(line);
								}
							} else {
								warnings.add(line);
							}
						} else {
							warnings.add(line);
						}
					}
				} else if (line.startsWith("Error:")) {
					if (!errors.contains(line)) {
						errors.add(line);
					}
				} else if (useListDeps && line.contains(" ")) {
					if (!line.contains("unamed module:")) {
						warnings.add(line);
					}
				} else {
					for (String dep : line.split(",")) {

						// remove optional package name, if any
						int ndx = dep.indexOf('/');
						if (ndx > 0) {
							dep = dep.substring(0, ndx);
						}

						if (!deps.contains(dep)) {
							deps.add(dep);
						}
						if (!allModules.contains(dep)) {
							allModules.add(dep);
						}
						if (systemModules.contains(dep)) {
							if (!linkedSystemModules.contains(dep)) {
								linkedSystemModules.add(dep);
							}
							if (!linkedSystemDeps.contains(dep)) {
								linkedSystemDeps.add(dep);
							}
						} else {
							if (automaticDep) {
								if (!automaticDeps.contains(dep)) {
									automaticDeps.add(dep);
								}
							} else {
								if (!linkedModules.contains(dep)) {
									linkedModules.add(dep);
								}
								if (!linkedDeps.contains(dep)) {
									linkedDeps.add(dep);
								}
							}
						}

					}
				}
			}

			// Files.move(sourceFile.toPath(), targetFile.toPath(), REPLACE_EXISTING);

		} catch (IOException ioe) {
			throw new MojoExecutionException("i/o error", ioe);
		}

		allModulesMap.put(nodeString, deps);
		automaticModulesMap.put(nodeString, automaticDeps);
		linkedModulesMap.put(nodeString, linkedDeps);
		linkedSystemModulesMap.put(nodeString, linkedSystemDeps);
	}

	protected void handleNonModJar(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {

		getLog().debug("handleNonModJar:" + artifact.getFile());

		boolean isAutomatic = (entry == null || entry.getValue() == null) ? false : entry.getValue().isAutomatic();

		if ((classpathArtifacts != null) && (classpathArtifacts.contains(artifact))) {
			isAutomatic = false;
		}

		String nodeString = dependencyNode.toNodeString();

		if (!nodeStrings.contains(nodeString)) {
			nodeStrings.add(nodeString);
		}

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

			File sourceFile = null;

			if (isAutomatic) {
				if (outputDirectoryAutomaticJars != null) {
					if (generateAutomaticJdeps) {
						sourceFile = new File(outputDirectoryAutomaticJars, artifact.getFile().getName());
					}
				}
			} else {
				if (outputDirectoryClasspathJars != null) {
					if (generateClassPathJdeps) {
						sourceFile = new File(outputDirectoryClasspathJars, artifact.getFile().getName());
					}
				}
			}
			if (sourceFile != null) {
				generateJdeps(nodeString, sourceFile, isAutomatic);
			}

		}

		if (isAutomatic) {
			String name = entry.getValue().name();

			if (!automaticModules.contains(name)) {
				automaticModules.add(name);
			}
		}

	}

	protected void handleModJar(final DependencyNode dependencyNode, final Artifact artifact,
			Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {

		getLog().debug("handleModJar:" + artifact.getFile());

		String nodeString = dependencyNode.toNodeString();

		if (!nodeStrings.contains(nodeString)) {
			nodeStrings.add(nodeString);
		}

		if (generateModuleJdeps) {
			File sourceFile = new File(outputDirectoryModules, artifact.getFile().getName());

			generateJdeps(nodeString, sourceFile, false);

		}

		String name = entry.getValue().name();

		if (!linkedModules.contains(name)) {
			linkedModules.add(name);
		}

	}
	
	protected void handleUpdate4JConfig(final DependencyNode dependencyNode)
			throws MojoExecutionException, MojoFailureException {
	
		Artifact artifact = dependencyNode.getArtifact();
		Path source = artifact.getFile().toPath();
		Path target = outputDirectoryJPacktool
				.toPath().resolve("conf");
		if ( !target.toFile().exists()) {
			target.toFile().mkdirs();
		}
		target = target.resolve("update4j_"+artifact.getGroupId()+"_"+artifact.getArtifactId()+".xml");

		try {
			Files.copy(source, target, REPLACE_EXISTING);
		} catch (IOException e) {
			throw new MojoFailureException("i/o error", e);
		}
	}
	
	protected void executeCommand(final Commandline cmd, OutputStream outputStream) throws MojoExecutionException {
		ExecuteCommand.executeCommand(false, this.getLog(), cmd, outputStream);
	}

	public List<File> getClassPathElements() {
		return classPathElements;
	}

	public List<String> getSystemModules() {
		return systemModules;
	}

	public List<String> getLinkedSystemModules() {
		return linkedSystemModules;
	}

	public List<String> getAllModules() {
		return allModules;
	}

	public List<String> getLinkedModules() {
		return linkedModules;
	}

	public List<String> getAutomaticModules() {
		return automaticModules;
	}

	public List<String> getNodeStrings() {
		return nodeStrings;
	}

	public Map<String, List<String>> getAllModulesMap() {
		return allModulesMap;
	}

	public Map<String, List<String>> getLinkedModulesMap() {
		return linkedModulesMap;
	}

	public Map<String, List<String>> getAutomaticModulesMap() {
		return automaticModulesMap;
	}

	public Map<String, List<String>> getLinkedSystemModulesMap() {
		return linkedSystemModulesMap;
	}

	public List<String> getJarsOnClassPath() {
		return jarsOnClassPath;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public List<String> getErrors() {
		return errors;
	}

}
