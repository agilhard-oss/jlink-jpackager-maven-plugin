
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * @author bei
 *
 */
public abstract class AbstractToolMojo extends AbstractMojo {


	
	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	protected File buildDirectory;

	@Parameter(defaultValue = "${project.build.directory}/maven-jpacktool", required = true, readonly = true)
	protected File outputDirectoryJPacktool;

	@Parameter(defaultValue = "${project.build.directory}/maven-jpacktool/jar-automatic", required = true, readonly = true)
	protected File outputDirectoryAutomaticJars;

	@Parameter(defaultValue = "${project.build.directory}/maven-jpacktool/jar", required = true, readonly = true)
	protected File outputDirectoryClasspathJars;

	@Parameter(defaultValue = "${project.build.directory}/maven-jpacktool/jmods", required = true, readonly = true)
	protected File outputDirectoryModules;

	@Parameter(defaultValue = "jpacktool", required = true, readonly = true)
	protected String jpacktoolPropertyPrefix;

	/**
	 * Artifacts that should be excluded
	 */
	@Parameter
	protected List<ArtifactParameter> excludedArtifacts;

	/**
	 * Artifacts that should be explicitly on the classpath
	 */
	@Parameter
	protected List<ArtifactParameter> classpathArtifacts;

	@Component
	protected LocationManager locationManager;

	@Component
	protected ToolchainManager toolchainManager;

	/**
	 * The JAR archiver needed for archiving the environments.
	 */
	@Component(role = Archiver.class, hint = "zip")
	protected ZipArchiver zipArchiver;

	/**
	 * The MavenProjectHelper
	 */
	@Component
	protected MavenProjectHelper mavenProjectHelper;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	protected MavenSession session;

	protected List<String> systemModules;

	/**
	 * This will turn on verbose mode.
	 * <p>
	 * The jlink/jpackager command line equivalent is: <code>--verbose</code>
	 * </p>
	 */
	@Parameter(defaultValue = "false")
	protected boolean verbose;

	
	@Parameter(defaultValue = "false")
	protected boolean skip;
	
	/**
	 * <p>
	 * Specify the requirements for this jdk toolchain. This overrules the toolchain
	 * selected by the maven-toolchain-plugin.
	 * </p>
	 * <strong>note:</strong> requires at least Maven 3.3.1
	 */
	@Parameter
	protected Map<String, String> jdkToolchain;

	private double javaVersion = 0;

	private String shouldSkipReason;
	
	protected Map<String, Object> jpacktoolModel;

	protected Map<String, Object> templateMap;
	
	/**
	 *
	 */
	public AbstractToolMojo() {
		super();
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		
		checkShouldSkip();
		
		if ( getShouldSkipReason() != null )  {
			getLog().warn("skipped due to: " + shouldSkipReason);
		} else {
			executeToolStart();
			executeToolMain();
			executeToolFinish();
		}
	}
	
	public void executeToolStart() throws MojoExecutionException, MojoFailureException {
		// can be overriden in derivced clases
	}
	
	public abstract void executeToolMain() throws MojoExecutionException, MojoFailureException;
	
	public void executeToolFinish() throws MojoExecutionException, MojoFailureException {
		// can be overriden in derivced clases
	}
	
	public String getPluginVersion() throws MojoFailureException {
		String v = null;
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("META-INF/maven/net.agilhard.maven.plugins/jpacktool-maven-plugin/pom.properties") ) {
			Properties props = new Properties();
			props.load(is);
			v=props.getProperty("version");
		} catch (IOException e) {
			throw new MojoFailureException("i/o error", e);
		}
		
		return v;
	}
	
	
	public void checkShouldSkip() {
		if ( skip ) {
			setShouldSkipReason("skip parameter is true");
		}
	}
	
	
	public String getShouldSkipReason() {
		return shouldSkipReason;
	}

	public void setShouldSkipReason(String shouldSkipReason) {
		this.shouldSkipReason = shouldSkipReason;
	}

	public double getJavaVersion() {

		if (javaVersion == 0) {

			String version = System.getProperty("java.version");
			int pos = version.indexOf('.');
			if (pos > -1) {
				pos = version.indexOf('.', pos + 1);
				if (pos == -1) {
					pos = version.length();
				}
			} else {
				pos = version.length();
			}
			version = version.substring(0, pos);
			pos = version.indexOf('-');
			if (pos > -1) {
				version = version.substring(0, pos);
			}
			javaVersion = Double.parseDouble(version);
		}

		return javaVersion;
	}

	protected String getToolExecutable(final String toolName) throws IOException {
		final Toolchain tc = this.getToolchain();

		String toolExecutable = null;
		if (tc != null) {
			toolExecutable = tc.findTool(toolName);
		}

		// TODO: Check if there exist a more elegant way?
		final String toolCommand = toolName + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");

		File toolExe;

		if (StringUtils.isNotEmpty(toolExecutable)) {
			toolExe = new File(toolExecutable);

			if (toolExe.isDirectory()) {
				toolExe = new File(toolExe, toolCommand);
			}

			if (SystemUtils.IS_OS_WINDOWS && toolExe.getName().indexOf('.') < 0) {
				toolExe = new File(toolExe.getPath() + ".exe");
			}

			if (!toolExe.isFile()) {
				throw new IOException(
						"The " + toolName + " executable '" + toolExe + "' doesn't exist or is not a file.");
			}
			return toolExe.getAbsolutePath();
		}

		// ----------------------------------------------------------------------
		// Try to find tool from System.getProperty( "java.home" )
		// By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
		// should be in the JDK_HOME
		// ----------------------------------------------------------------------
		toolExe = new File(SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "bin", toolCommand);

		// ----------------------------------------------------------------------
		// Try to find javadocExe from JAVA_HOME environment variable
		// ----------------------------------------------------------------------
		if (!toolExe.exists() || !toolExe.isFile()) {
			final Properties env = CommandLineUtils.getSystemEnvVars();
			final String javaHome = env.getProperty("JAVA_HOME");
			if (StringUtils.isEmpty(javaHome)) {
				throw new IOException("The environment variable JAVA_HOME is not correctly set.");
			}
			if (!new File(javaHome).getCanonicalFile().exists() || new File(javaHome).getCanonicalFile().isFile()) {
				throw new IOException("The environment variable JAVA_HOME=" + javaHome
						+ " doesn't exist or is not a valid directory.");
			}

			toolExe = new File(javaHome + File.separator + "bin", toolCommand);
		}

		if (!toolExe.getCanonicalFile().exists() || !toolExe.getCanonicalFile().isFile()) {
			throw new IOException("The " + toolName + " executable '" + toolExe
					+ "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
		}

		return toolExe.getAbsolutePath();
	}

	protected void executeCommand(final Commandline cmd) throws MojoExecutionException {
		ExecuteCommand.executeCommand(verbose, this.getLog(), cmd);
	}

	public Toolchain getToolchain() {
		Toolchain tc = null;

		if (this.jdkToolchain != null) {
			// Maven 3.3.1 has plugin execution scoped Toolchain Support
			try {
				final Method getToolchainsMethod = this.toolchainManager.getClass().getMethod("getToolchains",
						MavenSession.class, String.class, Map.class);

				@SuppressWarnings("unchecked")
				final List<Toolchain> tcs = (List<Toolchain>) getToolchainsMethod.invoke(this.toolchainManager,
						this.session, "jdk", this.jdkToolchain);

				if (tcs != null && tcs.size() > 0) {
					tc = tcs.get(0);
				}
			} catch (final ReflectiveOperationException e) {
				// ignore
			} catch (final SecurityException e) {
				// ignore
			} catch (final IllegalArgumentException e) {
				// ignore
			}
		}

		if (tc == null) {
			// TODO: Check if we should make the type configurable?
			tc = this.toolchainManager.getToolchainFromBuildContext("jdk", this.session);
		}

		return tc;
	}

	public MavenProject getProject() {
		return this.project;
	}

	public MavenSession getSession() {
		return this.session;
	}

	public File getOutputDirectoryJPacktool() {
		return outputDirectoryJPacktool;
	}

	public File getOutputDirectoryAutomaticJars() {
		return outputDirectoryAutomaticJars;
	}

	public File getOutputDirectoryClasspathJars() {
		return outputDirectoryClasspathJars;
	}

	public File getOutputDirectoryModules() {
		return outputDirectoryModules;
	}

	public String getJpacktoolPropertyPrefix() {
		return jpacktoolPropertyPrefix;
	}

	public List<ArtifactParameter> getExcludedArtifacts() {
		return excludedArtifacts;
	}

	public List<ArtifactParameter> getClasspathArtifacts() {
		return classpathArtifacts;
	}

	public LocationManager getLocationManager() {
		return locationManager;
	}

	public ToolchainManager getToolchainManager() {
		return toolchainManager;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public Map<String, String> getJdkToolchain() {
		return jdkToolchain;
	}

	public List<String> getSystemModules() throws MojoExecutionException {

		if (!outputDirectoryJPacktool.exists()) {
			outputDirectoryJPacktool.mkdirs();
		}

		if (systemModules == null) {

			systemModules = new ArrayList<String>();

			String javaExecutable;
			try {
				javaExecutable = getToolExecutable("java");
			} catch (IOException e) {
				throw new MojoExecutionException("i/o error", e);
			}

			final Commandline cmd = new Commandline();

			cmd.createArg().setValue("--list-modules");
			cmd.setExecutable(javaExecutable);

			File file = new File(outputDirectoryJPacktool, "java_modules.list");
			try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
				ExecuteCommand.executeCommand(false, getLog(), cmd, fileOutputStream);
			} catch (IOException ioe) {
				throw new MojoExecutionException("i/o error", ioe);
			}
			try (FileReader fr = new FileReader(file); BufferedReader br = new BufferedReader(fr)) {
				String line;
				while ((line = br.readLine()) != null) {
					int i = line.indexOf('@');
					if (i > 0) {
						line = line.substring(0, i);
					}
					systemModules.add(line);
				}
			} catch (IOException ioe) {
				throw new MojoExecutionException("i/o error", ioe);
			}
		}

		return systemModules;
	}

	/**
	 * Returns the artifact file to generate, based on an optional classifier.
	 *
	 * @param basedir    the output directory
	 * @param finalName  the name of the ear file
	 * @param classifier an optional classifier
	 * @param ext        The extension of the file.
	 * @return the file to generate
	 */
	protected File getArtifactFile(final File basedir, final String finalName, final String classifier,
			final String ext) {
		if (basedir == null) {
			throw new IllegalArgumentException("basedir is not allowed to be null");
		}
		if (finalName == null) {
			throw new IllegalArgumentException("finalName is not allowed to be null");
		}
		if (ext == null) {
			throw new IllegalArgumentException("archiveExt is not allowed to be null");
		}

		if (finalName.isEmpty()) {
			throw new IllegalArgumentException("finalName is not allowed to be empty.");
		}
		if (ext.isEmpty()) {
			throw new IllegalArgumentException("archiveExt is not allowed to be empty.");
		}

		final StringBuilder fileName = new StringBuilder(finalName);

		if (this.hasClassifier(classifier)) {
			fileName.append("-").append(classifier);
		}

		fileName.append('.');
		fileName.append(ext);

		return new File(basedir, fileName.toString());
	}

	protected boolean hasClassifier(final String classifier) {
		boolean result = false;
		if (classifier != null && classifier.trim().length() > 0) {
			result = true;
		}

		return result;
	}

	protected void publishJPacktoolProperties() {
		String finalName = getFinalName();
		if (finalName != null) {
			File propertiesFile = this.getArtifactFile(buildDirectory, finalName, "jpacktool_jdeps", "properties");
			if (propertiesFile.exists()) {
				this.mavenProjectHelper.attachArtifact(this.project, "properties", "jpacktool_jdeps", propertiesFile);
			}
		}
	}

	protected File createZipArchiveFromDirectory(final File outputDirectory, final File outputDirectoryToZip)
			throws MojoExecutionException {
		this.zipArchiver.addDirectory(outputDirectoryToZip);

		String finalName = getFinalName();
		File resultArchive = null;

		if (finalName != null) {
			resultArchive = this.getArtifactFile(outputDirectory, finalName, null, "zip");

			this.zipArchiver.setDestFile(resultArchive);
			try {
				this.zipArchiver.createArchive();
			} catch (final ArchiverException e) {
				this.getLog().error(e.getMessage(), e);
				throw new MojoExecutionException(e.getMessage(), e);
			} catch (final IOException e) {
				this.getLog().error(e.getMessage(), e);
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}

		return resultArchive;

	}

	public String getFinalName() {
		return null;
	}
	
}