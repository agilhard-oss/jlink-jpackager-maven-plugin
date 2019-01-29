
package net.agilhard.maven.plugins.jpacktool.mojo.base;

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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * @author bei
 *
 */
public abstract class AbstractToolMojo extends AbstractMojo {

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

    /**
     * <p>
     * Specify the requirements for this jdk toolchain. This overrules the toolchain
     * selected by the maven-toolchain-plugin.
     * </p>
     * <strong>note:</strong> requires at least Maven 3.3.1
     */
    @Parameter
    protected Map<String, String> jdkToolchain;

    /**
     *
     */
    public AbstractToolMojo() {
        super();
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
        
        if ( !outputDirectoryJPacktool.exists() ) {
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


}