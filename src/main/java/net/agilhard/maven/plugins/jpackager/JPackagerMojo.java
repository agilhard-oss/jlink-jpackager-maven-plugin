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
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

import net.agilhard.maven.plugins.jlink.AbstractPackageToolMojo;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** 
 * The JPackager goal is intended to create a native installer package file based on
 * <a href="http://openjdk.java.net/jeps/343">http://openjdk.java.net/jeps/343</a>.
 * 
 * @author Bernd Eilers
 */
// CHECKSTYLE_OFF: LineLength
@Mojo( name = "jpackager", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true )
// CHECKSTYLE_ON: LineLength
public class JPackagerMojo extends AbstractPackageToolMojo 
{

    /**
     * The MavenProjectHelper
     */
    @Component 
    protected MavenProjectHelper mavenProjectHelper;
    
    /**
     * Mode of JPackager operation. 
     * One of <code>create-image</code>, <code>create-installer</code>, <code>create-jre-installer</code>.
     */
    @Parameter( defaultValue = "create-installer", required = true, readonly = false )
    private String mode;

    /**
     * Installer type of JPackager operation. 
     * <p>
     *  Valid values for &lt;type&gt; are &quot;msi&quot;, &quot;rpm&quot;, &quot;deb&quot;,
     *  &quot;dmg&quot;, &quot;pkg&quot;, &quot;pkg-app-store&quot;.
     * </p><p>
     *  If &lt;type&gt; is omitted a value from the platform specific settings
     *  &lt;linuxType&gt, &lt;windowsType&gt; or &lt;macType&gt; is being used.
     *  </p>
     */
    @Parameter( required = false, readonly = false )
    private String type;

    /**
     * The output directory for the resulting Application Image or Package.
     *
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-out", required = true, readonly = true )
    private File outputDirectoryPackage;

    /**
     * The output directory for the resulting Application Image or Package.
     * 
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-in", required = true, readonly = true )
    private File inputDirectoryPackage;

    /**
     * Directory in which to use and place temporary files.
     * 
     * <p>
     * <code>--build-root &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-build", required = true, readonly = false )
    private File buildRootPackage;

    
    /**
     * TempDirectory where artifact modules are temporarily copied too.
     */
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-jmods", required = true, readonly = false )
    private File moduleTempDirectory;
    
    
    /**
     * Flag whether to copy artifact modules to the moduleTempDirectory.
     * 
     * <p>
     * The default value is true. Setting this to false only works if there are no modules whith classes
     * in the module hierachy.
     * </p>
     * 
     */
    @Parameter( defaultValue = "true", required = true, readonly = false )
    private boolean copyArtifacts;
    
    /**
     * List of files in the base directory. If omitted, all files from "input"
     * directory (which is a mandatory argument in this case) will be packaged.
     * 
     * <p>
     * <code>--files &lt;files&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private List<String> files;

    /**
     * Name of the application. 
     * 
     * <p>
     * <code>--name &lt;name&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.name}", required = false, readonly = false )
    private String name;

    /**
     * The main JAR of the application. This JAR should have the main-class, and is
     * relative to the assembled application directory.
     * 
     * <p>
     * <code>--main-jar&lt;jarname&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String mainJar;

    /**
     * Qualified name of the application class to execute.
     * 
     * <p>
     * <code>-class &lt;className&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String mainClass;

    /**
     * Version of the application.
     *
     * <p>
     * Note a -SNAPSHOT or .SNAPSHOT is automatically deleted from the
     * version when constructing the jpackage command line arguments.
     * </p>
     * <p>
     * <code>--app-version &lt;version&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.version}", required = false, readonly = false )
    private String appVersion;

    /**
     * Command line arguments to pass to the main class if no arguments
     * are specified by the launcher.
     * 
     * <p>
     * <code>--arguments &lt;args&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private List<String> arguments;

    /**
     * Icon of the application bundle.
     * 
     * <p>
     * <code>--icon &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private File icon;

    /**
     * Prevents multiple instances of the application from launching
     * (see SingleInstanceService API for more details).
     * 
     * <p>
     * <code>--singleton</code>
     * </p>
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
     * <p>
     * <code>--identifier &lt;identifier&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.groupId}.${project.artifactId}", required = false, readonly = false )
    private String identifier;
    
    /**
     * Removes native executables from the custom run-time images.
     * 
     * <p>
     * <code>--strip-native-commands</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private boolean stripNativeCommands;
    
    /**
     * JVM flags and options to pass to the application.
     * 
     * <p>
     * <code>--jvm-args &lt;args&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private List<String> jvmArgs;
    
    /**
     * JVM options the user may override along and their default values
     * (see UserJvmOptionsService API for more details).
     * 
     * <p>
     * <code>--user-jvm-args &lt;args&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private List<String> userJvmArgs;
    
    /**
     * Properties file that contains list of key=value parameters that
     * describe a file association. &quot;extension&quot;, &quot;mime-type&quot;, &quot;icon&quot;,
     * &quot;description&quot; can be used as keys for the association.
     * 
     * <p>
     * <code>--file-associations &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private File fileAssociations;
    
    /**
     * Properties file that contains a collection of options for a secondary launcher.
     * 
     * <p>
     * <code>--secondary-launcher &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private File secondaryLauncher;
    
    /**
     * Location of the predefined runtime image that is used to build
     * an application image and installable package.
     * 
     * <p>
     * <code>--runtime-image &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private File runtimeImage;

    /**
     * Location of the predefined application image that is used to build
     * an installable package.
     * 
     * <p>
     * <code>--app-image &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private File appImage;

    /**
     * Qualified name of the application class to execute.
     * 
     * <p>
     * <code>-install-dir &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String installDir;
    
    /**
     * The license file, relative to the base directory.
     *
     * <p>
     * <code>--license-file &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String licenseFile;

    
    /**
     * Copyright for the application.
     *
     * <p>
     * <code>--copyright &lt;text&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String copyright;

    /**
     * Description of the application.
     * 
     * <p>
     * <code>--description &lt;text&gt;</code>
     * </p>
     */
    @Parameter(  defaultValue = "${project.description}", required = false, readonly = false )
    private String description;

    /**
     * Category or group of the application
     *
     * <p>
     * <code>--category &lt;text&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String category;

    /**
     * Vendor of the application.
     *
     * <p>
     * <code>--vendor &lt;text&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.organization}", required = false, readonly = false )
    private String vendor;

    
    /**
     * Main module of the application. This module must have the main-class,
     * and be on the module path.
     *
     * <p>
     * <code>--module &lt;name&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    private String module;
    
    /**
     * Linux Options.
     * 
     * <p>
     * Available subelements of &lt;LinuxOptions&gt; are:
     * bundleName, packageDeps, rpmLicenseType, debMaintainer and linuxType.
     * </p>
     */
    @Parameter( required = false, readonly = false )
    JPackagerLinuxOptions linuxOptions;
    
    /**
     * Windows Options
     * <p>
     * Available subelements of &lt;WindowsOptions&gt; are:
     * menu, menuGroup, perUserInstall, dirChooser, registryName, upgradeUUID,
     * shortcut, console and windowsType.
     * </p>
     */
    @Parameter( required = false, readonly = false )
    JPackagerWindowsOptions windowsOptions;
    
    /**
     * Mac Options
	 *
     * <p>
     * Available subelements of &lt;MacOptions&gt; are:
     * sign, bundleName, bundleIdentifier, appStoreCategory,
     * appStoreEntitlements, bundleSigningPrefix, 
     * signingKeyUserName, signingKeychain and macType.
     * </p>
     */
    @Parameter( required = false, readonly = false )
    JPackagerMacOptions macOptions;
    
 // CHECKSTYLE_OFF: LineLength
    /**
     * Flag to indicate we are use JDK11 ported jpackager
     * from the posting 
     * <a href="http://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html">Filling the Packager gap</a>
     */
 // CHECKSTYLE_ON: LineLength
    private boolean usingJDK11Jpackager;
    
    protected String getJPackageExecutable()
        throws IOException

    {
        return getToolExecutable( "jpackage" );
    }

    protected String getJPackagerExecutable()
            throws IOException

    {
        return getToolExecutable( "jpackager" );
    }
    
    
    
    public boolean isUsingJDK11Jpackager()
    {
        return usingJDK11Jpackager;
    }

    public void execute() throws MojoExecutionException, MojoFailureException 
    {

        String jPackagerExec = getExecutable();

        getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jpackager [ " + jPackagerExec + " ]" );

        // TODO: Find a more better and cleaner way?
        File jPackagerExecuteable = new File( jPackagerExec );

        // Really Hacky...do we have a better solution to find the jmods directory of the JDK?
        File jPackagerParent = jPackagerExecuteable.getParentFile().getParentFile();
        File jmodsFolder = new File( jPackagerParent, JMODS );

        maySetPlatformDefaultType();
        
        failIfParametersAreNotValid();
        
        ifBuildRootDirectoryDoesNotExistcreateIt();
                
        ifOutputDirectoryExistsDeleteIt();
        
        prepareModules( jmodsFolder, true, copyArtifacts, moduleTempDirectory );

        if ( copyArtifacts )
        {
            ifModuleTempDirectoryDoesNotExistCreateIt();
            copyArtifactsToModuleTempDirectory();
        }

        Commandline cmd;
        try
        {
            if ( isUsingJDK11Jpackager() )
            {
                cmd = createJPackagerCommandLine( pathsOfModules, modulesToAdd );
            }
            else
            {
                cmd = createJPackageCommandLine( pathsOfModules, modulesToAdd );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        cmd.setExecutable( jPackagerExec );

        executeCommand( cmd, outputDirectoryPackage );

        if ( "create-image".equals( mode ) ) 
        {
            File createZipArchiveFromPackage = createZipArchiveFromImage( buildDirectory, outputDirectoryPackage );

            failIfProjectHasAlreadySetAnArtifact();

            getProject().getArtifact().setFile( createZipArchiveFromPackage );
        }
        
        publishClassifierArtifacts();

    }

    private void maySetPlatformDefaultType()
    {
        if ( ( ( type == null ) || ( "".equals( type ) ) )
	    && ( ! "create-image".equals(mode)) )
        {
            if ( SystemUtils.IS_OS_LINUX && ( linuxOptions != null ) ) {
            	type = linuxOptions.linuxType;
            }
            else if ( SystemUtils.IS_OS_WINDOWS ) {
            	type = ( ( windowsOptions == null )  || ( windowsOptions.windowsType == null ) ) ? "exe" : windowsOptions.windowsType;
            }
            else if ( SystemUtils.IS_OS_MAC  ) {
            	type = ( ( macOptions == null ) || ( macOptions.macType == null ) ) ? "dmg" : macOptions.macType;
            }
	    getLog().info("<type> is not set using platform default (" + ( type == null ? "" : type ) + ")" );
        }
    }
    
    private void publishClassifierArtifacts()
    {
        String[] extensions = {
                "msi", "exe", "rpm", "deb", "dmg",
                "pkg", "pkg-app-store" 
                };

        for ( String extension : extensions ) 
        {
            File artifactFile = findPackageFile( extension );
            if ( artifactFile != null )
            {
                mavenProjectHelper.attachArtifact( project, extension, extension, artifactFile );
            }
        }
        
    }
    

    private File findPackageFile( final String extension ) 
    {
        final class FindPackageResult
        {
            private File file;

            public File getFile()
            {
                return file;
            }

            public void setFile( File file )
            {
                this.file = file;
            }
        }
        final FindPackageResult result = new FindPackageResult();
        
        BiPredicate<Path, BasicFileAttributes> predicate =
                ( path, attrs ) -> 
                {
                        return path.toString().endsWith( extension );
                };

                try ( Stream<Path> stream =
                        Files.find( Paths.get( outputDirectoryPackage.toURI() ),
                                    1, predicate ) )
                {
                    stream.forEach( name -> 
                    {
                        if ( result.getFile() != null )
                        {
                            getLog().info( "findPackageFile name=" + name );
                        }
                        result.setFile( name.toFile() );
                    } );
                } catch ( IOException e )
                {
                    e.printStackTrace();
                }
        
        return result.getFile();
    }
    
   
    private void copyArtifactsToModuleTempDirectory() throws MojoExecutionException
    {
        if ( pathsOfArtifacts != null )
        {
            for ( String path : pathsOfArtifacts ) 
            {
               Path file = new File( path ).toPath();
               if ( Files.isRegularFile( file ) )
               {
                   getLog().info( "copy module " + path );
                   try 
                   {
                       Path target = moduleTempDirectory.toPath().resolve( file.getFileName() );
                       Files.copy( file, target, REPLACE_EXISTING );
                   }
                   catch ( IOException e )
                   {
                       getLog().error( "IOException", e );
                       throw new MojoExecutionException(
                            "Failure during copying of " + path + " occured." );
                   }
               }
           }
        }
    }
    
    private String getExecutable() throws MojoFailureException 
    {
        String jPackagerExec;
        try 
        {
            jPackagerExec = getJPackageExecutable();
        }
        catch ( IOException e )
        {
            try 
            {
                jPackagerExec = getJPackagerExecutable();
                usingJDK11Jpackager = true;
            }
            catch ( IOException e2 )
            {
                throw new MojoFailureException( "Unable to find jpackage or jpackager command: " + e2.getMessage(), e );
            }
        }
        return jPackagerExec;
    }

    
    private void ifBuildRootDirectoryDoesNotExistcreateIt() throws MojoExecutionException 
    {
        if ( ! buildRootPackage.exists() )
        {
            try
            {
                getLog().debug( "Create directory " + buildRootPackage.getAbsolutePath() );
                buildRootPackage.mkdirs();
            }
            catch ( Exception e )
            {
                getLog().error( "Exception", e );
                throw new MojoExecutionException(
                        "FargsFileailure during creation of " + buildRootPackage.getAbsolutePath() + " occured." );
            }
        }
    }
    
    private void ifModuleTempDirectoryDoesNotExistCreateIt() throws MojoExecutionException 
    {
        if ( ! moduleTempDirectory.exists() )
        {
            try
            {
                getLog().debug( "Create directory " + moduleTempDirectory.getAbsolutePath() );
                moduleTempDirectory.mkdirs();
            }
            catch ( Exception e )
            {
                getLog().error( "Exception", e );
                throw new MojoExecutionException(
                        "Failure during creation of " + moduleTempDirectory.getAbsolutePath() + " occured." );
            }
        }
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

    /**
     * Build Commandline for JDK >= 12 jpackage command
     * 
     * @param pathsOfModules
     * @param modulesToAdd
     * @return
     * @throws IOException
     */
    private Commandline createJPackageCommandLine( Collection<String> pathsOfModules, Collection<String> modulesToAdd )
        throws IOException
    {

        File file = new File( outputDirectoryPackage.getParentFile(), "jpackageArgs" );
        
        if ( !getLog().isDebugEnabled() )
        {
            file.deleteOnExit();
        }
        
        file.getParentFile().mkdirs();
        file.createNewFile();

        PrintStream argsFile = new PrintStream( file );

        if ( mode != null )
        {
            argsFile.println( mode );
        }
        
        if ( type != null )
        {
            argsFile.println( type );
        }
        
        if ( verbose )
        {
            argsFile.println( "--verbose" );
        }
        
        if ( buildDirectory != null )
        {
            argsFile.println( "--output" );
            String s = outputDirectoryPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }        

            if ( inputDirectoryPackage.exists() )
            {
                argsFile.println( "--input" );
                s = inputDirectoryPackage.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( s );
                }        

            }

            argsFile.println( "--build-root" );
            s = buildRootPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }        

        }
        
        
        if ( ! ( ( files == null ) || files.isEmpty() ) )
        {
            argsFile.println( "--files" );
            String sb = getColonSeparatedList( files );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            argsFile.println( sb2.toString() ); 
        }
       
        if ( name != null ) 
        {
            argsFile.println( "--name" );
            if ( name.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( name ).println( "\"" );
            }
            else
            {
                argsFile.println( name );
            }        
        }
        
        if ( appVersion != null ) 
        {
            argsFile.println( "--app-version" );
            argsFile.println(  appVersion.replaceAll( "-SNAPSHOT", "" ).replaceAll( ".SNAPSHOT", "" ) );
        }
        
        if ( pathsOfModules != null )
        {
            argsFile.println( "--module-path" );
            String s = getPlatformDependSeparateList( pathsOfModules );
            if ( s.indexOf( " " ) > -1 )
            {
                argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }        
            
        }
        
        
        if ( mainClass != null ) 
        {
            argsFile.println( "--class" );
            argsFile.println(  mainClass );
        }
        
        if ( mainJar != null ) 
        {
            argsFile.println( "--main-jar" );
            if ( mainJar.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( mainJar.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( mainJar );
            }
        }

        if ( module != null ) 
        {
            argsFile.println( "--module" );
            argsFile.println(  module );
        }

        if ( ! ( ( arguments == null ) || arguments.isEmpty() ) )
        {
            for ( String arg : arguments )
            {
                argsFile.println( "--arguments" );
                if ( arg.indexOf( " " ) > -1 )
                {
                    argsFile.append( "\"" ).append( arg.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( arg );
                }
            }
        }
        
        if ( icon != null ) 
        {
            argsFile.println( "--icon" );
            String s = icon.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }        
        }
        
        if ( singleton )
        {
            argsFile.println( "--singleton" );
        }
        
        
        if ( identifier != null ) 
        {
            argsFile.println( "--identifier" );
            if ( identifier.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( identifier.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( identifier );
            }         
        }
        
        if ( stripNativeCommands )
        {
            argsFile.println( "--strip-native-commands" );
        }

        if ( ! ( ( jvmArgs == null ) || jvmArgs.isEmpty() ) )
        {
            argsFile.println( "--jvmArgs" );
            String sb = getColonSeparatedList( jvmArgs );
            argsFile.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).println( '"' );
        }
        
        if ( ! ( ( userJvmArgs == null ) || userJvmArgs.isEmpty() ) )
        {
            argsFile.println( "--user-jvm-args" );
            String sb = getColonSeparatedList( userJvmArgs );
            argsFile.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).println( '"' ); 
        }
        
        if ( fileAssociations != null ) 
        {
            argsFile.println( "--file-associations" );
            String s = fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            } 
        }
        
        if ( secondaryLauncher != null ) 
        {
            argsFile.println( "--file-associations" );
            String s = fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            } 
        }
        
        if ( runtimeImage != null ) 
        {
            argsFile.println( "--runtime-image" );
            String s = runtimeImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            } 
        }
        
        if ( appImage != null ) 
        {
            argsFile.println( "--app-image" );
            String s = appImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            } 
        }
        
        if ( installDir != null ) 
        {
            argsFile.println( "--install-dir" );
            if ( installDir.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( installDir.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( installDir );
            } 
        }
        
        if ( licenseFile != null ) 
        {
            argsFile.println( "--license-file" );
            if ( licenseFile.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( licenseFile.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( licenseFile );
            }        
        }
        
        if ( copyright != null ) 
        {
            argsFile.println( "--copyright" );
            if ( copyright.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( copyright.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( copyright );
            }       
        }
        
        if ( description != null ) 
        {
            argsFile.println( "--description" );
            if ( description.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( description.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( description );
            }
        }
        
        if ( category != null ) 
        {
            argsFile.println( "--category" );
            if ( category.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( category.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( category );
            }        
        }
       
        if ( vendor != null ) 
        {
            argsFile.println( "--vendor" );
            if ( vendor.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( vendor.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( vendor );
            }
         }
        
        if ( hasLimitModules() )
        {
            argsFile.println( "--limit-modules" );
            String sb = getColonSeparatedList( limitModules );
            argsFile.println( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            argsFile.println( "--add-modules" );
            argsFile.println( getColonSeparatedList( modulesToAdd ) );
        }
       
        if ( SystemUtils.IS_OS_LINUX && ( linuxOptions != null ) )
        {
            if ( linuxOptions.bundleName != null )
            {
                argsFile.println( "--linux-bundle-name" );
                if ( linuxOptions.bundleName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( linuxOptions.bundleName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( linuxOptions.bundleName );
                }            
            }
            if ( linuxOptions.packageDeps != null )
            {
                argsFile.println( "--linux-package-deps" );
                if ( linuxOptions.packageDeps.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( linuxOptions.packageDeps.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( linuxOptions.packageDeps );
                }      
            }
            if ( linuxOptions.rpmLicenseType != null )
            {
                argsFile.println( "--linux-rpm-license-type" );
                if ( linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( linuxOptions.rpmLicenseType.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( linuxOptions.rpmLicenseType );
                }             
            }
            if ( linuxOptions.debMaintainer != null )
            {
                argsFile.println( "--linux-deb-maintainer" );
                if ( linuxOptions.debMaintainer.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( linuxOptions.debMaintainer.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( linuxOptions.debMaintainer );
                }
            }
        }
        
        if ( SystemUtils.IS_OS_WINDOWS && ( windowsOptions != null ) )
        {
            if ( windowsOptions.menu ) 
            {
                argsFile.println( "--win-menu" );
            }
            if ( windowsOptions.menuGroup != null )
            {
                argsFile.println( "--win-menu-group" );
                argsFile.println( windowsOptions.menuGroup );
            }
            if ( windowsOptions.perUserInstall ) 
            {
                argsFile.println( "--win-per-user-install" );
            }
            if ( windowsOptions.dirChooser ) 
            {
                argsFile.println( "--win-dir-chooser" );
            }
            if ( windowsOptions.registryName != null )
            {
                argsFile.println( "--win-registry-name" );
                if ( windowsOptions.registryName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( windowsOptions.registryName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( windowsOptions.registryName );
                }
            }
            if ( windowsOptions.shortcut ) 
            {
                argsFile.println( "--win-shortcut" );
            }
            if ( windowsOptions.console ) 
            {
                argsFile.println( "--win-console" );
            }
            
        }
        
        if ( SystemUtils.IS_OS_MAC && ( macOptions != null ) )
        {
            if ( macOptions.sign ) 
            {
                argsFile.println( "--mac-sign" );
            }
            if ( macOptions.bundleName != null )
            {
                argsFile.println( "--mac-bundle-name" );
                if ( macOptions.bundleName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( macOptions.bundleName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( macOptions.bundleName );
                }
            }
            if ( macOptions.bundleIdentifier != null )
            {
                argsFile.println( "--mac-bundle-identifier" );
                if ( macOptions.bundleIdentifier.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( macOptions.bundleIdentifier.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( macOptions.bundleIdentifier );
                }
            }
            if ( macOptions.appStoreCategory != null )
            {
                argsFile.println( "--mac-app-store-category" );
                if ( macOptions.appStoreCategory.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( macOptions.appStoreCategory.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( macOptions.appStoreCategory );
                }
            }
            if ( macOptions.appStoreEntitlements != null )
            {
                argsFile.println( "--mac-app-store-entitlements" );
                String s = macOptions.appStoreEntitlements.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( s );
                }
            }
            if ( macOptions.bundleSigningPrefix != null )
            {
                argsFile.println( "--mac-bundle-signing-prefix" );
                if ( macOptions.bundleSigningPrefix.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" )
                  .append( macOptions.bundleSigningPrefix
                          .replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( macOptions.bundleSigningPrefix );
                }
            }
            if ( macOptions.signingKeyUserName != null )
            {
                argsFile.println( "--mac-signing-key-username" );
                if ( macOptions.signingKeyUserName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" )
                  .append( macOptions.signingKeyUserName
                          .replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( macOptions.signingKeyUserName );
                }
            }
            if ( macOptions.signingKeychain != null )
            {
                argsFile.println( "--mac-signing-keychain" );
                String s = macOptions.signingKeychain.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( s );
                }
            }
        }
        
        argsFile.println( "--force" );
        argsFile.close();

        Commandline cmd = new Commandline();
        cmd.createArg().setValue( '@' + file.getAbsolutePath() );

        return cmd;
    }


    /**
     * Build Commandline for JDK11 backported jpackager command
     * 
     * @param pathsOfModules
     * @param modulesToAdd
     * @return
     * @throws IOException
     */
    private Commandline createJPackagerCommandLine( Collection<String> pathsOfModules, Collection<String> modulesToAdd )
        throws IOException
    {

        Commandline cmd = new Commandline();

        if ( mode != null )
        {
            cmd.createArg().setValue( mode );
        }
        
        if ( type != null )
        {
            cmd.createArg().setValue( type );
        }
        
        if ( verbose )
        {
            cmd.createArg().setValue( "--verbose" );
        }
        
        if ( buildDirectory != null )
        {
            cmd.createArg().setValue( "--output" );
            String s = outputDirectoryPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              StringBuilder sb = new StringBuilder();
              sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
              cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }        

            if ( inputDirectoryPackage.exists() )
            {
                cmd.createArg().setValue( "--input" );
                s = inputDirectoryPackage.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );                
                }
                else
                {
                    cmd.createArg().setValue( s );
                }        

            }

            cmd.createArg().setValue( "--build-root" );
            s = buildRootPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );            
            }
            else
            {
                cmd.createArg().setValue( s );
            }        

        }
        
        
        if ( ! ( ( files == null ) || files.isEmpty() ) )
        {
            cmd.createArg().setValue( "--files" );
            String sb = getColonSeparatedList( files );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb2.toString() ); 
        }
       
        if ( name != null ) 
        {
            cmd.createArg().setValue( "--name" );
            if ( name.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( name.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );            
            }
            else
            {
                cmd.createArg().setValue( name );
            }        
        }
        
        if ( appVersion != null ) 
        {
            cmd.createArg().setValue( "--version" );
            cmd.createArg().setValue(  appVersion.replaceAll( "-SNAPSHOT", "" ).replaceAll( ".SNAPSHOT", "" ) );
        }
        
        if ( pathsOfModules != null )
        {
            cmd.createArg().setValue( "--module-path" );
            String s = getPlatformDependSeparateList( pathsOfModules );
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }        
            
        }
        
        
        if ( mainClass != null ) 
        {
            cmd.createArg().setValue( "--class" );
            cmd.createArg().setValue(  mainClass );
        }
        
        if ( mainJar != null ) 
        {
            cmd.createArg().setValue( "--main-jar" );
            if ( mainJar.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( mainJar.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );            
            }
            else
            {
                cmd.createArg().setValue( mainJar );
            }
        }

        if ( module != null ) 
        {
            cmd.createArg().setValue( "--module" );
            cmd.createArg().setValue(  module );
        }

        if ( ! ( ( arguments == null ) || arguments.isEmpty() ) )
        {
            for ( String arg : arguments )
            {
                cmd.createArg().setValue( "--arguments" );
                if ( arg.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( arg.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( arg );
                }
            }
        }
        
        if ( icon != null ) 
        {
            cmd.createArg().setValue( "--icon" );
            String s = icon.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }        
        }
        
        if ( singleton )
        {
            cmd.createArg().setValue( "--singleton" );
        }
        
        
        if ( identifier != null ) 
        {
            cmd.createArg().setValue( "--identifier" );
            if ( identifier.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( identifier.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( identifier );
            }         
        }
        
        if ( stripNativeCommands )
        {
            cmd.createArg().setValue( "--strip-native-commands" );
        }

        if ( ! ( ( jvmArgs == null ) || jvmArgs.isEmpty() ) )
        {
            cmd.createArg().setValue( "--jvmArgs" );
            String sb = getColonSeparatedList( jvmArgs );
            StringBuilder sb2 = new StringBuilder();
            sb2.append( "\"" ).append( sb.replace( "\\", "\\\\" ) ).append( "\"" );
            cmd.createArg().setValue( sb2.toString() );
        }
        
        if ( ! ( ( userJvmArgs == null ) || userJvmArgs.isEmpty() ) )
        {
            cmd.createArg().setValue( "--user-jvm-args" );
            String sb = getColonSeparatedList( userJvmArgs );
            StringBuilder sb2 = new StringBuilder();
            sb2.append( "\"" ).append( sb.replace( "\\", "\\\\" ) ).append( "\"" );
            cmd.createArg().setValue( sb2.toString() );        }
        
        if ( fileAssociations != null ) 
        {
            cmd.createArg().setValue( "--file-associations" );
            String s = fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            } 
        }
        
        if ( secondaryLauncher != null ) 
        {
            cmd.createArg().setValue( "--file-associations" );
            String s = fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            } 
        }
        
        if ( runtimeImage != null ) 
        {
            cmd.createArg().setValue( "--runtime-image" );
            String s = runtimeImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            } 
        }
        
        if ( appImage != null ) 
        {
            cmd.createArg().setValue( "--app-image" );
            String s = appImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            } 
        }
        
        if ( installDir != null ) 
        {
            cmd.createArg().setValue( "--install-dir" );
            if ( installDir.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( installDir.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( installDir );
            } 
        }
        
        if ( licenseFile != null ) 
        {
            cmd.createArg().setValue( "--license-file" );
            if ( licenseFile.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( licenseFile.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( licenseFile );
            }        
        }
        
        if ( copyright != null ) 
        {
            cmd.createArg().setValue( "--copyright" );
            if ( copyright.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( copyright.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( copyright );
            }       
        }
        
        if ( description != null ) 
        {
            cmd.createArg().setValue( "--description" );
            if ( description.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( description.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( description );
            }
        }
        
        if ( category != null ) 
        {
            cmd.createArg().setValue( "--category" );
            if ( category.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( category.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( category );
            }        
        }
       
        if ( vendor != null ) 
        {
            cmd.createArg().setValue( "--vendor" );
            if ( vendor.indexOf( " " ) > -1 )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( vendor.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( vendor );
            }
         }
        
        if ( hasLimitModules() )
        {
            cmd.createArg().setValue( "--limit-modules" );
            String sb = getColonSeparatedList( limitModules );
            cmd.createArg().setValue( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            cmd.createArg().setValue( "--add-modules" );
            cmd.createArg().setValue( getColonSeparatedList( modulesToAdd ) );
        }
       
        if ( SystemUtils.IS_OS_LINUX && ( linuxOptions != null ) )
        {
            if ( linuxOptions.bundleName != null )
            {
                cmd.createArg().setValue( "--linux-bundle-name" );
                if ( linuxOptions.bundleName.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( linuxOptions.bundleName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( linuxOptions.bundleName );
                }            
            }
            if ( linuxOptions.packageDeps != null )
            {
                cmd.createArg().setValue( "--linux-package-deps" );
                if ( linuxOptions.packageDeps.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( linuxOptions.packageDeps.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( linuxOptions.packageDeps );
                }      
            }
            if ( linuxOptions.rpmLicenseType != null )
            {
                cmd.createArg().setValue( "--linux-rpm-license-type" );
                if ( linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( linuxOptions.rpmLicenseType.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( linuxOptions.rpmLicenseType );
                }             
            }
            if ( linuxOptions.debMaintainer != null )
            {
                cmd.createArg().setValue( "--linux-deb-maintainer" );
                if ( linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( linuxOptions.debMaintainer.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( linuxOptions.debMaintainer );
                }
            }
        }
        
        if ( SystemUtils.IS_OS_WINDOWS && ( windowsOptions != null ) )
        {
            if ( windowsOptions.menu ) 
            {
                cmd.createArg().setValue( "--win-menu" );
            }
            if ( windowsOptions.menuGroup != null )
            {
                cmd.createArg().setValue( "--win-menu-group" );
                cmd.createArg().setValue( windowsOptions.menuGroup );
            }
            if ( windowsOptions.perUserInstall ) 
            {
                cmd.createArg().setValue( "--win-per-user-install" );
            }
            if ( windowsOptions.dirChooser ) 
            {
                cmd.createArg().setValue( "--win-dir-chooser" );
            }
            if ( windowsOptions.registryName != null )
            {
                cmd.createArg().setValue( "--win-registry-name" );
                if ( windowsOptions.registryName.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( windowsOptions.registryName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( windowsOptions.registryName );
                }
            }
            if ( windowsOptions.shortcut ) 
            {
                cmd.createArg().setValue( "--win-shortcut" );
            }
            if ( windowsOptions.console ) 
            {
                cmd.createArg().setValue( "--win-console" );
            }
            
        }
        
        if ( SystemUtils.IS_OS_MAC && ( macOptions != null ) )
        {
            if ( macOptions.sign ) 
            {
                cmd.createArg().setValue( "--mac-sign" );
            }
            if ( macOptions.bundleName != null )
            {
                cmd.createArg().setValue( "--mac-bundle-name" );
                if ( macOptions.bundleName.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( macOptions.bundleName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( macOptions.bundleName );
                }
            }
            if ( macOptions.bundleIdentifier != null )
            {
                cmd.createArg().setValue( "--mac-bundle-identifier" );
                if ( macOptions.bundleIdentifier.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( macOptions.bundleIdentifier.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( macOptions.bundleIdentifier );
                }
            }
            if ( macOptions.appStoreCategory != null )
            {
                cmd.createArg().setValue( "--mac-app-store-category" );
                if ( macOptions.appStoreCategory.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( macOptions.appStoreCategory.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( macOptions.appStoreCategory );
                }
            }
            if ( macOptions.appStoreEntitlements != null )
            {
                cmd.createArg().setValue( "--mac-app-store-entitlements" );
                String s = macOptions.appStoreEntitlements.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( s );
                }
            }
            if ( macOptions.bundleSigningPrefix != null )
            {
                cmd.createArg().setValue( "--mac-bundle-signing-prefix" );
                if ( macOptions.bundleSigningPrefix.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( macOptions.bundleSigningPrefix.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );

                }
                else
                {
                    cmd.createArg().setValue( macOptions.bundleSigningPrefix );
                }
            }
            if ( macOptions.signingKeyUserName != null )
            {
                cmd.createArg().setValue( "--mac-signing-key-username" );
                if ( macOptions.signingKeyUserName.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( macOptions.signingKeyUserName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );

                }
                else
                {
                    cmd.createArg().setValue( macOptions.signingKeyUserName );
                }
            }
            if ( macOptions.signingKeychain != null )
            {
                cmd.createArg().setValue( "--mac-signing-keychain" );
                String s = macOptions.signingKeychain.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( s );
                }
            }
        }
        
        cmd.createArg().setValue( "--force" );

        return cmd;
    }

    private void failIfParametersAreNotValid()
            throws MojoFailureException
    {
        if ( ( mode == null ) && ( "".equals( mode ) ) )
        {
            String message = "The mode parameter must be set";
            getLog().error( message );
            throw new MojoFailureException( message );
        }
        
        if ( ! ( "create-image".equals( mode ) 
                || "create-installer".equals( mode )
                || "create-jre-installer".equals( mode ) ) ) 
        {
            String message = "The mode parameter must be one of create-image, create-installer or create-jre-installer";
            getLog().error( message );
            throw new MojoFailureException( message );
        }
        
        if ( ( ( module == null ) && ( mainClass == null ) 
            && ( mainJar == null ) ) && ( appImage != null ) )
        {
// CHECKSTYLE_OFF: LineLength
            String message = "At least one of <module>, <mainClass> or <mainJar> must be specified if <appImage> is not present.";
// CHECKSTYLE_ON: LineLength
            getLog().error( message );
            throw new MojoFailureException( message );
        }
        int c = 0;
        if ( module != null )
        {
            c++;
        }
        if ( mainClass != null )
        {
            c++;
        }
        if ( mainJar != null )
        {
            c++;
        }
        if ( c > 1 )
        {
            // CHECKSTYLE_OFF: LineLength
            String message = "The parameters <module>, <mainClass> or <mainJar> \nare mutually exclusive, only one of them can be specified.";
            // CHECKSTYLE_ON: LineLength
            getLog().error( message );
            throw new MojoFailureException( message );
        }
        
        if ( ( type != null ) && ( "create-image".equals( mode ) ) )
        {
            String message = "<type> is not valid if <mode> is \"create-image\".";
            getLog().error( message );
            throw new MojoFailureException( message );
        }
        if ( ( type != null ) && ( ! ( "msi".equals( type ) || "exe".equals( type ) 
        		 || "rpm".equals( type ) || "deb".equals( type ) 
        		 || "dmg".equals( type ) || "pkg".equals( type )
                 || "pkg-app-store".equals( type ) ) ) ) 
        {
            String message = "<type> is not valid, only msi, rpm, deb, dmg, pkg, pkg-app-store are allowed.";
            getLog().error( message );
        }
        
    }
    
    
}
