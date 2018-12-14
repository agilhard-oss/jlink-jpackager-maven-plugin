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
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

import net.agilhard.maven.plugins.jlink.AbstractPackageToolMojo;

/**
 * @author Bernd Eilers
 */
// CHECKSTYLE_OFF: LineLength
@Mojo( name = "jpackager", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true )
// CHECKSTYLE_ON: LineLength
public class JPackagerMojo extends AbstractPackageToolMojo 
{

    /**
     * Mode of JPackager operation. 
     * One of <code>create-image</code>, <code>create-installer</code>, <create-jre-installer>.
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
     * Defaults to ${project.version}
     * 
     * <code>--version &lt;version&gt;</code>
     */
    @Parameter( defaultValue = "${project.version}", required = false, readonly = false )
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
     * <p>
     * Defaults to ${project.groupId}.${project.artifactId}
     * </p>
     * <code>--identifier &lt;<identifier>&gt;</code>
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

        failIfParametersAreNotValid();
        
        ifBuildRootDirectoryDoesNotExistcreateIt();
        
        ifOutputDirectoryExistsDeleteIt();

        prepareModules( jmodsFolder );


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

        //File createZipArchiveFromImage = createZipArchiveFromImage( buildDirectory, outputDirectoryImage );

        failIfProjectHasAlreadySetAnArtifact();

        //getProject().getArtifact().setFile( createZipArchiveFromImage );

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
                        "Failure during deletion of " + outputDirectoryPackage.getAbsolutePath() + " occured." );
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

        Commandline cmd = new Commandline();
        
        cmd.createArg().setValue( mode );
        
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
            cmd.createArg().setValue( outputDirectoryPackage.getAbsolutePath() );

            cmd.createArg().setValue( "--input" );
            cmd.createArg().setValue( inputDirectoryPackage.getAbsolutePath() );
        

            cmd.createArg().setValue( "--build-root" );
            cmd.createArg().setValue( buildRootPackage.getAbsolutePath() );
        
        }
        
        
        if ( ! ( ( files == null ) || files.isEmpty() ) )
        {
            cmd.createArg().setValue( "--files" );
            String sb = getCommaSeparatedList( files );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb.toString() ); 
        }
       
        if ( name != null ) 
        {
            cmd.createArg().setValue( "--name" );
            cmd.createArg().setValue(  name );
        }
        
        if ( version != null ) 
        {
            cmd.createArg().setValue( "--version" );
            cmd.createArg().setValue(  version );
        }
        
        if ( pathsOfModules != null )
        {
            // @formatter:off
            cmd.createArg().setValue( "--module-path" );
            StringBuffer sb = new StringBuffer();
            sb.append( '"' )
                .append( getPlatformDependSeparateList( pathsOfModules )
                         .replace( "\\", "\\\\" ) ).append( '"' );
            
            cmd.createArg().setValue( sb.toString() );
            // @formatter:off
        }
        
        if ( mainClass != null ) 
        {
            cmd.createArg().setValue( "--class" );
            cmd.createArg().setValue(  mainClass );
        }
        
        if ( mainJar != null ) 
        {
            cmd.createArg().setValue( "--main-jar" );
            cmd.createArg().setValue(  mainJar );
        }

        if ( module != null ) 
        {
            cmd.createArg().setValue( "--module" );
            cmd.createArg().setValue(  module );
        }

        if ( ! ( ( arguments == null ) || arguments.isEmpty() ) )
        {
            cmd.createArg().setValue( "--arguments" );
            String sb = getCommaSeparatedList( arguments );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb.toString() ); 
        }
        
        if ( icon != null ) 
        {
            cmd.createArg().setValue( "--icon" );
            cmd.createArg().setValue( icon.getAbsolutePath() );
        }
        
        if ( singleton )
        {
            cmd.createArg().setValue( "--singleton" );
        }
        
        
        if ( identifier != null ) 
        {
            cmd.createArg().setValue( "--identifier" );
            cmd.createArg().setValue(  identifier );
        }
        
        if ( stripNativeCommands )
        {
            cmd.createArg().setValue( "--strip-native-commands" );
        }
        
        if ( ! ( ( jvmArgs == null ) || jvmArgs.isEmpty() ) )
        {
            cmd.createArg().setValue( "--jvm-args" );
            String sb = getCommaSeparatedList( jvmArgs );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb.toString() ); 
        }
        
        if ( ! ( ( userJvmArgs == null ) || userJvmArgs.isEmpty() ) )
        {
            cmd.createArg().setValue( "--user-jvm-args" );
            String sb = getCommaSeparatedList( jvmArgs );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb.toString() ); 
        }
        
        if ( fileAssociations != null ) 
        {
            cmd.createArg().setValue( "--file-associations" );
            cmd.createArg().setValue(  fileAssociations.getAbsolutePath() );
        }
        
        if ( secondaryLauncher != null ) 
        {
            cmd.createArg().setValue( "--file-associations" );
            cmd.createArg().setValue(  fileAssociations.getAbsolutePath() );
        }
        
        if ( runtimeImage != null ) 
        {
            cmd.createArg().setValue( "--runtime-image" );
            cmd.createArg().setValue(  runtimeImage.getAbsolutePath() );
        }
        
        if ( appImage != null ) 
        {
            cmd.createArg().setValue( "--app-image" );
            cmd.createArg().setValue(  appImage.getAbsolutePath() );
        }
        
        if ( installDir != null ) 
        {
            cmd.createArg().setValue( "--install-dir" );
            cmd.createArg().setValue(  installDir );
        }
        
        if ( licenseFile != null ) 
        {
            cmd.createArg().setValue( "--licenseFile" );
            cmd.createArg().setValue(  licenseFile );
        }
        
        if ( copyright != null ) 
        {
            cmd.createArg().setValue( "--copyright" );
            cmd.createArg().setValue(  copyright );
        }
        
        if ( description != null ) 
        {
            cmd.createArg().setValue( "--description" );
            cmd.createArg().setValue(  description );
        }
        
        if ( category != null ) 
        {
            cmd.createArg().setValue( "--category" );
            cmd.createArg().setValue(  category );
        }
       
        if ( vendor != null ) 
        {
            cmd.createArg().setValue( "--vendor" );
            cmd.createArg().setValue(  vendor );
        }
        
        if ( hasLimitModules() )
        {
            cmd.createArg().setValue( "--limit-modules" );
            String sb = getCommaSeparatedList( limitModules );
            cmd.createArg().setValue( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            cmd.createArg().setValue( "--add-modules" );
            // This must be name of the module and *NOT* the name of the
            // file! Can we somehow pre check this information to fail early?
            String sb = getCommaSeparatedList( modulesToAdd );
            StringBuffer sb2 = new StringBuffer();
            sb2.append( '"' ).append( sb.replace( "\\", "\\\\" ) ).append( '"' );
            cmd.createArg().setValue( sb.toString() );
            
        }
        
        if ( SystemUtils.IS_OS_LINUX && ( linuxOptions != null ) )
        {
            if ( linuxOptions.bundleName != null )
            {
                cmd.createArg().setValue( "--linux-bundle-name" );
                cmd.createArg().setValue( linuxOptions.bundleName );
            }
            if ( linuxOptions.packageDeps != null )
            {
                cmd.createArg().setValue( "--linux-package-deps" );
                cmd.createArg().setValue( linuxOptions.packageDeps );
            }
            if ( linuxOptions.rpmLicenseType != null )
            {
                cmd.createArg().setValue( "--linux-rpm-license-type" );
                cmd.createArg().setValue( linuxOptions.rpmLicenseType );
            }
            if ( linuxOptions.debMaintainer != null )
            {
                cmd.createArg().setValue( "--linux-deb-maintainer" );
                cmd.createArg().setValue( linuxOptions.debMaintainer );
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
                cmd.createArg().setValue( windowsOptions.registryName );
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
                cmd.createArg().setValue( macOptions.bundleName );
            }
            if ( macOptions.bundleIdentifier != null )
            {
                cmd.createArg().setValue( "--mac-bundle-identifier" );
                cmd.createArg().setValue( macOptions.bundleIdentifier );
            }
            if ( macOptions.appStoreCategory != null )
            {
                cmd.createArg().setValue( "--mac-app-store-category" );
                cmd.createArg().setValue( macOptions.appStoreCategory );
            }
            if ( macOptions.appStoreEntitlements != null )
            {
                cmd.createArg().setValue( "--mac-app-store-entitlements" );
                cmd.createArg().setValue( macOptions.appStoreEntitlements.getAbsolutePath() );
            }
            if ( macOptions.bundleSigningPrefix != null )
            {
                cmd.createArg().setValue( "--mac-bundle-signing-prefix" );
                cmd.createArg().setValue( macOptions.bundleSigningPrefix );
            }
            if ( macOptions.sigingKeyUserName != null )
            {
                cmd.createArg().setValue( "--mac-signing-key-username" );
                cmd.createArg().setValue( macOptions.sigingKeyUserName );
            }
            if ( macOptions.signingKeychain != null )
            {
                cmd.createArg().setValue( "--mac-signing-keychain" );
                cmd.createArg().setValue( macOptions.signingKeychain.getAbsolutePath() );
            }
        }
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
        
        if ( (( module == null ) && ( mainClass == null ) && ( mainJar == null )) && (appImage != null) )
        {
            String message = "At least one of <module>, <mainClass> or <mainJar> must be specified if <appImage> is not present.";
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
