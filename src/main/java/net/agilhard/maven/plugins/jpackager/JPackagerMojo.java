package net.agilhard.maven.plugins.jpackager;

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
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import net.agilhard.maven.plugins.jlink.AbstractPackageToolMojo;

/**
 * @author Bernd Eilers
 */

public class JPackagerMojo extends AbstractPackageToolMojo 
{

    /**
     * The output directory for the resulting Application Image or Package.
     * <code>--output &lt;path&gt;</code>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-out", required = true, readonly = true )
    private File outputDirectoryPackage;

    /**
     * The output directory for the resulting Application Image or Package.
     * <code>--output &lt;path&gt;</code>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-in", required = true, readonly = true )
    private File inputDirectoryPackage;

    /**
     * Directory in which to use and place temporary files.
     * <code>--build-root &lt;path&gt;</code>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-build", required = true, readonly = false )
    private File buildRootPackage;

    /**
     * List of files in the base directory. If omitted, all files from "input"
     * directory (which is a mandatory argument in this case) will be packaged.
     * <code>--files &lt;files&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private List<File> files;

    /**
     * Name of the application. <code>--name &lt;name&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String name;

    /**
     * The main JAR of the application. This JAR should have the main-class, and is
     * relative to the assembled application directory.
     * <code>--main-jar&lt;jarname&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String mainJar;

    /**
     * Qualified name of the application class to execute.
     * <code>-class &lt;className&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String className;

    /**
     * Version of the application.
     * <code>--version &lt;version&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String version;

    /**
     * Command line arguments to pass to the main class if no arguments
     * are specified by the launcher.
     * <code>--arguments &lt;args&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private List<String> arguments;

    /**
     * Icon of the application bundle.
     * <code>--icon &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private File icon;

    /**
     * Prevents multiple instances of the application from launching
     * (see SingleInstanceService API for more details).
     * <code>--singleton</code>
     */
    @Parameter( required = false, readonly = false )
    private boolean singleton;

    /**
     * Machine readable identifier of the application. The format
     * must be a DNS name in reverse order, such as com.example.myapplication.
     * The identifier is used for composing Single Instance unique id and 
     * calculating preferences node to search for User JVM Options
     * (the format is a slash delimited version of the main package name,
     * such as "com/example/myapplication"), see UserJvmOptionsService API
     * for more details.
     * <code>--identifier &lt;<identifier>&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String identifier;
    
    /**
     * Removes native executables from the custom run-time images.
     * <code>--strip-native-commands</code>
     */
    @Parameter( required = false, readonly = false )
    private boolean stripNativeCommands;
    
    /**
     * JVM flags and options to pass to the application.
     * <code>--jvm-args &lt;args&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private List<String> jvmArgs;
    
    /**
     * JVM options the user may override along and their default values
     * (see UserJvmOptionsService API for more details).
     * <code>--user-jvm-args &lt;args&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private List<String> userJvmArgs;
    
    /**
     * Properties file that contains list of key=value parameters that
     * describe a file association. &quot;extension&quot;, &quot;mime-type&quot;, &quot;icon&quot;,
     * &quot;description&quot; can be used as keys for the association.
     * <code>--file-associations &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private File fileAssociations;
    
    /**
     * Properties file that contains a collection of options for a secondary launcher.
     * <code>--secondary-launcher &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private File secondaryLauncher;
    
    /**
     * Location of the predefined runtime image that is used to build
     * an application image and installable package.
     * 
     * <code>--runtime-image &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private File runtimeImage;

    /**
     * Location of the predefined application image that is used to build
     * an installable package.
     * 
     * <code>--app-image &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private File appImage;

    /**
     * Qualified name of the application class to execute.
     * <code>-install-dir &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String installDir;
    
    /**
     * Removes native executables from the custom run-time images.
     * <code>--strip-native-commands</code>
     */
    @Parameter( required = false, readonly = false )
    private boolean echoMode;
    
    /**
     * The license file, relative to the base directory.
     *
     * <code>--license-file &lt;path&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String licenseFile;

    
    /**
     * Copyright for the application.
     *
     * <code>--copyright &lt;text&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String copyright;

    /**
     * Description of the application.
     *
     * <code>--description &lt;text&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String description;

    /**
     * Category or group of the application
     *
     * <code>--category &lt;text&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String category;

    /**
     * Vendor of the application.
     *
     * <code>--vendor &lt;text&gt;</code>
     */
    @Parameter( defaultValue = "${project.organization}", required = false, readonly = false )
    private String vendor;

    
    protected String getJPackagerExecutable()
        throws IOException

    {
        return getToolExecutable( "jpackager" );
    }

    public void execute() throws MojoExecutionException, MojoFailureException 
    {

        String jPackagerExec = getExecutable();

        getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jpackager [ " + jPackagerExec + " ]" );

        // TODO: Find a more better and cleaner way?
        File jLinkExecuteable = new File( jPackagerExec );

        // Really Hacky...do we have a better solution to find the jmods directory of
        // the JDK?
        File jLinkParent = jLinkExecuteable.getParentFile().getParentFile();
        File jmodsFolder = new File( jLinkParent, JMODS );

        getLog().debug( " Parent: " + jLinkParent.getAbsolutePath() );
        getLog().debug( " jmodsFolder: " + jmodsFolder.getAbsolutePath() );

        ifOutputDirectoryExistsDeleteIt();

        prepareModules( jmodsFolder );

    }

    private String getExecutable() throws MojoFailureException 
    {
        String jPackagerExec;
        try 
        {
            jPackagerExec = getJPackagerExecutable();
        }
        catch ( IOException e )
        {
            throw new MojoFailureException( "Unable to find jpackager command: " + e.getMessage(), e );
        }
        return jPackagerExec;
    }

    private void ifOutputDirectoryExistsDeleteIt() throws MojoExecutionException 
    {
        if ( outputDirectoryPackage.exists() )
        {
            // Delete the output folder of JPackager before we start
            // otherwise JPackager will fail with a message "Error: directory already
            // exists: ..."
            try
            {
                getLog().debug( "Deleting existing " + outputDirectoryPackage.getAbsolutePath() );
                FileUtils.forceDelete( outputDirectoryPackage );
            }
            catch ( IOException e )
            {
                getLog().error( "IOException", e );
                throw new MojoExecutionException(
                        "Failure during deletion of " + outputDirectoryPackage.getAbsolutePath() + " occured." );
            }
        }
    }

}
