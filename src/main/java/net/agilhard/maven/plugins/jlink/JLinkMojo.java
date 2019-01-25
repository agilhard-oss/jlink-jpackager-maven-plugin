package net.agilhard.maven.plugins.jlink;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

import net.agilhard.maven.plugins.jpacktool.AbstractPackageToolMojo;

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
     * Name of the app folder
     */
    @Parameter( defaultValue = "app" )
    protected String appFolderName;

    
    protected Exception lastException;
    
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

    protected String getJLinkExecutable()
        throws IOException
    {
        return this.getToolExecutable( "jlink" );
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

    	checkJpacktoolPrepareUsed();
    	
    	if ( jpacktoolPrepareUsed ) {
    		this.addSystemModulesFromJPackTool();
    	}
    	
        final String jLinkExec = this.getExecutable();

        this.getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jlink [ " + jLinkExec + " ]" );

        // TODO: Find a more better and cleaner way?
        final File jLinkExecuteable = new File( jLinkExec );

        // Really Hacky...do we have a better solution to find the jmods directory of the JDK?
        final File jLinkParent = jLinkExecuteable.getParentFile().getParentFile();
        final File jmodsFolder = new File( jLinkParent, JMODS );

        this.getLog().debug( " Parent: " + jLinkParent.getAbsolutePath() );
        this.getLog().debug( " jmodsFolder: " + jmodsFolder.getAbsolutePath() );

        this.failIfParametersAreNotInTheirValidValueRanges();

        if ( addJDKToLimitModules ) {
        	this.addSystemModulesToLimitModules();
        }
        
        if ( limitModulesDirs != null ) {
        	for ( File dir : limitModulesDirs ) {
        		this.addModulesToLimitModules(dir.toPath());
        	}
        }
        
        this.ifOutputDirectoryExistsDeleteIt();

        this.prepareModules( jmodsFolder );

        Commandline cmd;
        try
        {
            cmd = this.createJLinkCommandLine( this.pathsOfModules, this.modulesToAdd );
        }
        catch ( final IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        cmd.setExecutable( jLinkExec );

        this.executeCommand(cmd);

        
        this.failIfProjectHasAlreadySetAnArtifact();

        if ( jpacktoolPrepareUsed ) {
        	try {
				this.moveJPacktoolJars();
			} catch (Exception e) {
	            throw new MojoExecutionException( e.getMessage() );
			}
        }

        final File createZipArchiveFromImage = this.createZipArchiveFromImage( this.buildDirectory, this.outputDirectoryImage );

        this.getProject().getArtifact().setFile( createZipArchiveFromImage );
    }

    private void moveJarToJLinkOutClasspath(Path source) throws IOException {
    	Path target = resolveAndCreate(outputDirectoryImage, appFolderName, classPathFolderName);

    	target = target.resolve(source.getFileName());
    	Files.move(source, target, REPLACE_EXISTING);
    }
    
    private void moveJarToJLinkOutAutomatic(Path source) throws IOException  {
    	Path target = resolveAndCreate(outputDirectoryImage, appFolderName, automaticModulesFolderName);

    	target = target.resolve(source.getFileName());
    	Files.move(source, target, REPLACE_EXISTING);
    }
    
    private void moveJarToJLinkOutModule(Path source) throws IOException  {
    	Path target = resolveAndCreate(outputDirectoryImage, appFolderName, modulesFolderName);

    	target = target.resolve(source.getFileName());
    	Files.move(source, target, REPLACE_EXISTING);
    }
    
    private void moveJPacktoolJars() throws Exception {
    	
    	lastException = null;

    	if ( this.jPacktoolMoveClassPathJars ) {
    	Files.newDirectoryStream(outputDirectoryClasspathJars.toPath(),
    	        path -> path.toString().endsWith(".jar"))
    	        .forEach(t -> {
					try {
						moveJarToJLinkOutClasspath(t);
					} catch (IOException e) {
						lastException=e;
					}
				});
    		if ( lastException != null ) {
    			throw lastException;
    		}
    	}

    	
    	if ( this.jPacktoolMoveAutomaticModules ) {
    	Files.newDirectoryStream(outputDirectoryAutomaticJars.toPath(),
    	        path -> path.toString().endsWith(".jar"))
    	        .forEach(t -> {
					try {
						moveJarToJLinkOutAutomatic(t);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
			if ( lastException != null ) {
				throw lastException;
			}
		}

    	
    	if ( this.jPacktoolMoveRealModules ) {
    	Files.newDirectoryStream(outputDirectoryClasspathJars.toPath(),
    	        path -> path.toString().endsWith(".jar"))
    	        .forEach(t -> {
					try {
						moveJarToJLinkOutModule(t);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
    		if ( lastException != null ) {
    			throw lastException;
    		}
    	}
    }

    private String getExecutable()
        throws MojoFailureException
    {
        String jLinkExec;
        try
        {
            jLinkExec = this.getJLinkExecutable();
        }
        catch ( final IOException e )
        {
            throw new MojoFailureException( "Unable to find jlink command: " + e.getMessage(), e );
        }
        return jLinkExec;
    }


    private void failIfParametersAreNotInTheirValidValueRanges()
        throws MojoFailureException
    {
        if ( this.compress != null && ( this.compress < 0 || this.compress > 2 ) )
        {
            final String message = "The given compress parameters " + this.compress + " is not in the valid value range from 0..2";
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }

        if ( this.endian != null && ( !"big".equals( this.endian ) && !"little".equals( this.endian ) ) )
        {
            final String message = "The given endian parameter " + this.endian
                + " does not contain one of the following values: 'little' or 'big'.";
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }
    }

    private void ifOutputDirectoryExistsDeleteIt()
        throws MojoExecutionException
    {
        if ( this.outputDirectoryImage.exists() )
        {
            // Delete the output folder of JLink before we start
            // otherwise JLink will fail with a message "Error: directory already exists: ..."
            try
            {
                this.getLog().debug( "Deleting existing " + this.outputDirectoryImage.getAbsolutePath() );
                FileUtils.forceDelete( this.outputDirectoryImage );
            }
            catch ( final IOException e )
            {
                this.getLog().error( "IOException", e );
                throw new MojoExecutionException( "Failure during deletion of " + this.outputDirectoryImage.getAbsolutePath()
                    + " occured." );
            }
        }
    }

    private Commandline createJLinkCommandLine( final Collection<String> pathsOfModules, final Collection<String> modulesToAdd )
        throws IOException
    {
        final File file = new File( this.outputDirectoryImage.getParentFile(), "jlinkArgs" );
        if ( !this.getLog().isDebugEnabled() )
        {
            file.deleteOnExit();
        }
        file.getParentFile().mkdirs();
        file.createNewFile();

        final PrintStream argsFile = new PrintStream( file );

        if ( this.stripDebug )
        {
            argsFile.println( "--strip-debug" );
        }

        if ( this.bindServices )
        {
            argsFile.println( "--bind-services" );
        }

        if ( this.endian != null )
        {
            argsFile.println( "--endian" );
            argsFile.println( this.endian );
        }
        if ( this.ignoreSigningInformation )
        {
            argsFile.println( "--ignore-signing-information" );
        }
        if ( this.compress != null )
        {
            argsFile.println( "--compress" );
            argsFile.println( this.compress );
        }
        if ( this.launcher != null )
        {
            argsFile.println( "--launcher" );
            argsFile.println( this.launcher );
        }

        if ( this.disablePlugin != null )
        {
            argsFile.println( "--disable-plugin" );
            argsFile.append( '"' ).append( this.disablePlugin ).println( '"' );

        }
        if ( pathsOfModules != null )
        {
            // @formatter:off
            argsFile.println( "--module-path" );
            argsFile
                .append( this.getPlatformDependSeparateList( pathsOfModules )
                         .replace( "\\", "\\\\" ) ).println("");
            // @formatter:off
        }

        if ( this.noHeaderFiles )
        {
            argsFile.println( "--no-header-files" );
        }

        if ( this.noManPages )
        {
            argsFile.println( "--no-man-pages" );
        }

        if ( this.hasSuggestProviders() )
        {
            argsFile.println( "--suggest-providers" );
            final String sb = this.getCommaSeparatedList( this.suggestProviders );
            argsFile.println( sb );
        }

        if ( this.hasLimitModules() )
        {
            argsFile.println( "--limit-modules" );
            final String sb = this.getCommaSeparatedList( this.limitModules );
            argsFile.println( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            argsFile.println( "--add-modules" );
            // This must be name of the module and *NOT* the name of the
            // file! Can we somehow pre check this information to fail early?
            final String sb = this.getCommaSeparatedList( modulesToAdd );
            argsFile.println( sb );
        }

        if ( this.pluginModulePath != null )
        {
            argsFile.println( "--plugin-module-path" );
            final StringBuilder sb = this.convertSeparatedModulePathToPlatformSeparatedModulePath( this.pluginModulePath );
            argsFile.println( sb );
        }

        if ( this.buildDirectory != null )
        {
            argsFile.println( "--output" );
            argsFile.println( this.outputDirectoryImage );
        }

        if ( this.verbose )
        {
            argsFile.println( "--verbose" );
        }
        argsFile.close();

        final Commandline cmd = new Commandline();
        cmd.createArg().setValue( '@' + file.getAbsolutePath() );

        return cmd;
    }

    private boolean hasSuggestProviders()
    {
        return this.suggestProviders != null && !this.suggestProviders.isEmpty();
    }



}
