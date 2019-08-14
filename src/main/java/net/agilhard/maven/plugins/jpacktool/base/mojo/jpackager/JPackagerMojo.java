package net.agilhard.maven.plugins.jpacktool.base.mojo.jpackager;

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
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

import net.agilhard.maven.plugins.jpacktool.base.mojo.AbstractPackageToolMojo;

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
     * Mode of JPackager operation.
     * One of <code>create-image</code>, <code>create-installer</code>, <code>create-jre-installer</code>.
     */
    @Parameter( defaultValue = "create-installer", required = true, readonly = false )
    protected String mode;

    /**
     * Installer type of JPackager operation.
     * <p>
     *  Valid values for &lt;type&gt; are &quot;msi&quot;, &quot;rpm&quot;, &quot;deb&quot;,
     *  &quot;dmg&quot;, &quot;pkg&quot;, &quot;pkg-app-store&quot;.
     * </p><p>
     *  If &lt;type&gt; is omitted a value from the platform specific settings
     *  &lt;linuxType&gt;, &lt;windowsType&gt; or &lt;macType&gt; is being used.
     *  </p>
     */
    @Parameter( required = false, readonly = false )
    protected String type;

    /**
     * The output directory for the resulting Application Image or Package.
     *
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/jpackager-out", required = true, readonly = true )
    protected File outputDirectoryPackage;

    /**
     * The output directory for the resulting Application Image or Package.
     *
     * <p>
     * <code>--output &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/jpackager-in", required = true, readonly = true )
    protected File inputDirectoryPackage;

    /**
     * Directory in which to use and place temporary files.
     *
     * <p>
     * <code>--build-root &lt;path&gt;</code>
     * </p>
     */
    // TODO: is this a good final location?
    @Parameter( defaultValue = "${project.build.directory}/jpackager-build", required = true, readonly = false )
    protected File buildRootPackage;


    /**
     * TempDirectory where artifact modules are temporarily copied too.
     */
    @Parameter( defaultValue = "${project.build.directory}/jpackager-jmods", required = true, readonly = false )
    protected File moduleTempDirectory;


    /**
     * Flag whether to copy artifact modules to the moduleTempDirectory.
     *
     * <p>
     * The default value is true. Setting this to false only works if there are no modules with classes
     * in the module hierachy.
     * </p>
     *
     */
    @Parameter( defaultValue = "true", required = true, readonly = false )
    protected boolean copyArtifacts;

    /**
     * List of files in the base directory. If omitted, all files from "input"
     * directory (which is a mandatory argument in this case) will be packaged.
     *
     * <p>
     * <code>--files &lt;files&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected List<String> files;

    /**
     * Name of the application.
     *
     * <p>
     * <code>--name &lt;name&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.name}", required = false, readonly = false )
    protected String name;

    /**
     * The main JAR of the application. This JAR should have the main-class, and is
     * relative to the assembled application directory.
     *
     * <p>
     * <code>--main-jar&lt;jarname&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String mainJar;

    /**
     * Qualified name of the application class to execute.
     *
     * <p>
     * <code>-class &lt;className&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String mainClass;

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
    protected String appVersion;

    /**
     * Icon of the application bundle.
     *
     * <p>
     * <code>--icon &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected File icon;

    /**
     * Prevents multiple instances of the application from launching
     * (see SingleInstanceService API for more details).
     *
     * <p>
     * <code>--singleton</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected boolean singleton;

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
    protected String identifier;

    /**
     * Removes native executables from the custom run-time images.
     *
     * <p>
     * <code>--strip-native-commands</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected boolean stripNativeCommands;


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
    protected File fileAssociations;

    /**
     * Properties file that contains a collection of options for a secondary launcher.
     *
     * <p>
     * <code>--secondary-launcher &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected File secondaryLauncher;

    /**
     * Location of the predefined runtime image that is used to build
     * an application image and installable package.
     *
     * <p>
     * <code>--runtime-image &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected File runtimeImage;

    /**
     * Location of the predefined application image that is used to build
     * an installable package.
     *
     * <p>
     * <code>--app-image &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected File appImage;

    /**
     * Qualified name of the application class to execute.
     *
     * <p>
     * <code>-install-dir &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String installDir;

    /**
     * The license file, relative to the base directory.
     *
     * <p>
     * <code>--license-file &lt;path&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String licenseFile;


    /**
     * Copyright for the application.
     *
     * <p>
     * <code>--copyright &lt;text&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String copyright;

    /**
     * Description of the application.
     *
     * <p>
     * <code>--description &lt;text&gt;</code>
     * </p>
     */
    @Parameter(  defaultValue = "${project.description}", required = false, readonly = false )
    protected String description;

    /**
     * Category or group of the application
     *
     * <p>
     * <code>--category &lt;text&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String category;

    /**
     * Vendor of the application.
     *
     * <p>
     * <code>--vendor &lt;text&gt;</code>
     * </p>
     */
    @Parameter( defaultValue = "${project.organization}", required = false, readonly = false )
    protected String vendor;


    /**
     * Main module of the application. This module must have the main-class,
     * and be on the module path.
     *
     * <p>
     * <code>--module &lt;name&gt;</code>
     * </p>
     */
    @Parameter( required = false, readonly = false )
    protected String module;

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
    protected boolean usingJDK11Jpackager;

    
    protected Exception lastException;

    
    protected String getJPackageExecutable()
        throws IOException

    {
        return this.getToolExecutable( "jpackage" );
    }

    protected String getJPackagerExecutable()
            throws IOException

    {
        return this.getToolExecutable( "jpackager" );
    }



    public boolean isUsingJDK11Jpackager()
    {
        return this.usingJDK11Jpackager;
    }

	public void checkShouldSkip() {

		try {
			getExecutable();
		} catch (MojoFailureException e) {
			setShouldSkipReason("Unable to find jpackage or jpackager command");
		}
	}
    
    public void executeToolMain() throws MojoExecutionException, MojoFailureException
    {

        initJPacktoolModel();
        initTemplates();

        final String jPackagerExec = this.getExecutable();

        this.getLog().info( "Toolchain in jlink-jpackager-maven-plugin: jpackager [ " + jPackagerExec + " ]" );

        // TODO: Find a more better and cleaner way?
        final File jPackagerExecuteable = new File( jPackagerExec );

        // Really Hacky...do we have a better solution to find the jmods directory of the JDK?
        final File jPackagerParent = jPackagerExecuteable.getParentFile().getParentFile();

		File jmodsFolder;
        if ( sourceJdkModules != null && sourceJdkModules.isDirectory() )
        {
            jmodsFolder = new File ( sourceJdkModules, JMODS );
        }
        else
        {
            jmodsFolder = new File( jPackagerParent, JMODS );
        }
        this.maySetPlatformDefaultType();

        this.failIfParametersAreNotValid();
        
        if ( addJDKToLimitModules ) {
            this.addSystemModulesToLimitModules();
        }
        
        this.ifBuildRootDirectoryDoesNotExistcreateIt();

        this.ifOutputDirectoryExistsDeleteIt();

        File tempDirToAdd = this.moduleTempDirectory;
        if ( outputDirectoryModules.isDirectory() ) {
            tempDirToAdd = null;
        }
        
        prepareModules( jmodsFolder, true, this.copyArtifacts, tempDirToAdd );

        addSystemModulesFromJPackToolPrepare();
        
        if ( this.copyArtifacts && (! outputDirectoryModules.isDirectory() ))
        {
            this.ifModuleTempDirectoryDoesNotExistCreateIt();
            this.copyArtifactsToModuleTempDirectory();
        }

        updateModel();
        
        if ( jpacktoolPrepareUsed ) {
            try {
                this.moveJPacktoolJars();
            } catch (Exception e) {
                throw new MojoExecutionException( e.getMessage() );
            }
        }
        
        generateContent();
        
        executeResources();
        
        Commandline cmd;
        try
        {
            if ( this.isUsingJDK11Jpackager() )
            {
                cmd = this.createJPackagerCommandLine( this.pathsOfModules, this.modulesToAdd );
            }
            else
            {
                cmd = this.createJPackageCommandLine( this.pathsOfModules, this.modulesToAdd );
            }
        }
        catch ( final IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        cmd.setExecutable( jPackagerExec );

        this.executeCommand(cmd);

        if ( "create-image".equals( this.mode ) )
        {
            final File createZipArchiveFromPackage = this.createZipArchiveFromDirectory( this.buildDirectory, this.outputDirectoryPackage );

            this.failIfProjectHasAlreadySetAnArtifact();

            this.getProject().getArtifact().setFile( createZipArchiveFromPackage );
        }

        publishPackageArtifacts();
        publishJPacktoolProperties();
    }

    
    protected void moveJarToInputClasspath(Path source) throws IOException {
        Path target = resolveAndCreate(inputDirectoryPackage, null, classPathFolderName);

        target = target.resolve(source.getFileName());
        Files.move(source, target, REPLACE_EXISTING);
    }
    
    protected void moveJarToInputAutomatic(Path source) throws IOException  {
        Path target = resolveAndCreate(inputDirectoryPackage, null, automaticModulesFolderName);

        target = target.resolve(source.getFileName());
        Files.move(source, target, REPLACE_EXISTING);
    }
    
    protected void moveJarToInputModule(Path source) throws IOException  {
        Path target = resolveAndCreate(inputDirectoryPackage, null, modulesFolderName);

        target = target.resolve(source.getFileName());
        Files.move(source, target, REPLACE_EXISTING);
    }
    
    protected void moveJPacktoolJars() throws Exception {
        
        lastException = null;
        
        if ( this.jPacktoolMoveClassPathJars ) {
        Files.newDirectoryStream(outputDirectoryClasspathJars.toPath(),
                path -> path.toString().endsWith(".jar"))
                .forEach(t -> {
                    try {
                        moveJarToInputClasspath(t);
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
                        moveJarToInputAutomatic(t);
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
        Files.newDirectoryStream(outputDirectoryModules.toPath(),
                path -> path.toString().endsWith(".jar"))
                .forEach(t -> {
                    try {
                        moveJarToInputModule(t);
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

    
    protected void maySetPlatformDefaultType()
    {
        if ( ( ( this.type == null ) || ( "".equals( this.type ) ) )
        && ( ! "create-image".equals(this.mode)) )
        {
            if ( SystemUtils.IS_OS_LINUX && ( this.linuxOptions != null ) ) {
                this.type = this.linuxOptions.linuxType;
            }
            else if ( SystemUtils.IS_OS_WINDOWS ) {
                this.type = ( ( this.windowsOptions == null )  || ( this.windowsOptions.windowsType == null ) ) ? "exe" : this.windowsOptions.windowsType;
            }
            else if ( SystemUtils.IS_OS_MAC  ) {
                this.type = ( ( this.macOptions == null ) || ( this.macOptions.macType == null ) ) ? "dmg" : this.macOptions.macType;
            }
        this.getLog().info("<type> is not set using platform default (" + ( this.type == null ? "" : this.type ) + ")" );
        }
    }

    protected void publishPackageArtifacts()
    {
        final String[] extensions = {
                "msi", "exe", "rpm", "deb", "dmg",
                "pkg", "pkg-app-store"
                };

        for ( final String extension : extensions )
        {
            final File artifactFile = this.findPackageFile( extension );
            if ( artifactFile != null )
            {
                this.mavenProjectHelper.attachArtifact( this.project, extension, extension, artifactFile );
            }
        }

    }


    protected File findPackageFile( final String extension )
    {
        final class FindPackageResult
        {
            File file;

            public File getFile()
            {
                return this.file;
            }

            public void setFile( final File file )
            {
                this.file = file;
            }
        }
        final FindPackageResult result = new FindPackageResult();

        final BiPredicate<Path, BasicFileAttributes> predicate =
                ( path, attrs ) ->
                {
                        return path.toString().endsWith( extension );
                };

                try ( Stream<Path> stream =
                        Files.find( Paths.get( this.outputDirectoryPackage.toURI() ),
                                    1, predicate ) )
                {
                    stream.forEach( name ->
                    {
                        if ( result.getFile() != null )
                        {
                            this.getLog().info( "findPackageFile name=" + name );
                        }
                        result.setFile( name.toFile() );
                    } );
                } catch ( final IOException e )
                {
                    e.printStackTrace();
                }

        return result.getFile();
    }


    protected void copyArtifactsToModuleTempDirectory() throws MojoExecutionException
    {
        if ( this.pathsOfArtifacts != null )
        {
            for ( final String path : this.pathsOfArtifacts )
            {
               final Path file = new File( path ).toPath();
               if ( Files.isRegularFile( file ) )
               {
                   this.getLog().info( "copy module " + path );
                   try
                   {
                       final Path target = this.moduleTempDirectory.toPath().resolve( file.getFileName() );
                       Files.copy( file, target, REPLACE_EXISTING );
                   }
                   catch ( final IOException e )
                   {
                       this.getLog().error( "IOException", e );
                       throw new MojoExecutionException(
                            "Failure during copying of " + path + " occured." );
                   }
               }
           }
        }
    }

    protected String getExecutable() throws MojoFailureException
    {
        String jPackagerExec;
        try
        {
            jPackagerExec = this.getJPackageExecutable();
        }
        catch ( final IOException e )
        {
            try
            {
                jPackagerExec = this.getJPackagerExecutable();
                this.usingJDK11Jpackager = true;
            }
            catch ( final IOException e2 )
            {
                throw new MojoFailureException( "Unable to find jpackage or jpackager command: " + e2.getMessage(), e );
            }
        }
        return jPackagerExec;
    }


    protected void ifBuildRootDirectoryDoesNotExistcreateIt() throws MojoExecutionException
    {
        if ( ! this.buildRootPackage.exists() )
        {
            try
            {
                this.getLog().debug( "Create directory " + this.buildRootPackage.getAbsolutePath() );
                this.buildRootPackage.mkdirs();
            }
            catch ( final Exception e )
            {
                this.getLog().error( "Exception", e );
                throw new MojoExecutionException(
                        "FargsFileailure during creation of " + this.buildRootPackage.getAbsolutePath() + " occured." );
            }
        }
    }

    protected void ifModuleTempDirectoryDoesNotExistCreateIt() throws MojoExecutionException
    {
        if ( ! this.moduleTempDirectory.exists() )
        {
            try
            {
                this.getLog().debug( "Create directory " + this.moduleTempDirectory.getAbsolutePath() );
                this.moduleTempDirectory.mkdirs();
            }
            catch ( final Exception e )
            {
                this.getLog().error( "Exception", e );
                throw new MojoExecutionException(
                        "Failure during creation of " + this.moduleTempDirectory.getAbsolutePath() + " occured." );
            }
        }
    }

    protected void ifOutputDirectoryExistsDeleteIt() throws MojoExecutionException
    {
        if ( this.outputDirectoryPackage.exists() )
        {
            // Delete the output folder of JPackager before we start
            // otherwise JPackager will fail with a message "Error: directory already
            // exists: ..."
            try
            {
                this.getLog().debug( "Deleting existing " + this.outputDirectoryPackage.getAbsolutePath() );
                FileUtils.forceDelete( this.outputDirectoryPackage );
            }
            catch ( final IOException e )
            {
                this.getLog().error( "IOException", e );
                throw new MojoExecutionException(
                        "Failure during deletion of " + this.outputDirectoryPackage.getAbsolutePath() + " occured." );
            }
        }
    }

    /**
     * Build Commandline for JDK &gt;= 12 jpackage command
     *
     * @param pathsOfModules collected paths of modules
     * @param modulesToAdd collected modules to add
     * @return a Commandline
     * @throws IOException on i/o error
     */
    protected Commandline createJPackageCommandLine( final Collection<String> pathsOfModules, final Collection<String> modulesToAdd )
        throws IOException
    {

        final File file = new File( this.outputDirectoryPackage.getParentFile(), "jpackageArgs" );

        if ( !this.getLog().isDebugEnabled() )
        {
            file.deleteOnExit();
        }

        file.getParentFile().mkdirs();
        file.createNewFile();

        final PrintStream argsFile = new PrintStream( file );

        if ( this.type != null )
        {
            argsFile.println( "--package-type" );
            argsFile.println( this.type );
        }

        if ( this.verbose )
        {
            argsFile.println( "--verbose" );
        }

        if ( this.buildDirectory != null )
        {
            argsFile.println( "--output" );
            String s = this.outputDirectoryPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }

            if ( this.inputDirectoryPackage.exists() )
            {
                argsFile.println( "--input" );
                s = this.inputDirectoryPackage.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( s );
                }

            }

            argsFile.println( "--temp-root" );
            s = this.buildRootPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }

        }


        if ( ! ( ( this.files == null ) || this.files.isEmpty() ) )
        {
            argsFile.println( "--files" );
            final String sb = this.getPathSeparatedList( this.files );
            argsFile.println( sb.toString() );
        }

        if ( this.name != null )
        {
            argsFile.println( "--name" );
            if ( this.name.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.name ).println( "\"" );
            }
            else
            {
                argsFile.println( this.name );
            }
        }

        if ( this.appVersion != null )
        {
            argsFile.println( "--app-version" );
            argsFile.println(  this.appVersion.replaceAll( "-SNAPSHOT", "" ).replaceAll( ".SNAPSHOT", "" ) );
        }

        if ( pathsOfModules != null )
        {
            argsFile.println( "--module-path" );
            final String s = this.getPlatformDependSeparateList( pathsOfModules );
            if ( s.indexOf( " " ) > -1 )
            {
                argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }

        }


        if ( this.mainClass != null )
        {
            argsFile.println( "--class" );
            argsFile.println(  this.mainClass );
        }

        if ( this.mainJar != null )
        {
            argsFile.println( "--main-jar" );
            if ( this.mainJar.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.mainJar.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.mainJar );
            }
        }

        if ( this.module != null )
        {
            argsFile.println( "--module" );
            argsFile.println(  this.module );
        }

        if ( ! ( ( this.arguments == null ) || this.arguments.isEmpty() ) )
        {
            for ( final String arg : this.arguments )
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

        if ( this.icon != null )
        {
            argsFile.println( "--icon" );
            final String s = this.icon.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }
        }

        if ( this.singleton )
        {
            argsFile.println( "--singleton" );
        }


        if ( this.identifier != null )
        {
            argsFile.println( "--identifier" );
            if ( this.identifier.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.identifier.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.identifier );
            }
        }

        if ( this.stripNativeCommands )
        {
            argsFile.println( "--strip-native-commands" );
        }

        if ( ! ( ( this.jvmArgs == null ) || this.jvmArgs.isEmpty() ) )
        {
            for ( final String arg : this.jvmArgs )
            {
                argsFile.println( "--java-options" );
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

        if ( ! ( ( this.userJvmArgs == null ) || this.userJvmArgs.isEmpty() ) )
        {
            for ( final String arg : this.userJvmArgs )
            {
                argsFile.println( "--user-jvm-args" );
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

        if ( this.fileAssociations != null )
        {
            argsFile.println( "--file-associations" );
            final String s = this.fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }
        }

        if ( this.secondaryLauncher != null )
        {
            argsFile.println( "--file-associations" );
            final String s = this.fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }
        }

        if ( this.runtimeImage != null )
        {
            argsFile.println( "--runtime-image" );
            final String s = this.runtimeImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }
        }

        if ( this.appImage != null )
        {
            argsFile.println( "--app-image" );
            final String s = this.appImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( s );
            }
        }

        if ( this.installDir != null )
        {
            argsFile.println( "--install-dir" );
            if ( this.installDir.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.installDir.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.installDir );
            }
        }

        if ( this.licenseFile != null )
        {
            argsFile.println( "--license-file" );
            argsFile.println(this.licenseFile);
        }

        if ( this.copyright != null )
        {
            argsFile.println( "--copyright" );
            if ( this.copyright.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.copyright.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.copyright );
            }
        }

        if ( this.description != null )
        {
            argsFile.println( "--description" );
            if ( this.description.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.description.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.description );
            }
        }

        if ( this.category != null )
        {
            argsFile.println( "--category" );
            if ( this.category.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.category.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.category );
            }
        }

        if ( this.vendor != null )
        {
            argsFile.println( "--vendor" );
            if ( this.vendor.indexOf( " " ) > -1 )
            {
              argsFile.append( "\"" ).append( this.vendor.replace( "\\", "\\\\" ) ).println( "\"" );
            }
            else
            {
                argsFile.println( this.vendor );
            }
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
            argsFile.println( this.getCommaSeparatedList( modulesToAdd ) );
        }

        if ( SystemUtils.IS_OS_LINUX && ( this.linuxOptions != null ) )
        {
            if ( this.linuxOptions.bundleName != null )
            {
                argsFile.println( "--linux-bundle-name" );
                if ( this.linuxOptions.bundleName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.linuxOptions.bundleName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.linuxOptions.bundleName );
                }
            }
            if ( this.linuxOptions.packageDeps != null )
            {
                argsFile.println( "--linux-package-deps" );
                if ( this.linuxOptions.packageDeps.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.linuxOptions.packageDeps.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.linuxOptions.packageDeps );
                }
            }
            if ( this.linuxOptions.rpmLicenseType != null )
            {
                argsFile.println( "--linux-rpm-license-type" );
                if ( this.linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.linuxOptions.rpmLicenseType.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.linuxOptions.rpmLicenseType );
                }
            }
            if ( this.linuxOptions.debMaintainer != null )
            {
                argsFile.println( "--linux-deb-maintainer" );
                if ( this.linuxOptions.debMaintainer.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.linuxOptions.debMaintainer.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.linuxOptions.debMaintainer );
                }
            }
        }

        if ( SystemUtils.IS_OS_WINDOWS && ( this.windowsOptions != null ) )
        {
            if ( this.windowsOptions.menu )
            {
                argsFile.println( "--win-menu" );
            }
            if ( this.windowsOptions.menuGroup != null )
            {
                argsFile.println( "--win-menu-group" );
                argsFile.println( this.windowsOptions.menuGroup );
            }
            if ( this.windowsOptions.perUserInstall )
            {
                argsFile.println( "--win-per-user-install" );
            }
            if ( this.windowsOptions.dirChooser )
            {
                argsFile.println( "--win-dir-chooser" );
            }
            if ( this.windowsOptions.registryName != null )
            {
                argsFile.println( "--win-registry-name" );
                if ( this.windowsOptions.registryName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.windowsOptions.registryName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.windowsOptions.registryName );
                }
            }
            if ( this.windowsOptions.shortcut )
            {
                argsFile.println( "--win-shortcut" );
            }
            if ( this.windowsOptions.console )
            {
                argsFile.println( "--win-console" );
            }

        }

        if ( SystemUtils.IS_OS_MAC && ( this.macOptions != null ) )
        {
            if ( this.macOptions.sign )
            {
                argsFile.println( "--mac-sign" );
            }
            if ( this.macOptions.bundleName != null )
            {
                argsFile.println( "--mac-bundle-name" );
                if ( this.macOptions.bundleName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.macOptions.bundleName.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.macOptions.bundleName );
                }
            }
            if ( this.macOptions.bundleIdentifier != null )
            {
                argsFile.println( "--mac-bundle-identifier" );
                if ( this.macOptions.bundleIdentifier.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.macOptions.bundleIdentifier.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.macOptions.bundleIdentifier );
                }
            }
            if ( this.macOptions.appStoreCategory != null )
            {
                argsFile.println( "--mac-app-store-category" );
                if ( this.macOptions.appStoreCategory.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( this.macOptions.appStoreCategory.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.macOptions.appStoreCategory );
                }
            }
            if ( this.macOptions.appStoreEntitlements != null )
            {
                argsFile.println( "--mac-app-store-entitlements" );
                final String s = this.macOptions.appStoreEntitlements.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( s );
                }
            }
            if ( this.macOptions.bundleSigningPrefix != null )
            {
                argsFile.println( "--mac-bundle-signing-prefix" );
                if ( this.macOptions.bundleSigningPrefix.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" )
                  .append( this.macOptions.bundleSigningPrefix
                          .replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.macOptions.bundleSigningPrefix );
                }
            }
            if ( this.macOptions.signingKeyUserName != null )
            {
                argsFile.println( "--mac-signing-key-username" );
                if ( this.macOptions.signingKeyUserName.indexOf( " " ) > -1 )
                {
                  argsFile.append( "\"" )
                  .append( this.macOptions.signingKeyUserName
                          .replace( "\\", "\\\\" ) ).println( "\"" );
                }
                else
                {
                    argsFile.println( this.macOptions.signingKeyUserName );
                }
            }
            if ( this.macOptions.signingKeychain != null )
            {
                argsFile.println( "--mac-signing-keychain" );
                final String s = this.macOptions.signingKeychain.getCanonicalPath();
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

        argsFile.close();

        final Commandline cmd = new Commandline();
        cmd.createArg().setValue( '@' + file.getAbsolutePath() );

        return cmd;
    }


    /**
     * Build Commandline for JDK11 backported jpackager command
     *
     * @param pathsOfModules collected paths of modules
     * @param modulesToAdd collected modules to add
     * @return a Commandline
     * @throws IOException on i/o error
     */
    protected Commandline createJPackagerCommandLine( final Collection<String> pathsOfModules, final Collection<String> modulesToAdd )
        throws IOException
    {

        final Commandline cmd = new Commandline();

        if ( this.mode != null )
        {
            cmd.createArg().setValue( this.mode );
        }

        if ( this.type != null )
        {
            cmd.createArg().setValue( this.type );
        }

        if ( this.verbose )
        {
            cmd.createArg().setValue( "--verbose" );
        }

        if ( this.buildDirectory != null )
        {
            cmd.createArg().setValue( "--output" );
            String s = this.outputDirectoryPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
              final StringBuilder sb = new StringBuilder();
              sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
              cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }

            if ( this.inputDirectoryPackage.exists() )
            {
                cmd.createArg().setValue( "--input" );
                s = this.inputDirectoryPackage.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( s );
                }

            }

            cmd.createArg().setValue( "--build-root" );
            s = this.buildRootPackage.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }

        }


        if ( ! ( ( this.files == null ) || this.files.isEmpty() ) )
        {
            cmd.createArg().setValue( "--files" );
            final String sb = this.getPathSeparatedList( this.files );
            cmd.createArg().setValue( sb );
        }

        if ( this.name != null )
        {
            cmd.createArg().setValue( "--name" );
            if ( this.name.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.name.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.name );
            }
        }

        if ( this.appVersion != null )
        {
            cmd.createArg().setValue( "--version" );
            cmd.createArg().setValue(  this.appVersion.replaceAll( "-SNAPSHOT", "" ).replaceAll( ".SNAPSHOT", "" ) );
        }

        if ( pathsOfModules != null )
        {
            cmd.createArg().setValue( "--module-path" );
            final String s = this.getPlatformDependSeparateList( pathsOfModules );
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }

        }


        if ( this.mainClass != null )
        {
            cmd.createArg().setValue( "--class" );
            cmd.createArg().setValue(  this.mainClass );
        }

        if ( this.mainJar != null )
        {
            cmd.createArg().setValue( "--main-jar" );
            if ( this.mainJar.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.mainJar.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.mainJar );
            }
        }

        if ( this.module != null )
        {
            cmd.createArg().setValue( "--module" );
            cmd.createArg().setValue(  this.module );
        }

        if ( ! ( ( this.arguments == null ) || this.arguments.isEmpty() ) )
        {
            for ( final String arg : this.arguments )
            {
                cmd.createArg().setValue( "--arguments" );
                if ( arg.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( arg.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( arg );
                }
            }
        }

        if ( this.icon != null )
        {
            cmd.createArg().setValue( "--icon" );
            final String s = this.icon.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }
        }

        if ( this.singleton )
        {
            cmd.createArg().setValue( "--singleton" );
        }


        if ( this.identifier != null )
        {
            cmd.createArg().setValue( "--identifier" );
            if ( this.identifier.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.identifier.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.identifier );
            }
        }

        if ( this.stripNativeCommands )
        {
            cmd.createArg().setValue( "--strip-native-commands" );
        }

        if ( ! ( ( this.jvmArgs == null ) || this.jvmArgs.isEmpty() ) )
        {
            for ( final String arg : this.jvmArgs )
            {
                cmd.createArg().setValue( "--jvm-args" );
                if ( arg.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( arg.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( arg );
                }
            }
        }

        if ( ! ( ( this.userJvmArgs == null ) || this.userJvmArgs.isEmpty() ) )
        {
            for ( final String arg : this.jvmArgs )
            {
                cmd.createArg().setValue( "--user-jvm-args" );
                if ( arg.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( arg.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( arg );
                }
            }
    }

        if ( this.fileAssociations != null )
        {
            cmd.createArg().setValue( "--file-associations" );
            final String s = this.fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }
        }

        if ( this.secondaryLauncher != null )
        {
            cmd.createArg().setValue( "--file-associations" );
            final String s = this.fileAssociations.getCanonicalPath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }
        }

        if ( this.runtimeImage != null )
        {
            cmd.createArg().setValue( "--runtime-image" );
            final String s = this.runtimeImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }
        }

        if ( this.appImage != null )
        {
            cmd.createArg().setValue( "--app-image" );
            final String s = this.appImage.getAbsolutePath();
            if ( s.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( s );
            }
        }

        if ( this.installDir != null )
        {
            cmd.createArg().setValue( "--install-dir" );
            if ( this.installDir.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.installDir.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.installDir );
            }
        }

        if ( this.licenseFile != null )
        {
            cmd.createArg().setValue( "--license-file" );
            cmd.createArg().setValue( this.licenseFile);
        }

        if ( this.copyright != null )
        {
            cmd.createArg().setValue( "--copyright" );
            if ( this.copyright.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.copyright.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.copyright );
            }
        }

        if ( this.description != null )
        {
            cmd.createArg().setValue( "--description" );
            if ( this.description.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.description.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.description );
            }
        }

        if ( this.category != null )
        {
            cmd.createArg().setValue( "--category" );
            if ( this.category.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.category.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.category );
            }
        }

        if ( this.vendor != null )
        {
            cmd.createArg().setValue( "--vendor" );
            if ( this.vendor.indexOf( " " ) > -1 )
            {
                final StringBuilder sb = new StringBuilder();
                sb.append( "\"" ).append( this.vendor.replace( "\\", "\\\\" ) ).append( "\"" );
                cmd.createArg().setValue( sb.toString() );
            }
            else
            {
                cmd.createArg().setValue( this.vendor );
            }
         }

        if ( this.hasLimitModules() )
        {
            cmd.createArg().setValue( "--limit-modules" );
            final String sb = this.getCommaSeparatedList( this.limitModules );
            cmd.createArg().setValue( sb );
        }

        if ( !modulesToAdd.isEmpty() )
        {
            cmd.createArg().setValue( "--add-modules" );
            cmd.createArg().setValue( this.getCommaSeparatedList( modulesToAdd ) );
        }

        if ( SystemUtils.IS_OS_LINUX && ( this.linuxOptions != null ) )
        {
            if ( this.linuxOptions.bundleName != null )
            {
                cmd.createArg().setValue( "--linux-bundle-name" );
                if ( this.linuxOptions.bundleName.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.linuxOptions.bundleName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.linuxOptions.bundleName );
                }
            }
            if ( this.linuxOptions.packageDeps != null )
            {
                cmd.createArg().setValue( "--linux-package-deps" );
                if ( this.linuxOptions.packageDeps.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.linuxOptions.packageDeps.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.linuxOptions.packageDeps );
                }
            }
            if ( this.linuxOptions.rpmLicenseType != null )
            {
                cmd.createArg().setValue( "--linux-rpm-license-type" );
                if ( this.linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.linuxOptions.rpmLicenseType.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.linuxOptions.rpmLicenseType );
                }
            }
            if ( this.linuxOptions.debMaintainer != null )
            {
                cmd.createArg().setValue( "--linux-deb-maintainer" );
                if ( this.linuxOptions.rpmLicenseType.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.linuxOptions.debMaintainer.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.linuxOptions.debMaintainer );
                }
            }
        }

        if ( SystemUtils.IS_OS_WINDOWS && ( this.windowsOptions != null ) )
        {
            if ( this.windowsOptions.menu )
            {
                cmd.createArg().setValue( "--win-menu" );
            }
            if ( this.windowsOptions.menuGroup != null )
            {
                cmd.createArg().setValue( "--win-menu-group" );
                cmd.createArg().setValue( this.windowsOptions.menuGroup );
            }
            if ( this.windowsOptions.perUserInstall )
            {
                cmd.createArg().setValue( "--win-per-user-install" );
            }
            if ( this.windowsOptions.dirChooser )
            {
                cmd.createArg().setValue( "--win-dir-chooser" );
            }
            if ( this.windowsOptions.registryName != null )
            {
                cmd.createArg().setValue( "--win-registry-name" );
                if ( this.windowsOptions.registryName.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.windowsOptions.registryName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.windowsOptions.registryName );
                }
            }
            if ( this.windowsOptions.shortcut )
            {
                cmd.createArg().setValue( "--win-shortcut" );
            }
            if ( this.windowsOptions.console )
            {
                cmd.createArg().setValue( "--win-console" );
            }

        }

        if ( SystemUtils.IS_OS_MAC && ( this.macOptions != null ) )
        {
            if ( this.macOptions.sign )
            {
                cmd.createArg().setValue( "--mac-sign" );
            }
            if ( this.macOptions.bundleName != null )
            {
                cmd.createArg().setValue( "--mac-bundle-name" );
                if ( this.macOptions.bundleName.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.macOptions.bundleName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.macOptions.bundleName );
                }
            }
            if ( this.macOptions.bundleIdentifier != null )
            {
                cmd.createArg().setValue( "--mac-bundle-identifier" );
                if ( this.macOptions.bundleIdentifier.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.macOptions.bundleIdentifier.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.macOptions.bundleIdentifier );
                }
            }
            if ( this.macOptions.appStoreCategory != null )
            {
                cmd.createArg().setValue( "--mac-app-store-category" );
                if ( this.macOptions.appStoreCategory.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.macOptions.appStoreCategory.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( this.macOptions.appStoreCategory );
                }
            }
            if ( this.macOptions.appStoreEntitlements != null )
            {
                cmd.createArg().setValue( "--mac-app-store-entitlements" );
                final String s = this.macOptions.appStoreEntitlements.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( s.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );
                }
                else
                {
                    cmd.createArg().setValue( s );
                }
            }
            if ( this.macOptions.bundleSigningPrefix != null )
            {
                cmd.createArg().setValue( "--mac-bundle-signing-prefix" );
                if ( this.macOptions.bundleSigningPrefix.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.macOptions.bundleSigningPrefix.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );

                }
                else
                {
                    cmd.createArg().setValue( this.macOptions.bundleSigningPrefix );
                }
            }
            if ( this.macOptions.signingKeyUserName != null )
            {
                cmd.createArg().setValue( "--mac-signing-key-username" );
                if ( this.macOptions.signingKeyUserName.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append( "\"" ).append( this.macOptions.signingKeyUserName.replace( "\\", "\\\\" ) ).append( "\"" );
                    cmd.createArg().setValue( sb.toString() );

                }
                else
                {
                    cmd.createArg().setValue( this.macOptions.signingKeyUserName );
                }
            }
            if ( this.macOptions.signingKeychain != null )
            {
                cmd.createArg().setValue( "--mac-signing-keychain" );
                final String s = this.macOptions.signingKeychain.getCanonicalPath();
                if ( s.indexOf( " " ) > -1 )
                {
                    final StringBuilder sb = new StringBuilder();
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

    protected void failIfParametersAreNotValid()
            throws MojoFailureException
    {
        if ( ( this.mode == null ) && ( "".equals( this.mode ) ) )
        {
            final String message = "The mode parameter must be set";
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }

        if ( ! ( "create-image".equals( this.mode )
                || "create-installer".equals( this.mode )
                || "create-jre-installer".equals( this.mode ) ) )
        {
            final String message = "The mode parameter must be one of create-image, create-installer or create-jre-installer";
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }

        if ( ( ( this.module == null ) && ( this.mainClass == null )
            && ( this.mainJar == null ) ) && ( this.appImage != null ) )
        {
// CHECKSTYLE_OFF: LineLength
            final String message = "At least one of <module>, <mainClass> or <mainJar> must be specified if <appImage> is not present.";
// CHECKSTYLE_ON: LineLength
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }
        int c = 0;
        if ( this.module != null )
        {
            c++;
        }
        if ( this.mainClass != null )
        {
            c++;
        }
        if ( this.mainJar != null )
        {
            c++;
        }
        if ( c > 1 )
        {
            // CHECKSTYLE_OFF: LineLength
            final String message = "The parameters <module>, <mainClass> or <mainJar> \nare mutually exclusive, only one of them can be specified.";
            // CHECKSTYLE_ON: LineLength
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }

        if ( ( this.type != null ) && ( "create-image".equals( this.mode ) ) )
        {
            final String message = "<type> is not valid if <mode> is \"create-image\".";
            this.getLog().error( message );
            throw new MojoFailureException( message );
        }
        if ( ( this.type != null ) && ( ! ( "msi".equals( this.type ) || "exe".equals( this.type )
                 || "rpm".equals( this.type ) || "deb".equals( this.type )
                 || "dmg".equals( this.type ) || "pkg".equals( this.type )
                 || "pkg-app-store".equals( this.type ) ) ) )
        {
            final String message = "<type> is not valid, only msi, rpm, deb, dmg, pkg, pkg-app-store are allowed.";
            this.getLog().error( message );
        }

    }

    protected void updateJvmArgs() throws MojoFailureException {
        updateJvmArgs("app");
    }

	protected void generateContent() throws MojoExecutionException {
		generateContent(inputDirectoryPackage);
	}
	
	protected void executeResources() throws MojoExecutionException {
		executeResources(inputDirectoryPackage);
	}
}
