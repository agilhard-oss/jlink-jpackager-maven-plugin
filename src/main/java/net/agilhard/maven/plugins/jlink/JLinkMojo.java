package net.agilhard.maven.plugins.jlink;

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
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * The JLink goal is intended to create a Java Run Time Image file based on
 * <a href="http://openjdk.java.net/jeps/282">http://openjdk.java.net/jeps/282</a>,
 * <a href="http://openjdk.java.net/jeps/220">http://openjdk.java.net/jeps/220</a>.
 * 
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 */
// CHECKSTYLE_OFF: LineLength
@Mojo( name = "jlink", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true )
// CHECKSTYLE_ON: LineLength
public class JLinkMojo
    extends AbstractPackageToolMojo
{

    /**
     * This is intended to strip debug information out. The command line equivalent of <code>jlink</code> is:
     * <code>-G, --strip-debug</code> strip debug information.
     */
    @Parameter( defaultValue = "false" )
    private boolean stripDebug;

    /**
     * Here you can define the compression of the resources being used. The command line equivalent is:
     * <code>-c, --compress=level&gt;</code>. The valid values for the level are: <code>0, 1, 2</code>.
     */
    @Parameter
    private Integer compress;

    /**
     * Should the plugin generate a launcher script by means of jlink? The command line equivalent is:
     * <code>--launcher &lt;name&gt;=&lt;module&gt;[/&lt;mainclass&gt;]</code>. The valid values for the level are:
     * <code>&lt;name&gt;=&lt;module&gt;[/&lt;mainclass&gt;]</code>.
     */
    @Parameter
    private String launcher;


    /**
     * Define the plugin module path to be used. There can be defined multiple entries separated by either {@code ;} or
     * {@code :}. The jlink command line equivalent is: <code>--plugin-module-path &lt;modulepath&gt;</code>
     */
    @Parameter
    private String pluginModulePath;

    /**
     * The output directory for the resulting Run Time Image. The created Run Time Image is stored in non compressed
     * form. This will later being packaged into a <code>zip</code> file. <code>--output &lt;path&gt;</code>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jlink", required = true, readonly = true )
    private File outputDirectoryImage;

    /**
     * The byte order of the generated Java Run Time image. <code>--endian &lt;little|big&gt;</code>. If the endian is
     * not given the default is: <code>native</code>.
     */
    // TODO: Should we define either little or big as default? or should we left as it.
    @Parameter
    private String endian;


    /**
     * Add the option <code>--bind-services</code> or not.
     */
    @Parameter( defaultValue = "false" )
    private boolean bindServices;

    /**
     * You can disable a plugin by using this option. <code>--disable-plugin pluginName</code>.
     */
    @Parameter
    private String disablePlugin;

    /**
     * <code>--ignore-signing-information</code>
     */
    @Parameter( defaultValue = "false" )
    private boolean ignoreSigningInformation;

    /**
     * This will suppress to have an <code>includes</code> directory in the resulting Java Run Time Image. The JLink
     * command line equivalent is: <code>--no-header-files</code>
     */
    @Parameter( defaultValue = "false" )
    private boolean noHeaderFiles;

    /**
     * This will suppress to have the <code>man</code> directory in the resulting Java Run Time Image. The JLink command
     * line equivalent is: <code>--no-man-pages</code>
     */
    @Parameter( defaultValue = "false" )
    private boolean noManPages;

    /**
     * Suggest providers that implement the given service types from the module path.
     * 
     * <pre>
     * &lt;suggestProviders&gt;
     *   &lt;suggestProvider&gt;name-a&lt;/suggestProvider&gt;
     *   &lt;suggestProvider&gt;name-b&lt;/suggestProvider&gt;
     *   .
     *   .
     * &lt;/suggestProviders&gt;
     * </pre>
     * 
     * The jlink command line equivalent: <code>--suggest-providers [&lt;name&gt;,...]</code>
     */
    @Parameter
    private List<String> suggestProviders;

    /**
     * The JAR archiver needed for archiving the environments.
     */
    @Component( role = Archiver.class, hint = "zip" )
    private ZipArchiver zipArchiver;

    /**
     * Name of the generated ZIP file in the <code>target</code> directory. This will not change the name of the
     * installed/deployed file.
     */
    @Parameter( defaultValue = "${project.build.finalName}", readonly = true )
    private String finalName;

    protected String getJLinkExecutable()
        throws IOException
    {
        return getToolExecutable( "jlink" );
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

        String jLinkExec = getExecutable();

        getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jlink [ " + jLinkExec + " ]" );

        // TODO: Find a more better and cleaner way?
        File jLinkExecuteable = new File( jLinkExec );

        // Really Hacky...do we have a better solution to find the jmods directory of the JDK?
        File jLinkParent = jLinkExecuteable.getParentFile().getParentFile();
        File jmodsFolder = new File( jLinkParent, JMODS );

        getLog().debug( " Parent: " + jLinkParent.getAbsolutePath() );
        getLog().debug( " jmodsFolder: " + jmodsFolder.getAbsolutePath() );

        failIfParametersAreNotInTheirValidValueRanges();

        ifOutputDirectoryExistsDeleteIt();

        prepareModules( jmodsFolder );
        
        Commandline cmd;
        try
        {
            cmd = createJLinkCommandLine( pathsOfModules, modulesToAdd );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        cmd.setExecutable( jLinkExec );

        executeCommand( cmd, outputDirectoryImage );

        File createZipArchiveFromImage = createZipArchiveFromImage( buildDirectory, outputDirectoryImage );

        if ( projectHasAlreadySetAnArtifact() )
        {
            throw new MojoExecutionException( "You have to use a classifier "
                + "to attach supplemental artifacts to the project instead of replacing them." );
        }

        getProject().getArtifact().setFile( createZipArchiveFromImage );
    }



    private String getExecutable()
        throws MojoFailureException
    {
        String jLinkExec;
        try
        {
            jLinkExec = getJLinkExecutable();
        }
        catch ( IOException e )
        {
            throw new MojoFailureException( "Unable to find jlink command: " + e.getMessage(), e );
        }
        return jLinkExec;
    }

    private boolean projectHasAlreadySetAnArtifact()
    {
        if ( getProject().getArtifact().getFile() != null )
        {
            return getProject().getArtifact().getFile().isFile();
        }
        else
        {
            return false;
        }
    }

    private File createZipArchiveFromImage( File outputDirectory, File outputDirectoryImage )
        throws MojoExecutionException
    {
        zipArchiver.addDirectory( outputDirectoryImage );

        File resultArchive = getArchiveFile( outputDirectory, finalName, null, "zip" );

        zipArchiver.setDestFile( resultArchive );
        try
        {
            zipArchiver.createArchive();
        }
        catch ( ArchiverException e )
        {
            getLog().error( e.getMessage(), e );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        catch ( IOException e )
        {
            getLog().error( e.getMessage(), e );
            throw new MojoExecutionException( e.getMessage(), e );
        }

        return resultArchive;

    }

    private void failIfParametersAreNotInTheirValidValueRanges()
        throws MojoFailureException
    {
        if ( compress != null && ( compress < 0 || compress > 2 ) )
        {
            String message = "The given compress parameters " + compress + " is not in the valid value range from 0..2";
            getLog().error( message );
            throw new MojoFailureException( message );
        }

        if ( endian != null && ( !"big".equals( endian ) && !"little".equals( endian ) ) )
        {
            String message = "The given endian parameter " + endian
                + " does not contain one of the following values: 'little' or 'big'.";
            getLog().error( message );
            throw new MojoFailureException( message );
        }
    }

    private void ifOutputDirectoryExistsDeleteIt()
        throws MojoExecutionException
    {
        if ( outputDirectoryImage.exists() )
        {
            // Delete the output folder of JLink before we start
            // otherwise JLink will fail with a message "Error: directory already exists: ..."
            try
            {
                getLog().debug( "Deleting existing " + outputDirectoryImage.getAbsolutePath() );
                FileUtils.forceDelete( outputDirectoryImage );
            }
            catch ( IOException e )
            {
                getLog().error( "IOException", e );
                throw new MojoExecutionException( "Failure during deletion of " + outputDirectoryImage.getAbsolutePath()
                    + " occured." );
            }
        }
    }

    private Commandline createJLinkCommandLine( Collection<String> pathsOfModules, Collection<String> modulesToAdd )
        throws IOException
    {
        File file = new File( outputDirectoryImage.getParentFile(), "jlinkArgs" );
        if ( !getLog().isDebugEnabled() )
        {
            file.deleteOnExit();
        }
        file.getParentFile().mkdirs();
        file.createNewFile();

        PrintStream argsFile = new PrintStream( file );

        if ( stripDebug )
        {
            argsFile.println( "--strip-debug" );
        }

        if ( bindServices )
        {
            argsFile.println( "--bind-services" );
        }

        if ( endian != null )
        {
            argsFile.println( "--endian" );
            argsFile.println( endian );
        }
        if ( ignoreSigningInformation )
        {
            argsFile.println( "--ignore-signing-information" );
        }
        if ( compress != null )
        {
            argsFile.println( "--compress" );
            argsFile.println( compress );
        }
        if ( launcher != null )
        {
            argsFile.println( "--launcher" );
            argsFile.println( launcher );
        }

        if ( disablePlugin != null )
        {
            argsFile.println( "--disable-plugin" );
            argsFile.append( '"' ).append( disablePlugin ).println( '"' );

        }
        if ( pathsOfModules != null )
        {
            // @formatter:off
            argsFile.println( "--module-path" );
            argsFile.append( '"' )
                .append( getPlatformDependSeparateList( pathsOfModules )
                         .replace( "\\", "\\\\" ) ).println( '"' );
            // @formatter:off
        }

        if ( noHeaderFiles )
        {
            argsFile.println( "--no-header-files" );
        }

        if ( noManPages )
        {
            argsFile.println( "--no-man-pages" );
        }

        if ( hasSuggestProviders() )
        {
            argsFile.println( "--suggest-providers" );
            String sb = getCommaSeparatedList( suggestProviders );
            argsFile.println( sb );
        }

        if ( hasLimitModules() )
        {
            argsFile.println( "--limit-modules" );
            String sb = getCommaSeparatedList( limitModules );
            argsFile.println( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            argsFile.println( "--add-modules" );
            // This must be name of the module and *NOT* the name of the
            // file! Can we somehow pre check this information to fail early?
            String sb = getCommaSeparatedList( modulesToAdd );
            argsFile.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).println( '"' );
        }

        if ( pluginModulePath != null )
        {
            argsFile.println( "--plugin-module-path" );
            StringBuilder sb = convertSeparatedModulePathToPlatformSeparatedModulePath( pluginModulePath );
            argsFile.append( '"' ).append( sb.toString().replace( "\\", "\\\\" ) ).println( '"' );
        }

        if ( buildDirectory != null )
        {
            argsFile.println( "--output" );
            argsFile.println( outputDirectoryImage );
        }

        if ( verbose )
        {
            argsFile.println( "--verbose" );
        }
        argsFile.close();

        Commandline cmd = new Commandline();
        cmd.createArg().setValue( '@' + file.getAbsolutePath() );

        return cmd;
    }

    private boolean hasSuggestProviders()
    {
        return suggestProviders != null && !suggestProviders.isEmpty();
    }

    private boolean hasLimitModules()
    {
        return limitModules != null && !limitModules.isEmpty();
    }

}
