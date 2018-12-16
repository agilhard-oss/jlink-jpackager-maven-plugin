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
     * Default is <code>create-installer</code>.
     */
    @Parameter( defaultValue = "create-installer", required = true, readonly = false )
    private String mode;

    /**
     * Installer type of JPackager operation. 
     * <p>
     *  Valid values for &quot;type&quot; are &quot;msi&quot;, &quot;rpm&quot;, &quot;deb&quot;,
     *  &quot;dmg&quot;, &quot;pkg&quot;, &quot;pkg-app-store&quot;.
     *  If &quot;type&quot; is omitted, all supported types of installable
     *  packages for current platform will be generated.
     */
    @Parameter( required = false, readonly = false )
    private String type;

    /**
     * The output directory for the resulting Application Image or Package.
     * <p>
     * Defaults to ${project.build.directory}/maven-jpackager-ou
     * </p>
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-out", required = true, readonly = true )
    private File outputDirectoryPackage;

    /**
     * The output directory for the resulting Application Image or Package.
     * <p>
     * Defaults to ${project.build.directory}/maven-jpackager-in
     * </p>
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-in", required = true, readonly = true )
    private File inputDirectoryPackage;

    /**
     * Directory in which to use and place temporary files.
     * <p>
     * Defaults to ${project.build.directory}/maven-jpackager-build
     * </p>
     * <p>
     * <code>--build-root &lt;path&gt;</code>
     * </p>
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
    private List<String> files;

    /**
     * Name of the application. 
     * Defaults to ${project.name}.
     * 
     * <code>--name &lt;name&gt;</code>
     */
    @Parameter( defaultValue = "${project.name}", required = false, readonly = false )
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
    private String mainClass;

    /**
     * Version of the application.
     * <p>
     * Defaults to ${project.version}
     * </p>
     * <p>
     * Note a -SNAPSHOT or .SNAPSHOT is automatically deleted from the
     * version when constructing the jpackage command line arguments.
     * </p>
     * <code>--app-version &lt;version&gt;</code>
     */
    @Parameter( defaultValue = "${project.version}", required = false, readonly = false )
    private String appVersion;

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
     * <p>
     * Defaults to ${project.groupId}.${project.artifactId}
     * </p>
     * <code>--identifier &lt;identifier&gt;</code>
     */
    @Parameter( defaultValue = "${project.groupId}.${project.artifactId}", required = false, readonly = false )
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
     * <p>
     * Defaults to ${project.description}
     * </p>
     * <code>--description &lt;text&gt;</code>
     */
    @Parameter(  defaultValue = "${project.description}", required = false, readonly = false )
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
     * <p>
     * Defaults to ${project.organization}
     * </p>
     * 
     * <code>--vendor &lt;text&gt;</code>
     */
    @Parameter( defaultValue = "${project.organization}", required = false, readonly = false )
    private String vendor;

    
    /**
     * Main module of the application. This module must have the main-class,
     * and be on the module path.
     *
     * <code>--module &lt;name&gt;</code>
     */
    @Parameter( required = false, readonly = false )
    private String module;
    
    /**
     * Path JPackager looks in for modules when packaging the Java Runtime.
     * <p>
     * Currently jpackage can not use a PATH with several directories separated
     * by a colon or semicolon like jlink can.
     * <br/>
     * Thus we only use a single directory and copy the dependencies into that directory.
     * </p>
     * 
     * <p>
     * <code>--module-path &lt;module directory&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.build.directory}/maven-jpackager-jmods", required = true, readonly = false )
    private File modulePath;
    
    /**
     * Linux Options
     */
    @Parameter( required = false, readonly = false )
    JPackagerLinuxOptions linuxOptions;
    
    /**
     * Windows Options
     */
    @Parameter( required = false, readonly = false )
    JPackagerWindowsOptions windowsOptions;
    
    /**
     * Mac Options
     */
    @Parameter( required = false, readonly = false )
    JPackagerMacOptions macOptions;
    
    
    protected String getJPackagerExecutable()
        throws IOException

    {
        return getToolExecutable( "jpackage" );
    }

    public void execute() throws MojoExecutionException, MojoFailureException 
    {

        String jPackagerExec = getExecutable();

        getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jpackager [ " + jPackagerExec + " ]" );

        failIfParametersAreNotValid();
        
        ifBuildRootDirectoryDoesNotExistcreateIt();
        
        ifModulePathDirectoryDoesNotExistcreateIt();
        
        ifOutputDirectoryExistsDeleteIt();
        
        prepareModules();

        copyModulesToModulePath();

        Commandline cmd;
        try
        {
            cmd = createJPackagerCommandLine( pathsOfModules, modulesToAdd );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        cmd.setExecutable( jPackagerExec );

        executeCommand( cmd, outputDirectoryPackage );

        //File createZipArchiveFromPackage = createZipArchiveFromImage( buildDirectory, outputDirectoryPackage );

        failIfProjectHasAlreadySetAnArtifact();

        //getProject().getArtifact().setFile( createZipArchiveFromPackage );
        
        publishClassifierArtifacts();

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
    
    private void copyModulesToModulePath() throws MojoExecutionException
    {
        if ( pathsOfModules != null )
        {
            for ( String path : pathsOfModules ) 
            {
               Path file = new File( path ).toPath();
               if ( Files.isRegularFile( file ) )
               {
                   getLog().info( "copy module " + path );
                   try 
                   {
                       Path target = modulePath.toPath().resolve( file.getFileName() );
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
            jPackagerExec = getJPackagerExecutable();
        }
        catch ( IOException e )
        {
            throw new MojoFailureException( "Unable to find jpackager command: " + e.getMessage(), e );
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
                        "Failure during creation of " + buildRootPackage.getAbsolutePath() + " occured." );
            }
        }
    }
    
    private void ifModulePathDirectoryDoesNotExistcreateIt() throws MojoExecutionException 
    {
        if ( ! modulePath.exists() )
        {
            try
            {
                getLog().debug( "Create directory " + modulePath.getAbsolutePath() );
                modulePath.mkdirs();
            }
            catch ( Exception e )
            {
                getLog().error( "Exception", e );
                throw new MojoExecutionException(
                        "Failure during creation of " + modulePath.getAbsolutePath() + " occured." );
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

    private Commandline createJPackagerCommandLine( Collection<String> pathsOfModules, Collection<String> modulesToAdd )
        throws IOException
    {

        File file = new File( outputDirectoryPackage.getParentFile(), "jlinkArgs" );
        
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
            argsFile.println( outputDirectoryPackage.getCanonicalPath() );

            if ( inputDirectoryPackage.exists() )
            {
                argsFile.println( "--input" );
                argsFile.println( inputDirectoryPackage.getCanonicalPath() );
            }

            argsFile.println( "--build-root" );
            argsFile.println( buildRootPackage.getCanonicalPath() );
        }
        
        
        if ( ! ( ( files == null ) || files.isEmpty() ) )
        {
            argsFile.println( "--files" );
            String sb = getColonSeparatedList( files );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            argsFile.println( sb.toString() ); 
        }
       
        if ( name != null ) 
        {
            argsFile.println( "--name" );
            argsFile.println(  name );
        }
        
        if ( appVersion != null ) 
        {
            argsFile.println( "--app-version" );
            argsFile.println(  appVersion.replaceAll( "-SNAPSHOT", "" ).replaceAll( ".SNAPSHOT", "" ) );
        }
        
        if ( modulePath != null )
        {
            argsFile.println( "--module-path" );
            argsFile.println( modulePath );
        }
        
        if ( mainClass != null ) 
        {
            argsFile.println( "--class" );
            argsFile.println(  mainClass );
        }
        
        if ( mainJar != null ) 
        {
            argsFile.println( "--main-jar" );
            argsFile.println(  mainJar );
        }

        if ( module != null ) 
        {
            argsFile.println( "--module" );
            argsFile.println(  module );
        }

        if ( ! ( ( arguments == null ) || arguments.isEmpty() ) )
        {
            argsFile.println( "--arguments" );
            String sb = getColonSeparatedList( arguments );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            argsFile.println( sb.toString() ); 
        }
        
        if ( icon != null ) 
        {
            argsFile.println( "--icon" );
            argsFile.println( icon.getAbsolutePath() );
        }
        
        if ( singleton )
        {
            argsFile.println( "--singleton" );
        }
        
        
        if ( identifier != null ) 
        {
            argsFile.println( "--identifier" );
            argsFile.println(  identifier );
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
            argsFile.println(  fileAssociations.getAbsolutePath() );
        }
        
        if ( secondaryLauncher != null ) 
        {
            argsFile.println( "--file-associations" );
            argsFile.println(  fileAssociations.getAbsolutePath() );
        }
        
        if ( runtimeImage != null ) 
        {
            argsFile.println( "--runtime-image" );
            argsFile.println(  runtimeImage.getAbsolutePath() );
        }
        
        if ( appImage != null ) 
        {
            argsFile.println( "--app-image" );
            argsFile.println(  appImage.getAbsolutePath() );
        }
        
        if ( installDir != null ) 
        {
            argsFile.println( "--install-dir" );
            argsFile.println(  installDir );
        }
        
        if ( licenseFile != null ) 
        {
            argsFile.println( "--licenseFile" );
            argsFile.println(  licenseFile );
        }
        
        if ( copyright != null ) 
        {
            argsFile.println( "--copyright" );
            argsFile.println(  copyright );
        }
        
        if ( description != null ) 
        {
            argsFile.println( "--description" );
            argsFile.println(  description );
        }
        
        if ( category != null ) 
        {
            argsFile.println( "--category" );
            argsFile.println(  category );
        }
       
        if ( vendor != null ) 
        {
            argsFile.println( "--vendor" );
            argsFile.println(  vendor );
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
                argsFile.println( linuxOptions.bundleName );
            }
            if ( linuxOptions.packageDeps != null )
            {
                argsFile.println( "--linux-package-deps" );
                argsFile.println( linuxOptions.packageDeps );
            }
            if ( linuxOptions.rpmLicenseType != null )
            {
                argsFile.println( "--linux-rpm-license-type" );
                argsFile.println( linuxOptions.rpmLicenseType );
            }
            if ( linuxOptions.debMaintainer != null )
            {
                argsFile.println( "--linux-deb-maintainer" );
                argsFile.println( linuxOptions.debMaintainer );
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
                argsFile.println( windowsOptions.registryName );
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
                argsFile.println( macOptions.bundleName );
            }
            if ( macOptions.bundleIdentifier != null )
            {
                argsFile.println( "--mac-bundle-identifier" );
                argsFile.println( macOptions.bundleIdentifier );
            }
            if ( macOptions.appStoreCategory != null )
            {
                argsFile.println( "--mac-app-store-category" );
                argsFile.println( macOptions.appStoreCategory );
            }
            if ( macOptions.appStoreEntitlements != null )
            {
                argsFile.println( "--mac-app-store-entitlements" );
                argsFile.println( macOptions.appStoreEntitlements.getAbsolutePath() );
            }
            if ( macOptions.bundleSigningPrefix != null )
            {
                argsFile.println( "--mac-bundle-signing-prefix" );
                argsFile.println( macOptions.bundleSigningPrefix );
            }
            if ( macOptions.sigingKeyUserName != null )
            {
                argsFile.println( "--mac-signing-key-username" );
                argsFile.println( macOptions.sigingKeyUserName );
            }
            if ( macOptions.signingKeychain != null )
            {
                argsFile.println( "--mac-signing-keychain" );
                argsFile.println( macOptions.signingKeychain.getAbsolutePath() );
            }
        }
        
        argsFile.println( "--force" );
        argsFile.close();

        Commandline cmd = new Commandline();
        cmd.createArg().setValue( '@' + file.getAbsolutePath() );

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
        if ( ( type != null ) && ( ! ( "msi".equals( type ) || "rpm".equals( type ) 
                || "deb".equals( type ) || "dmg".equals( type ) || "pkg".equals( type )
                || "pkg-app-store".equals( type ) ) ) ) 
        {
            String message = "<type> is not valid, only msi, rpm, deb, dmg, pkg, pkg-app-store are allowed.";
            getLog().error( message );
        }
        
    }
}
