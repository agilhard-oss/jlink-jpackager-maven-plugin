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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
/**
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 * @author Bernd Eilers
 */
public abstract class AbstractPackageToolMojo
    extends AbstractMojo
{

    protected static final String JMODS = "jmods";
    
    @Component
    protected LocationManager locationManager;
    
    @Component
    protected ToolchainManager toolchainManager;

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

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    @Parameter ( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    
    
    /**
     * Flag to ignore automatic modules. 
     * <p>
     * The jlink/jpackager command line equivalent is: <code>--verbose</code>
     * </p>
     */
    @Parameter( defaultValue = "false" )
    protected boolean ignoreAutomaticModules;

    
    /**
     * This will turn on verbose mode. 
     * <p>
     * The jlink/jpackager command line equivalent is: <code>--verbose</code>
     * </p>
     */
    @Parameter( defaultValue = "false" )
    protected boolean verbose;

    /**
     * <p>
     * Specify the requirements for this jdk toolchain. This overrules the toolchain selected by the
     * maven-toolchain-plugin.
     * </p>
     * <strong>note:</strong> requires at least Maven 3.3.1
     */
    @Parameter
    protected Map<String, String> jdkToolchain;


    /**
     * Include additional paths on the <code>--module-path</code> option. Project dedependencies and JDK modules are
     * automatically added.
     */
    @Parameter
    protected List<String> modulePaths;


    /**
     * Limit the universe of observable modules. The following gives an example of the configuration which can be used
     * in the <code>pom.xml</code> file.
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

    protected Collection<String> modulesToAdd = new ArrayList<>();
    protected Collection<String> pathsOfModules = new ArrayList<>();
    protected Collection<String> pathsOfArtifacts = new ArrayList<>();

    @Parameter( defaultValue = "${project.build.directory}", required = true, readonly = true )
    protected File buildDirectory;

    @Parameter( defaultValue = "${project.build.outputDirectory}", required = true, readonly = true )
    protected File outputDirectory;
    
    /**
     * <p>
     * Usually this is not necessary, cause this is handled automatically by the given dependencies.
     * </p>
     * <p>
     * By using the --add-modules you can define the root modules to be resolved. The configuration in
     * <code>pom.xml</code> file can look like this:
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
     * The command line equivalent for jlink is: <code>--add-modules &lt;mod&gt;[,&lt;mod&gt;...]</code>.
     * </p>
     */
    @Parameter
    protected List<String> addModules;

    protected String getToolExecutable( String toolName )
        throws IOException
    {
        Toolchain tc = getToolchain();

        String toolExecutable = null;
        if ( tc != null )
        {
            toolExecutable = tc.findTool( toolName );
        }

        // TODO: Check if there exist a more elegant way?
        String toolCommand = toolName + ( SystemUtils.IS_OS_WINDOWS ? ".exe" : "" );

        File toolExe;

        if ( StringUtils.isNotEmpty( toolExecutable ) )
        {
            toolExe = new File( toolExecutable );

            if ( toolExe.isDirectory() )
            {
               toolExe = new File( toolExe, toolCommand );
            }

            if ( SystemUtils.IS_OS_WINDOWS && toolExe.getName().indexOf( '.' ) < 0 )
            {
                toolExe = new File( toolExe.getPath() + ".exe" );
            }

            if ( !toolExe.isFile() )
            {
                throw new IOException( "The " + toolName + " executable '" + toolExe
                                     + "' doesn't exist or is not a file." );
            }
            return toolExe.getAbsolutePath();
        }

        // ----------------------------------------------------------------------
        // Try to find tool from System.getProperty( "java.home" )
        // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
        // should be in the JDK_HOME
        // ----------------------------------------------------------------------
        toolExe = new File( SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "bin", toolCommand );

        // ----------------------------------------------------------------------
        // Try to find javadocExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if ( !toolExe.exists() || !toolExe.isFile() )
        {
            Properties env = CommandLineUtils.getSystemEnvVars();
            String javaHome = env.getProperty( "JAVA_HOME" );
            if ( StringUtils.isEmpty( javaHome ) )
            {
                throw new IOException( "The environment variable JAVA_HOME is not correctly set." );
            }
            if ( !new File( javaHome ).getCanonicalFile().exists()
                || new File( javaHome ).getCanonicalFile().isFile() )
            {
                throw new IOException( "The environment variable JAVA_HOME=" + javaHome
                    + " doesn't exist or is not a valid directory." );
            }

            toolExe = new File( javaHome + File.separator + "bin", toolCommand );
        }

        if ( !toolExe.getCanonicalFile().exists() || !toolExe.getCanonicalFile().isFile() )
        {
            throw new IOException( "The " + toolName + " executable '" + toolExe
                + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable." );
        }

        return toolExe.getAbsolutePath();
    }

    protected void executeCommand( Commandline cmd, File outputDirectory )
        throws MojoExecutionException
    {
        if ( getLog().isDebugEnabled() )
        {
            // no quoted arguments ???
            getLog().debug( CommandLineUtils.toString( cmd.getCommandline() ).replaceAll( "'", "" ) );
        }

        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
        try
        {
            int exitCode = CommandLineUtils.executeCommandLine( cmd, out, err );

            String output = ( StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim() );

            if ( exitCode != 0 )
            {

                if ( StringUtils.isNotEmpty( output ) )
                {
                    // Reconsider to use WARN / ERROR ?
                   //  getLog().error( output );
                    for ( String outputLine : output.split( "\n" ) )
                    {
                        getLog().error( outputLine );
                    }
                }

                StringBuilder msg = new StringBuilder( "\nExit code: " );
                msg.append( exitCode );
                if ( StringUtils.isNotEmpty( err.getOutput() ) )
                {
                    msg.append( " - " ).append( err.getOutput() );
                }
                msg.append( '\n' );
                msg.append( "Command line was: " ).append( cmd ).append( '\n' ).append( '\n' );

                throw new MojoExecutionException( msg.toString() );
            }

            if ( StringUtils.isNotEmpty( output ) )
            {
                //getLog().info( output );
                for ( String outputLine : output.split( "\n" ) )
                {
                    getLog().info( outputLine );
                }
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Unable to execute command: " + e.getMessage(), e );
        }

    }

    protected Toolchain getToolchain()
    {
        Toolchain tc = null;

        if ( jdkToolchain != null )
        {
            // Maven 3.3.1 has plugin execution scoped Toolchain Support
            try
            {
                Method getToolchainsMethod = toolchainManager.getClass().getMethod( "getToolchains", MavenSession.class,
                                                                                    String.class, Map.class );

                @SuppressWarnings( "unchecked" )
                List<Toolchain> tcs =
                    (List<Toolchain>) getToolchainsMethod.invoke( toolchainManager, session, "jdk", jdkToolchain );

                if ( tcs != null && tcs.size() > 0 )
                {
                    tc = tcs.get( 0 );
                }
            }
            catch ( ReflectiveOperationException e )
            {
                // ignore
            }
            catch ( SecurityException e )
            {
                // ignore
            }
            catch ( IllegalArgumentException e )
            {
                // ignore
            }
        }

        if ( tc == null )
        {
            // TODO: Check if we should make the type configurable?
            tc = toolchainManager.getToolchainFromBuildContext( "jdk", session );
        }

        return tc;
    }

    protected MavenProject getProject()
    {
        return project;
    }

    protected MavenSession getSession()
    {
        return session;
    }

    /**
     * Returns the archive file to generate, based on an optional classifier.
     *
     * @param basedir the output directory
     * @param finalName the name of the ear file
     * @param classifier an optional classifier
     * @param archiveExt The extension of the file.
     * @return the file to generate
     */
    protected File getArchiveFile( File basedir, String finalName, String classifier, String archiveExt )
    {
        if ( basedir == null )
        {
            throw new IllegalArgumentException( "basedir is not allowed to be null" );
        }
        if ( finalName == null )
        {
            throw new IllegalArgumentException( "finalName is not allowed to be null" );
        }
        if ( archiveExt == null )
        {
            throw new IllegalArgumentException( "archiveExt is not allowed to be null" );
        }

        if ( finalName.isEmpty() )
        {
            throw new IllegalArgumentException( "finalName is not allowed to be empty." );
        }
        if ( archiveExt.isEmpty() )
        {
            throw new IllegalArgumentException( "archiveExt is not allowed to be empty." );
        }

        StringBuilder fileName = new StringBuilder( finalName );

        if ( hasClassifier( classifier ) )
        {
            fileName.append( "-" ).append( classifier );
        }

        fileName.append( '.' );
        fileName.append( archiveExt );

        return new File( basedir, fileName.toString() );
    }

    protected boolean hasClassifier( String classifier )
    {
        boolean result = false;
        if ( classifier != null && classifier.trim().length() > 0 )
        {
            result = true;
        }

        return result;
    }

    /**
     * This will convert a module path separated by either {@code :} or {@code ;} into a string which uses the platform
     * depend path separator uniformly.
     * 
     * @param pluginModulePath The module path.
     * @return The platform separated module path.
     */
    protected StringBuilder convertSeparatedModulePathToPlatformSeparatedModulePath( String pluginModulePath )
    {
        StringBuilder sb = new StringBuilder();
        // Split the module path by either ":" or ";" linux/windows path separator and
        // convert uniformly to the platform used separator.
        String[] splitModule = pluginModulePath.split( "[;:]" );
        for ( String module : splitModule )
        {
            if ( sb.length() > 0 )
            {
                sb.append( File.pathSeparatorChar );
            }
            sb.append( module );
        }
        return sb;
    }

    /**
     * Convert a list into a string which is separated by platform depend path separator.
     * 
     * @param modulePaths The list of elements.
     * @return The string which contains the elements separated by {@link File#pathSeparatorChar}.
     */
    protected String getPlatformDependSeparateList( Collection<String> modulePaths )
    {
        StringBuilder sb = new StringBuilder();
        for ( String module : modulePaths )
        {
            if ( sb.length() > 0 )
            {
                sb.append( File.pathSeparatorChar );
            }
            sb.append( module );
        }
        return sb.toString();
    }

    /**
     * Convert a list into a 
     * @param modules The list of modules.
     * @return The string with the module list which is separated by {@code ,}.
     */
    protected String getCommaSeparatedList( Collection<String> modules )
    {
        StringBuilder sb = new StringBuilder();
        for ( String module : modules )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( module );
        }
        return sb.toString();
    }


    /**
     * Convert a list into a 
     * @param modules The list of modules.
     * @return The string with the module list which is separated by {@code ,}.
     */
    protected String getColonSeparatedList( Collection<String> modules )
    {
        StringBuilder sb = new StringBuilder();
        for ( String module : modules )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ':' );
            }
            sb.append( module );
        }
        return sb.toString();
    }

    
    private List<File> getCompileClasspathElements( MavenProject project )
    {
        List<File> list = new ArrayList<File>( project.getArtifacts().size() + 1 );

        for ( Artifact a : project.getArtifacts() )
        {
            getLog().debug( "Artifact: " + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() );
            list.add( a.getFile() );
        }
        return list;
    }


    private Map<String, File> getModulePathElements()
        throws MojoFailureException
    {
        // For now only allow named modules. Once we can create a graph with ASM we can specify exactly the modules
        // and we can detect if auto modules are used. In that case, MavenProject.setFile() should not be used, so
        // you cannot depend on this project and so it won't be distributed.

        Map<String, File> modulepathElements = new HashMap<>();

        try
        {
            Collection<File> dependencyArtifacts = getCompileClasspathElements( getProject() );

            ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles( dependencyArtifacts );

            Toolchain toolchain = getToolchain();
            if ( toolchain != null && toolchain instanceof DefaultJavaToolChain )
            {
                request.setJdkHome( new File( ( (DefaultJavaToolChain) toolchain ).getJavaHome() ) );
            }

            ResolvePathsResult<File> resolvePathsResult = locationManager.resolvePaths( request );

            for ( Map.Entry<File, JavaModuleDescriptor> entry : resolvePathsResult.getPathElements().entrySet() )
            {
                if ( entry.getValue() == null )
                {
                    String message = "The given dependency " + entry.getKey()
                        + " does not have a module-info.java file. So it can't be linked.";
                    getLog().error( message );
                    throw new MojoFailureException( message );
                }

                // Don't warn for automatic modules, let the jlink tool do that
                getLog().debug( " module: " + entry.getValue().name() + " automatic: "
                    + entry.getValue().isAutomatic() );
                if ( modulepathElements.containsKey( entry.getValue().name() ) )
                {
                    getLog().warn( "The module name " + entry.getValue().name() + " does already exists." );
                }
                
                if ( ignoreAutomaticModules )
                {
                    // just do not add automatic modules
                    if ( ! entry.getValue().isAutomatic() )
                    {
                        modulepathElements.put( entry.getValue().name(), entry.getKey() );
                    }
                }
                else
                {
                    modulepathElements.put( entry.getValue().name(), entry.getKey() );
                }
            }

            // This part is for the module in target/classes ? (Hacky..)
            // FIXME: Is there a better way to identify that code exists?            
            final AtomicBoolean b = new AtomicBoolean();
            
            if ( outputDirectory.exists() ) 
            {
                BiPredicate<Path, BasicFileAttributes> predicate =
                ( path, attrs ) -> 
                {
                        return path.toString().endsWith( ".class" );
                };

                try ( Stream<Path> stream =
                        Files.find( Paths.get( outputDirectory.toURI() ),
                                    Integer.MAX_VALUE, predicate ) )
                {
                    stream.forEach( name -> 
                    {
                        b.set( true );
                    } );
                } catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
            if ( b.get() )
            {
                List<File> singletonList = Collections.singletonList( outputDirectory );

                ResolvePathsRequest<File> singleModuls = ResolvePathsRequest.ofFiles( singletonList );

                ResolvePathsResult<File> resolvePaths = locationManager.resolvePaths( singleModuls );
                for ( Entry<File, JavaModuleDescriptor> entry : resolvePaths.getPathElements().entrySet() )
                {
                    if ( entry.getValue() == null )
                    {
                        String message = "The given project " + entry.getKey()
                            + " does not contain a module-info.java file. So it can't be linked.";
                        getLog().error( message );
                        throw new MojoFailureException( message );
                    }
                    if ( modulepathElements.containsKey( entry.getValue().name() ) )
                    {
                        getLog().warn( "The module name " + entry.getValue().name() + " does already exists." );
                    }
                    modulepathElements.put( entry.getValue().name(), entry.getKey() );
                }
            }

        }   
        catch ( IOException e )
        {
            getLog().error( e.getMessage() );
            throw new MojoFailureException( e.getMessage() );
        }

        return modulepathElements;
    }


    protected void prepareModules( File jmodsFolder ) throws MojoFailureException
    {
        prepareModules( jmodsFolder, false, false, null );
    }

    protected void prepareModules( File jmodsFolder, boolean useDirectory,
            boolean copyArtifacts, File moduleTempDirectory ) throws MojoFailureException
    {

        if ( addModules != null )
        {
            modulesToAdd.addAll( addModules );
        }

        if ( modulePaths != null )
        {
            pathsOfModules.addAll( modulePaths );
        }

        if ( copyArtifacts )
        {
            if ( moduleTempDirectory != null )
            {
                pathsOfModules.add( moduleTempDirectory.getAbsolutePath() ); 
            }
        }
        for ( Entry<String, File> item : getModulePathElements().entrySet() )
        {
            getLog().info( " -> module: " + item.getKey() + " ( " + item.getValue().getPath() + " )" );

            // We use the real module name and not the artifact Id...
             modulesToAdd.add( item.getKey() );
             if ( copyArtifacts )
             {
                 pathsOfArtifacts.add( item.getValue().getPath() );
             }
             else
             {
                 if ( useDirectory )
                 {
                    pathsOfModules.add( item.getValue().getParentFile().getPath() );
                }
                else
                {
                    pathsOfModules.add( item.getValue().getPath() );
                }
            }
        }
        if ( jmodsFolder != null )
        {
            // The jmods directory of the JDK
            pathsOfModules.add( jmodsFolder.getAbsolutePath() );
        }
    }

    protected File createZipArchiveFromImage( File outputDirectory, File outputDirectoryImage )
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

    protected void failIfProjectHasAlreadySetAnArtifact() throws MojoExecutionException
    {
        if ( projectHasAlreadySetAnArtifact() )
        {
            throw new MojoExecutionException( "You have to use a classifier "
                + "to attach supplemental artifacts to the project instead of replacing them." );
        }
    }

    protected boolean projectHasAlreadySetAnArtifact()
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

    protected boolean hasLimitModules()
    {
        return limitModules != null && !limitModules.isEmpty();
    }
}
