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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.sonatype.plexus.build.incremental.BuildContext;

import net.agilhard.maven.plugins.jpacktool.base.handler.CollectArtifactsToLinkHandler;

/**
 * @author Karl Heinz Marbaise
 *         <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 * @author Bernd Eilers
 */
public abstract class AbstractPackageToolMojo extends AbstractTemplateToolMojo implements Contextualizable {

	
	/**
	 * JVM flags and options to pass to the application.
	 *
	 * <p>
	 * <code>--jvm-args &lt;args&gt;</code>
	 * </p>
	 */
	@Parameter(required = false, readonly = false)
	protected List<String> jvmArgs;

	/**
	 * JVM options the user may override along and their default values (see
	 * UserJvmOptionsService API for more details).
	 *
	 * <p>
	 * <code>--user-jvm-args &lt;args&gt;</code>
	 * </p>
	 */
	@Parameter(required = false, readonly = false)
	protected List<String> userJvmArgs;

	/**
	 * Command line arguments to pass to the main class if no arguments are
	 * specified by the launcher.
	 *
	 * <p>
	 * <code>--arguments &lt;args&gt;</code>
	 * </p>
	 */
	@Parameter(required = false, readonly = false)
	protected List<String> arguments;

	/**
	 * Resources to package into the image or installable package.
	 * 
	 * <p>
	 * You can use almost any configuration option that the 
	 * <a href="https://maven.apache.org/plugins/maven-resources-plugin/">Apache Maven Resources Plugin</a> 
	 * has on this setting.
	 * </p>
	 * <p>
	 * When filtering is set to true on a resource the 
	 * <a href="https://freemarker.apache.org/">Freemarker Java Template Engine</a>
	 * is being used to filter the contents and some variables set by the packaging process
	 * are available to it.
	 * </p>
	 * <p>
	 * Hava a look at the <a href="https://freemarker.apache.org/docs/dgui.html">Template Author's Guide</a>
	 * for details on how to write templates.
	 * </p>
	 */
	@Parameter
	protected PackagingResources packagingResources;

    /**
     * Set the JDK location to create a Java custom runtime image.
     */
    @Parameter
    protected File sourceJdkModules;

    /**
     * Just a constant for the jmods directory name.
     */
	protected static final String JMODS = "jmods";

	/**
	 * Name of the generated ZIP file in the <code>target</code> directory. This
	 * will not change the name of the installed/deployed file.
	 */
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	protected String finalName;	

	/**
	 * Flag to ignore automatic modules.
	 */
	@Parameter(defaultValue = "true")
	protected boolean ignoreAutomaticModules;

	/**
	 * Include additional paths on the <code>--module-path</code> option. Project
	 * dependencies and JDK modules are automatically added.
	 */
	@Parameter
	protected List<String> modulePaths;

	/**
	 * Limit the universe of observable modules. The following gives an example of
	 * the configuration which can be used in the <code>pom.xml</code> file.
	 *
	 * <pre>
	 *   &lt;limitModules&gt;
	 *     &lt;limitModule&gt;mod1&lt;/limitModule&gt;
	 *     &lt;limitModule&gt;xyz&lt;/limitModule&gt;
	 *     .
	 *     .
	 *   &lt;/limitModules&gt;
	 * </pre>
	 * <p>
	 * This configuration is the equivalent of the command line option:
	 * <code>--limit-modules &lt;mod&gt;[,&lt;mod&gt;...]</code>
	 * </p>
	 */
	@Parameter
	protected List<String> limitModules;

	@Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
	protected File outputDirectory;

	/**
	 * Toggle whether to add all modules in the java boot path to the limitModules
	 * setting.
	 */
	@Parameter(defaultValue = "false")
	protected boolean addJDKToLimitModules;

	/**
	 * Flag if to move classpath jars from jpacktool-prepare goal
	 */
	@Parameter(defaultValue = "true")
	protected boolean jPacktoolMoveClassPathJars;

	/**
	 * Flag if to move real modules from jpacktool-prepare goal
	 */
	@Parameter(defaultValue = "true")
	protected boolean jPacktoolMoveAutomaticModules;

	/**
	 * Flag if to move real modules from jpacktool-prepare goal. This can not be set
	 * to true when using jpackager because jpackager explicitly disallows modules
	 * in input directories.
	 */
	@Parameter(defaultValue = "false")
	protected boolean jPacktoolMoveRealModules;
	
	/**
	 * <p>
	 * Usually this is not necessary, cause this is handled automatically by the
	 * given dependencies.
	 * </p>
	 * <p>
	 * By using the --add-modules you can define the root modules to be resolved.
	 * The configuration in <code>pom.xml</code> file can look like this:
	 * </p>
	 *
	 * <pre>
	 * &lt;addModules&gt;
	 *   &lt;addModule&gt;mod1&lt;/addModule&gt;
	 *   &lt;addModule&gt;first&lt;/addModule&gt;
	 *   .
	 *   .
	 * &lt;/addModules&gt;
	 * </pre>
	 * <p>
	 * The command line equivalent for jlink is:
	 * <code>--add-modules &lt;mod&gt;[,&lt;mod&gt;...]</code>.
	 * </p>
	 */
	@Parameter
	protected List<String> addModules;
	
	/**
	 * Name of the classpath folder
	 */
	@Parameter(defaultValue = "jar")
	protected String classPathFolderName;

	/**
	 * Name of the automatic-modules folder
	 */
	@Parameter(defaultValue = "jar_auto")
	protected String automaticModulesFolderName;

	/**
	 * Name of the modules folder
	 */
	@Parameter(defaultValue = "jmods")
	protected String modulesFolderName;

	/**
	 * Fall back to using old unfiltered maven dependency handling.
	 */
	@Parameter(defaultValue = "false")
	protected boolean unfilteredDependencyHandling;

	
	/**
	 * The JAR archiver needed for archiving the environments.
	 */
	@Component
	protected BuildContext buildContext;

	protected Context context;

	/**
	 * The MavenResourcesFiltering Component.
	 */
	@Component(role = MavenResourcesFiltering.class, hint = "default")
	public MavenResourcesFiltering mavenResourcesFiltering;

	/**
	 * The MavenFileFilter component.
	 */
	@Component(role = MavenFileFilter.class, hint = "default")
	protected MavenFileFilter mavenFileFilter;

	/**
	 * The DependencyGraphBuilder component.
	 */
    @Component(role = DependencyGraphBuilder.class, hint = "maven31")
    protected DependencyGraphBuilder dependencyGraphBuilder;


    protected Collection<String> modulesToAdd = new ArrayList<>();
	
    protected Collection<String> pathsOfModules = new ArrayList<>();
	
    protected Collection<String> pathsOfArtifacts = new ArrayList<>();

    
	/**
	 * Flag if jpacktool-prepare goal has been used before
	 */
	protected boolean jpacktoolPrepareUsed;

	/**
	 * set jpacktoolPrepareUsed variable based on maven property
	 */
	protected void checkJpacktoolPrepareUsed() {
		Boolean b = (Boolean) this.project.getProperties().get(this.jpacktoolPropertyPrefix + ".used");
		jpacktoolPrepareUsed = b == null ? false : b.booleanValue();
	}

	/**
	 * initialize jpacktooModel
	 */
	@SuppressWarnings("unchecked")
	protected void initJPacktoolModel() {
		checkJpacktoolPrepareUsed();
		if (jpacktoolPrepareUsed) {
			jpacktoolModel = (Map<String, Object>) this.project.getProperties()
					.get(this.jpacktoolPropertyPrefix + ".model");
		}
	}


	/**
	 * resolve to path and create directory if not exists.
	 * @param dir the parent directory
	 * @param appFolderName the name of the application folder
	 * @param folderName the name of the folder in the application folder 
	 * @throws IOException if i/o error occured
	 * @return the resolved path
	 */
	protected Path resolveAndCreate(File dir, String appFolderName, String folderName) throws IOException {
		Path target = dir.toPath();
		if ((appFolderName != null) && (!"".equals(appFolderName))) {
			target = target.resolve(appFolderName);
		}
		if ((folderName != null) && (!"".equals(folderName))) {
			target = target.resolve(folderName);
		}
		if (!Files.exists(target)) {
			Files.createDirectories(target);
		}
		return target;
	}

	/**
	 * This will convert a module path separated by either {@code :} or {@code ;}
	 * into a string which uses the platform depend path separator uniformly.
	 *
	 * @param pluginModulePath The module path.
	 * @return The platform separated module path.
	 */
	protected StringBuilder convertSeparatedModulePathToPlatformSeparatedModulePath(final String pluginModulePath) {
		final StringBuilder sb = new StringBuilder();
		// Split the module path by either ":" or ";" linux/windows path separator and
		// convert uniformly to the platform used separator.
		final String[] splitModule = pluginModulePath.split("[;:]");
		for (final String module : splitModule) {
			if (sb.length() > 0) {
				sb.append(File.pathSeparatorChar);
			}
			sb.append(module);
		}
		return sb;
	}

	/**
	 * Convert a list into a string which is separated by platform depend path
	 * separator.
	 *
	 * @param modulePaths The list of elements.
	 * @return The string which contains the elements separated by
	 *         {@link File#pathSeparatorChar}.
	 */
	protected String getPlatformDependSeparateList(final Collection<String> modulePaths) {
		final StringBuilder sb = new StringBuilder();
		for (final String module : modulePaths) {
			if (sb.length() > 0) {
				sb.append(File.pathSeparatorChar);
			}
			sb.append(module);
		}
		return sb.toString();
	}

	/**
	 * Convert a list into a
	 * 
	 * @param modules The list of modules.
	 * @return The string with the module list which is separated by {@code ,}.
	 */
	protected String getCommaSeparatedList(final Collection<String> modules) {
		final StringBuilder sb = new StringBuilder();
		for (final String module : modules) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(module);
		}
		return sb.toString();
	}

	/**
	 * Convert a list into a
	 * 
	 * @param modules The list of modules.
	 * @return The string with the module list which is separated by {@code ,}.
	 */
	public String getPathSeparatedList(final Collection<String> modules) {
		final StringBuilder sb = new StringBuilder();
		for (final String module : modules) {
			if (sb.length() > 0) {
				sb.append(File.pathSeparatorChar);
			}
			sb.append(module);
		}
		return sb.toString();
	}

	/**
	 * Get  project dependencies as List of files to the artifacts.
	 * 
	 * <p>
	 * This is the old unfiltered maven dependency handling behaviour turn on
	 * with the {@link #unfilteredDependencyHandling} Parameter.
	 * </p>
	 * @param project the maven project
	 * @return list of file elements for artifacts.
	 */
	protected List<File> getCompileClasspathElements(final MavenProject project) {
		final List<File> list = new ArrayList<>(project.getArtifacts().size() + 1);

		for (final Artifact a : project.getArtifacts()) {
			this.getLog().debug("Artifact: " + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + ":" + a.getClassifier()+":"+a.getType());
			list.add(a.getFile());
		}
		return list;
	}


	/**
	 * Get project dependencies as List of files to the artifacts.
	 * 
	 * <p>
	 * This is the new filtered maven dependency behaviour which can be turned off
	 * with the {@link #unfilteredDependencyHandling} Parameter.
	 * </p>
	 * @return list of file elements for artifacts.
	 * @throws MojoExecutionException on plugin execution error
	 * @throws MojoFailureException on plugin failure
	 */
	protected List<File> getDependenciesToLink() throws MojoExecutionException, MojoFailureException {

		getLog().debug("dependencyGraphBuilder="+dependencyGraphBuilder);
		CollectArtifactsToLinkHandler handler = new CollectArtifactsToLinkHandler(this, dependencyGraphBuilder);
		handler.execute();
	
		List<Artifact> artifacts = handler.getElements();
		
		final List<File> list = new ArrayList<>(artifacts.size() + 1);

		for (final Artifact a : artifacts ) {
			this.getLog().debug("Artifact: " + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + ":" + a.getClassifier()+":"+a.getType());
			list.add(a.getFile());
		}
		return list;
	}
	
	protected Map<String, File> getModulePathElements() throws MojoFailureException, MojoExecutionException {
		// For now only allow named modules. Once we can create a graph with ASM we can
		// specify exactly the modules
		// and we can detect if auto modules are used. In that case,
		// MavenProject.setFile() should not be used, so
		// you cannot depend on this project and so it won't be distributed.

		final Map<String, File> modulepathElements = new HashMap<>();

		try {
			//final Collection<File> dependencyArtifacts = this.getCompileClasspathElements(this.getProject());
			Collection<File> dependencyArtifacts;
			
			if ( unfilteredDependencyHandling ) {
				// fallback to old old unfiltered maven dependency handling
				dependencyArtifacts= this.getCompileClasspathElements(this.getProject());
			} else {
				dependencyArtifacts= this.getDependenciesToLink();
			}
			
			final ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(dependencyArtifacts);

			final Toolchain toolchain = this.getToolchain();
			if ((toolchain != null) && (toolchain instanceof DefaultJavaToolChain)) {
				request.setJdkHome(new File(((DefaultJavaToolChain) toolchain).getJavaHome()));
			}

			final ResolvePathsResult<File> resolvePathsResult = this.locationManager.resolvePaths(request);

			for (final Map.Entry<File, JavaModuleDescriptor> entry : resolvePathsResult.getPathElements().entrySet()) {
				if (entry.getValue() == null) {

					if ( ! isJpacktoolPrepareUsed()) {
						final String message = "The given dependency " + entry.getKey()
						+ " does not have a module-info.java file.\n"
						+ " So it can't be linked when using packaging jlink or packaging jpackager.\n"
						+ " Use packaging jpacktool-jlink or packaging jpacktool-jpackager instead.";
						
						this.getLog().error(message);
						throw new MojoFailureException(message);
					}
					else if (isVerbose()) {
						final String message = "The given dependency " + entry.getKey()
						+ " does not have a module-info.java file. So it can't be linked it is put on the classpath or module-path instead.";
						this.getLog().info(message);
					}
				} else {

					// Don't warn for automatic modules, let the jlink tool do that
					this.getLog().debug(
							" module: " + entry.getValue().name() + " automatic: " + entry.getValue().isAutomatic());
					if (modulepathElements.containsKey(entry.getValue().name())) {
						this.getLog().warn("The module name " + entry.getValue().name() + " does already exists.");
					} else {

						if (this.ignoreAutomaticModules) {
							// just do not add automatic modules
							if (!entry.getValue().isAutomatic()) {
								modulepathElements.put(entry.getValue().name(), entry.getKey());
							}
						} else {
							modulepathElements.put(entry.getValue().name(), entry.getKey());
						}
					}
				}
			}

			// This part is for the module in target/classes ? (Hacky..)
			// FIXME: Is there a better way to identify that code exists?
			final AtomicBoolean b = new AtomicBoolean();

			if (this.outputDirectory.exists()) {
				final BiPredicate<Path, BasicFileAttributes> predicate = (path, attrs) -> {
					return path.toString().endsWith(".class");
				};

				try (Stream<Path> stream = Files.find(Paths.get(this.outputDirectory.toURI()), Integer.MAX_VALUE,
						predicate)) {
					stream.forEach(name -> {
						b.set(true);
					});
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
			if (b.get()) {
				final List<File> singletonList = Collections.singletonList(this.outputDirectory);

				final ResolvePathsRequest<File> singleModuls = ResolvePathsRequest.ofFiles(singletonList);

				final ResolvePathsResult<File> resolvePaths = this.locationManager.resolvePaths(singleModuls);
				for (final Entry<File, JavaModuleDescriptor> entry : resolvePaths.getPathElements().entrySet()) {
					if (entry.getValue() == null) {
						final String message = "The given project " + entry.getKey()
								+ " does not contain a module-info.java file. So it can't be linked.";
						this.getLog().error(message);
						throw new MojoFailureException(message);
					}
					if (modulepathElements.containsKey(entry.getValue().name())) {
						this.getLog().warn("The module name " + entry.getValue().name() + " does already exists.");
					}
					modulepathElements.put(entry.getValue().name(), entry.getKey());
				}
			}

		} catch (final IOException e) {
			this.getLog().error(e.getMessage());
			throw new MojoFailureException(e.getMessage());
		}

		return modulepathElements;
	}

	protected void prepareModules(final File jmodsFolder) throws MojoFailureException, MojoExecutionException {
		this.prepareModules(jmodsFolder, false, false, null);
	}

	protected void prepareModules(final File jmodsFolder, final boolean useDirectory, final boolean copyArtifacts,
			final File moduleTempDirectory) throws MojoFailureException, MojoExecutionException {

		if (this.addModules != null) {
			this.modulesToAdd.addAll(this.addModules);
		}

		if (this.modulePaths != null) {
			this.pathsOfModules.addAll(this.modulePaths);
		}

		// add dependencies only if not jPacktoolMoveRealModules is set

		if (!(jPacktoolMoveRealModules && jpacktoolPrepareUsed)) {

			if (copyArtifacts) {
				if (moduleTempDirectory != null) {
					try {
						this.pathsOfModules.add(moduleTempDirectory.getCanonicalPath());
					} catch (IOException e) {
						throw new MojoFailureException("i/o error:", e);
					}
				}
			}

			if ((outputDirectoryModules != null) && (outputDirectoryModules.isDirectory())) {
				try {
					this.pathsOfModules.add(outputDirectoryModules.getCanonicalPath());
				} catch (IOException e) {
					throw new MojoFailureException("i/o error:", e);
				}
			}

			for (final Entry<String, File> item : this.getModulePathElements().entrySet()) {
				this.getLog().info(" -> module: " + item.getKey() + " ( " + item.getValue().getPath() + " )");

				// We use the real module name and not the artifact Id...
				this.modulesToAdd.add(item.getKey());
				if (copyArtifacts) {
					if (!outputDirectoryModules.isDirectory()) {
						this.pathsOfArtifacts.add(item.getValue().getPath());
					}
				} else {
					if (useDirectory) {
						this.pathsOfModules.add(item.getValue().getParentFile().getPath());
					} else {
						this.pathsOfModules.add(item.getValue().getPath());
					}
				}
			}
		}

		if (jmodsFolder != null) {
			// The jmods directory of the JDK
			try {
				this.pathsOfModules.add(jmodsFolder.getCanonicalPath());
			} catch (IOException e) {
				throw new MojoFailureException("i/o error:", e);
			}
		}

	}

	/**
	 * add system modules from jpacktool-prepare goal
	 */
	protected void addSystemModulesFromJPackToolPrepare() {
		if (jpacktoolPrepareUsed && (jpacktoolModel != null)) {
			@SuppressWarnings("unchecked")
			List<String> linkedSystemModules = (List<String>) jpacktoolModel.get("linkedSystemModules");

			if (modulesToAdd == null) {
				modulesToAdd = new ArrayList<String>();
			}
			for (String mod : linkedSystemModules) {
				if (!(modulesToAdd.contains(mod))) {
					modulesToAdd.add(mod);
				}
			}
		}

	}


	protected void failIfProjectHasAlreadySetAnArtifact() throws MojoExecutionException {
		if (this.projectHasAlreadySetAnArtifact()) {
			throw new MojoExecutionException("You have to use a classifier "
					+ "to attach supplemental artifacts to the project instead of replacing them.");
		}
	}

	protected boolean projectHasAlreadySetAnArtifact() {
		if (this.getProject().getArtifact().getFile() != null) {
			return this.getProject().getArtifact().getFile().isFile();
		} else {
			return false;
		}
	}

	protected boolean hasLimitModules() {
		return this.limitModules != null && !this.limitModules.isEmpty();
	}


	protected void addToLimitModules(String name) {
		if (limitModules == null) {
			limitModules = new ArrayList<String>();
		}
		if (!limitModules.contains(name)) {
			getLog().info("addToLimitModules name=" + name);

			limitModules.add(name);
		}
	}

	protected void addSystemModulesToLimitModules() throws MojoExecutionException {
		if (limitModules == null) {
			limitModules = new ArrayList<String>();
		}
		limitModules.addAll(this.getSystemModules());

	}


	protected void appendOrCreateJvmArgPath(String opt1, String opt2, String value) {
		if (jvmArgs == null) {
			jvmArgs = new ArrayList<String>();
		}

		int i = 0;
		int fi = -1;

		for (String arg : jvmArgs) {
			if (arg.equals(opt1) || arg.equals(opt2)) {
				fi = i + 1;
			}
			i++;
		}
		if ((fi > 0) && (jvmArgs.size() > fi)) {
			String oldValue = jvmArgs.get(fi);
			jvmArgs.set(fi, oldValue + ":" + value);
		} else {
			jvmArgs.add(opt1);
			jvmArgs.add(value);
		}
	}

	protected abstract void updateJvmArgs() throws MojoFailureException;
	
	@SuppressWarnings("unchecked")
	protected void updateJvmArgs(String appFolderName) throws MojoFailureException {

		if (jvmArgs == null) {
			jvmArgs = new ArrayList<String>();
		}

		if (jpacktoolModel == null) {
			jpacktoolModel = new HashMap<String, Object>();
		}

		if ((jPacktoolMoveAutomaticModules || jPacktoolMoveRealModules) && jpacktoolPrepareUsed) {
			StringBuffer sb = new StringBuffer();
			if (jPacktoolMoveAutomaticModules) {
				if (appFolderName != null) {
					sb.append(appFolderName);
					sb.append(File.separator);
				}
				sb.append(automaticModulesFolderName);
				if (jPacktoolMoveRealModules) {
					sb.append(':');
				}
			}
			if (jPacktoolMoveRealModules) {
				if (appFolderName != null) {
					sb.append(appFolderName);
					sb.append(File.separator);
				}
				sb.append(modulesFolderName);
			}
			String s = sb.toString();
			if (!"".equals(s)) {
				appendOrCreateJvmArgPath("--module-path", "-p", s);
				jpacktoolModel.put("additionalModulePath", s);
			}

		}

		if (jPacktoolMoveClassPathJars && jpacktoolPrepareUsed) {
			StringBuffer sb1 = new StringBuffer();
			if (appFolderName != null) {
				sb1.append(appFolderName);
				sb1.append(File.separator);
			}
			sb1.append(classPathFolderName);

			String classPathPrefix = sb1.toString();

			StringBuffer sb = new StringBuffer();

			boolean b = false;
			for (String jarOnClassPath : (List<String>) jpacktoolModel.get("jarsOnClassPath")) {
				if (b) {
					sb.append(':');
				} else {
					b = true;
				}
				sb.append(classPathPrefix);
				sb.append(File.separator);
				sb.append(jarOnClassPath);
			}

			String s = sb.toString();
			if (!"".equals(s)) {
				appendOrCreateJvmArgPath("--class-path", "-p", s);
				jpacktoolModel.put("additionalClassPath", s);
			}

		}
	}

	protected void updateModel() throws MojoFailureException {
		if (jpacktoolModel == null) {
			jpacktoolModel = new HashMap<String, Object>();
		}

		updateJvmArgs();

		StringBuffer sb = new StringBuffer();
		boolean b = false;

		if ((jvmArgs != null) && (jvmArgs.size() > 0)) {
			for (String arg : jvmArgs) {
				if (b) {
					sb.append(' ');
				} else {
					b = true;
				}
				sb.append(arg);
			}
			jpacktoolModel.put("jvmArgs", sb.toString());
		}

		if ((arguments != null) && (arguments.size() > 0)) {

			sb = new StringBuffer();
			b = false;
			for (String arg : arguments) {
				if (b) {
					sb.append(' ');
				} else {
					b = true;
				}
				sb.append(arg);
			}
			jpacktoolModel.put("arguments", sb.toString());
		}
	}

	public Context getContext() {
		return context;
	}

	public List<String> getJvmArgs() {
		return jvmArgs;
	}

	public List<String> getUserJvmArgs() {
		return userJvmArgs;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public File getOutputDirectoyTemplates() {
		return outputDirectoyTemplates;
	}

	public PackagingResources getPackagingResources() {
		return packagingResources;
	}

	public String getFinalName() {
		return finalName;
	}

	public ZipArchiver getZipArchiver() {
		return zipArchiver;
	}

	public BuildContext getBuildContext() {
		return buildContext;
	}

	public MavenResourcesFiltering getMavenResourcesFiltering() {
		return mavenResourcesFiltering;
	}

	public MavenFileFilter getMavenFileFilter() {
		return mavenFileFilter;
	}

	public boolean isIgnoreAutomaticModules() {
		return ignoreAutomaticModules;
	}

	public List<String> getModulePaths() {
		return modulePaths;
	}

	public List<String> getLimitModules() {
		return limitModules;
	}

	public Collection<String> getModulesToAdd() {
		return modulesToAdd;
	}

	public Collection<String> getPathsOfModules() {
		return pathsOfModules;
	}

	public Collection<String> getPathsOfArtifacts() {
		return pathsOfArtifacts;
	}

	public File getBuildDirectory() {
		return buildDirectory;
	}

	public File getOutputDirectory() {
		return outputDirectory;
	}

	public boolean isAddJDKToLimitModules() {
		return addJDKToLimitModules;
	}

	public boolean isjPacktoolMoveClassPathJars() {
		return jPacktoolMoveClassPathJars;
	}

	public boolean isjPacktoolMoveAutomaticModules() {
		return jPacktoolMoveAutomaticModules;
	}

	public boolean isjPacktoolMoveRealModules() {
		return jPacktoolMoveRealModules;
	}

	public List<String> getAddModules() {
		return addModules;
	}

	public String getClassPathFolderName() {
		return classPathFolderName;
	}

	public String getAutomaticModulesFolderName() {
		return automaticModulesFolderName;
	}

	public String getModulesFolderName() {
		return modulesFolderName;
	}

	public boolean isJpacktoolPrepareUsed() {
		return jpacktoolPrepareUsed;
	}

	public Map<String, Object> getJpacktoolModel() {
		return jpacktoolModel;
	}

	/** {@inheritDoc} */
	public void contextualize(Context context) throws ContextException {
		this.context = context;
	}

	
	protected abstract void generateContent() throws MojoExecutionException;
	
	protected void generateContent(File outputDirectory) throws MojoExecutionException {
	}
	
	protected void executeResources(File outputDirectory) throws MojoExecutionException {
		if (packagingResources != null) {
			ResourcesExecutor resourcesExecutor = new ResourcesExecutor(this, packagingResources, getTemplateMap());
			if (context != null) {
				try {
					resourcesExecutor.contextualize(context);
				} catch (ContextException e) {
					throw new MojoExecutionException("can not apply context");
				}
			}
			resourcesExecutor.setOutputDirectory(outputDirectory);
			resourcesExecutor.execute();
		}
	}

	protected abstract void executeResources() throws MojoExecutionException;


}
