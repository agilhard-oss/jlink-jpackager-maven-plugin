package net.agilhard.maven.plugins.jpacktool;

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
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
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
    extends AbstractToolMojo
{

    protected static final String JMODS = "jmods";    

    /**
     * Name of the generated ZIP file in the <code>target</code> directory. This will not change the name of the
     * installed/deployed file.
     */        List<String> deps = new ArrayList<>();

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    protected String finalName;
    
    
    /**
     * The JAR archiver needed for archiving the environments.
     */
    @Component( role = Archiver.class, hint = "zip" )
    private ZipArchiver zipArchiver;

    /**
     * Flag to ignore automatic modules.
     */
    @Parameter( defaultValue = "true" )
    protected boolean ignoreAutomaticModules;


    /**
     * Include additional paths on the <code>--module-path</code> option. Project dependencies and JDK modules are
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
     * Directory with .jar modules to add to --limit-modules
     */
    @Parameter
    protected List<File> limitModulesDirs;
    
    /**
     * Toggle whether to add all modules in the java boot path to the limitModules setting.
     */
    @Parameter( defaultValue = "false" )
    protected boolean addJDKToLimitModules;
    
    /**
     * Flag if to move classpath jars from jpacktool-prepare goal
     */
    @Parameter( defaultValue = "true" )
    protected boolean jPacktoolMoveClassPathJars;

    /**
     * Flag if to move real modules from jpacktool-prepare goal
     */
    @Parameter( defaultValue = "true" )
    protected boolean jPacktoolMoveAutomaticModules;

    /**
     * Flag if to move real modules from jpacktool-prepare goal
     */
    @Parameter( defaultValue = "false" )
    protected boolean jPacktoolMoveRealModules;
    
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

    
    /**
     * Directory with .jar modules to add to --add-modules
     */
    @Parameter
    protected List<File> addModulesDirs;
    
    
    /**
     * Name of the classpath folder
     */
    @Parameter( defaultValue = "classpath" )
    protected String classPathFolderName;
    
    
    /**
     * Name of the automatic-modules folder
     */
    @Parameter( defaultValue = "automatic-modules" )
    protected String automaticModulesFolderName;
    
    
    /**
     * Name of the modules folder
     */
    @Parameter( defaultValue = "modules" )
    protected String modulesFolderName;
    
    /**
     * Flag if jpacktool-prepare goal has been used before
     */
    protected boolean jpacktoolPrepareUsed;

    /**
     * set jpacktoolPrepareUsed variable based on maven property
     */
    protected void checkJpacktoolPrepareUsed() {
    	String pfx=this.jpacktoolPropertyPrefix;
    	Boolean b = (Boolean) this.project.getProperties().get(pfx+".used");
    	jpacktoolPrepareUsed = b == null ? false : b.booleanValue();	
    }
    
    
    /**
     * resolve to path and create directory if not exists.
     * @throws IOException 
     */
    protected Path resolveAndCreate(File dir, String appFolderName, String folderName) throws IOException
    {
    	Path target=dir.toPath();
    	if ( (appFolderName != null) && ( ! "".equals(appFolderName) ) ) {
    		target = target.resolve(appFolderName);
    	}
    	if ( (folderName != null) && ( ! "".equals(folderName) ) ) {
    		target = target.resolve(folderName);
    	}
    	if (!Files.exists(target)) {
    		Files.createDirectories(target);
    	}
    	return target;
    }
    
    /**
     * This will convert a module path separated by either {@code :} or {@code ;} into a string which uses the platform
     * depend path separator uniformly.
     *
     * @param pluginModulePath The module path.
     * @return The platform separated module path.
     */
    protected StringBuilder convertSeparatedModulePathToPlatformSeparatedModulePath( final String pluginModulePath )
    {
        final StringBuilder sb = new StringBuilder();
        // Split the module path by either ":" or ";" linux/windows path separator and
        // convert uniformly to the platform used separator.
        final String[] splitModule = pluginModulePath.split( "[;:]" );
        for ( final String module : splitModule )
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
    protected String getPlatformDependSeparateList( final Collection<String> modulePaths )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final String module : modulePaths )
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
    protected String getCommaSeparatedList( final Collection<String> modules )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final String module : modules )
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
    protected String getColonSeparatedList( final Collection<String> modules )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final String module : modules )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ':' );
            }
            sb.append( module );
        }
        return sb.toString();
    }


    private List<File> getCompileClasspathElements( final MavenProject project )
    {
        final List<File> list = new ArrayList<>( project.getArtifacts().size() + 1 );

        for ( final Artifact a : project.getArtifacts() )
        {
            this.getLog().debug( "Artifact: " + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() );
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

        final Map<String, File> modulepathElements = new HashMap<>();

        try
        {
            final Collection<File> dependencyArtifacts = this.getCompileClasspathElements( this.getProject() );

            final ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles( dependencyArtifacts );

            final Toolchain toolchain = this.getToolchain();
            if ( toolchain != null && toolchain instanceof DefaultJavaToolChain )
            {
                request.setJdkHome( new File( ( (DefaultJavaToolChain) toolchain ).getJavaHome() ) );
            }

            final ResolvePathsResult<File> resolvePathsResult = this.locationManager.resolvePaths( request );

            for ( final Map.Entry<File, JavaModuleDescriptor> entry : resolvePathsResult.getPathElements().entrySet() )
            {
                if ( entry.getValue() == null )
                {
                    final String message = "The given dependency " + entry.getKey()
                        + " does not have a module-info.java file. So it can't be linked.";
                    this.getLog().error( message );
                    throw new MojoFailureException( message );
                }

                // Don't warn for automatic modules, let the jlink tool do that
                this.getLog().debug( " module: " + entry.getValue().name() + " automatic: "
                    + entry.getValue().isAutomatic() );
                if ( modulepathElements.containsKey( entry.getValue().name() ) )
                {
                    this.getLog().warn( "The module name " + entry.getValue().name() + " does already exists." );
                } else {

                	if ( this.ignoreAutomaticModules )
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
            }

            // This part is for the module in target/classes ? (Hacky..)
            // FIXME: Is there a better way to identify that code exists?
            final AtomicBoolean b = new AtomicBoolean();

            if ( this.outputDirectory.exists() )
            {
                final BiPredicate<Path, BasicFileAttributes> predicate =
                ( path, attrs ) ->
                {
                        return path.toString().endsWith( ".class" );
                };

                try ( Stream<Path> stream =
                        Files.find( Paths.get( this.outputDirectory.toURI() ),
                                    Integer.MAX_VALUE, predicate ) )
                {
                    stream.forEach( name ->
                    {
                        b.set( true );
                    } );
                } catch ( final IOException e )
                {
                    e.printStackTrace();
                }
            }
            if ( b.get() )
            {
                final List<File> singletonList = Collections.singletonList( this.outputDirectory );

                final ResolvePathsRequest<File> singleModuls = ResolvePathsRequest.ofFiles( singletonList );

                final ResolvePathsResult<File> resolvePaths = this.locationManager.resolvePaths( singleModuls );
                for ( final Entry<File, JavaModuleDescriptor> entry : resolvePaths.getPathElements().entrySet() )
                {
                    if ( entry.getValue() == null )
                    {
                        final String message = "The given project " + entry.getKey()
                            + " does not contain a module-info.java file. So it can't be linked.";
                        this.getLog().error( message );
                        throw new MojoFailureException( message );
                    }
                    if ( modulepathElements.containsKey( entry.getValue().name() ) )
                    {
                        this.getLog().warn( "The module name " + entry.getValue().name() + " does already exists." );
                    }
                    modulepathElements.put( entry.getValue().name(), entry.getKey() );
                }
            }

        }
        catch ( final IOException e )
        {
            this.getLog().error( e.getMessage() );
            throw new MojoFailureException( e.getMessage() );
        }

        return modulepathElements;
    }


    protected void prepareModules( final File jmodsFolder ) throws MojoFailureException
    {
        this.prepareModules( jmodsFolder, false, false, null );
    }
    
    protected void prepareModules( final File jmodsFolder, final boolean useDirectory,
            final boolean copyArtifacts, final File moduleTempDirectory ) throws MojoFailureException
    {
    	
        if ( this.addModules != null )
        {
            this.modulesToAdd.addAll( this.addModules );
        }

        if ( this.modulePaths != null )
        {
            this.pathsOfModules.addAll( this.modulePaths );
        }

    	if ( addModulesDirs != null ) {
    		for ( File dir : addModulesDirs ) {
    			try {
    				String p=dir.getCanonicalPath();
					this.pathsOfModules.add(p);
					
			    	ModuleFinder finder = ModuleFinder.of(dir.toPath());
			    	Set<ModuleReference> moduleReferences = finder.findAll();
			    	
			    	this.getLog().debug("addModulesDir " + p + " found " + ( moduleReferences == null ? 0 : moduleReferences.size() ) + " module references");
			    	for ( ModuleReference moduleReference : moduleReferences ) {
			    		this.addModules.add(moduleReference.descriptor().name());
			    	}
				} catch (IOException e) {
					throw new MojoFailureException("i/o error:", e);
				}
    		}
    	}
        
        if ( copyArtifacts )
        {
            if ( moduleTempDirectory != null)
            {
                this.pathsOfModules.add( moduleTempDirectory.getAbsolutePath() );
            }
        }
        
        if ( (outputDirectoryModules != null) && (outputDirectoryModules.isDirectory() ) )
        {
            this.pathsOfModules.add( outputDirectoryModules.getAbsolutePath() );
        }
        
        for ( final Entry<String, File> item : this.getModulePathElements().entrySet() )
        {
            this.getLog().info( " -> module: " + item.getKey() + " ( " + item.getValue().getPath() + " )" );

            // We use the real module name and not the artifact Id...
             this.modulesToAdd.add( item.getKey() );
             if ( copyArtifacts )
             {
            	 if ( ! outputDirectoryModules.isDirectory() ) {
            		 this.pathsOfArtifacts.add( item.getValue().getPath() );
            	 }
             }
             else
             {
                 if ( useDirectory )
                 {
                    this.pathsOfModules.add( item.getValue().getParentFile().getPath() );
                }
                else
                {
                    this.pathsOfModules.add( item.getValue().getPath() );
                }
            }
        }
        if ( jmodsFolder != null )
        {
            // The jmods directory of the JDK
            this.pathsOfModules.add( jmodsFolder.getAbsolutePath() );
        }
        
        if ( outputDirectoryModules.isDirectory() ) {
        	this.pathsOfModules.add(outputDirectoryModules.getAbsolutePath());
        }
    }

    protected File createZipArchiveFromImage( final File outputDirectory, final File outputDirectoryImage )
            throws MojoExecutionException
        {
            this.zipArchiver.addDirectory( outputDirectoryImage );

            final File resultArchive = this.getArchiveFile( outputDirectory, this.finalName, null, "zip" );

            this.zipArchiver.setDestFile( resultArchive );
            try
            {
                this.zipArchiver.createArchive();
            }
            catch ( final ArchiverException e )
            {
                this.getLog().error( e.getMessage(), e );
                throw new MojoExecutionException( e.getMessage(), e );
            }
            catch ( final IOException e )
            {
                this.getLog().error( e.getMessage(), e );
                throw new MojoExecutionException( e.getMessage(), e );
            }

            return resultArchive;

        }

    protected void failIfProjectHasAlreadySetAnArtifact() throws MojoExecutionException
    {
        if ( this.projectHasAlreadySetAnArtifact() )
        {
            throw new MojoExecutionException( "You have to use a classifier "
                + "to attach supplemental artifacts to the project instead of replacing them." );
        }
    }

    protected boolean projectHasAlreadySetAnArtifact()
    {
        if ( this.getProject().getArtifact().getFile() != null )
        {
            return this.getProject().getArtifact().getFile().isFile();
        }
        else
        {
            return false;
        }
    }

    protected boolean hasLimitModules()
    {
        return this.limitModules != null && !this.limitModules.isEmpty();
    }

    /**
     * Returns the archive file to generate, based on an optional classifier.
     *
     * @param basedir
     *            the output directory
     * @param finalName
     *            the name of the ear file
     * @param classifier
     *            an optional classifier
     * @param archiveExt
     *            The extension of the file.
     * @return the file to generate
     */
    protected File getArchiveFile(final File basedir, final String finalName, final String classifier,
        final String archiveExt) {
        if (basedir == null) {
            throw new IllegalArgumentException("basedir is not allowed to be null");
        }
        if (finalName == null) {
            throw new IllegalArgumentException("finalName is not allowed to be null");
        }
        if (archiveExt == null) {
            throw new IllegalArgumentException("archiveExt is not allowed to be null");
        }

        if (finalName.isEmpty()) {
            throw new IllegalArgumentException("finalName is not allowed to be empty.");
        }
        if (archiveExt.isEmpty()) {
            throw new IllegalArgumentException("archiveExt is not allowed to be empty.");
        }

        final StringBuilder fileName = new StringBuilder(finalName);

        if (this.hasClassifier(classifier)) {
            fileName.append("-").append(classifier);
        }

        fileName.append('.');
        fileName.append(archiveExt);

        return new File(basedir, fileName.toString());
    }

    protected boolean hasClassifier(final String classifier) {
        boolean result = false;
        if (classifier != null && classifier.trim().length() > 0) {
            result = true;
        }

        return result;
    }

    protected void addToLimitModules(String name) {
    	if ( limitModules == null ) {
    		limitModules = new ArrayList<String>();
    	}
    	if ( ! limitModules.contains(name) ) {
    		getLog().info("addToLimitModules name="+name);

    		limitModules.add(name);
    	}
    }
    
    protected void addSystemModulesToLimitModules() throws MojoExecutionException {
    	if ( limitModules == null ) {
    		limitModules = new ArrayList<String>();
    	}
    	limitModules.addAll( this.getSystemModules() );
    	
    }
    
    protected void addModulesToLimitModules(Path...paths) {
    	
    	for ( Path path : paths ) {
    		this.getLog().debug("addModulesToLimitModules path="+path);
    	}
    	ModuleFinder finder = ModuleFinder.of(paths);
    	Set<ModuleReference> moduleReferences = finder.findAll();
    	
    	this.getLog().debug("addModulesToLimitModules found " + ( moduleReferences == null ? 0 : moduleReferences.size() ) + " module references");
    	for ( ModuleReference moduleReference : moduleReferences ) {
    		 this.addToLimitModules(moduleReference.descriptor().name());
    	}
    	
    }
    
    protected void addSystemModulesFromJPackTool() {
    	@SuppressWarnings("unchecked")
		List<String> linkedSystemModules = (List<String>) this.project.getProperties().get(this.jpacktoolPropertyPrefix+".linkedSystemModules");
    	if ( linkedSystemModules != null ) {
    		if ( addModules == null ) {
    			addModules=new ArrayList<String>();
    		}
    		for ( String mod : linkedSystemModules ) {
    			if ( ! addModules.contains(mod) ) {
    				addModules.add(mod);
    			}
    		}
    	}
    
    }
}
