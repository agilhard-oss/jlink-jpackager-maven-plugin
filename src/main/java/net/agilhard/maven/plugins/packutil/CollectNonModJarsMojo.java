/**
 * Copyright Fr.Meyer's Sohn Logistics 2019. All Rights Reserved

 * $Date:  $
 * $Author:  $
 * $Revision:  $
 * $Source:  $
 * $State: Exp $ - $Locker:  $
 * **********************
 * auto generated header
 *
 * Project : jlink-jpackager-maven-plugin
 * Created by bei, 20.01.2019
 */
package net.agilhard.maven.plugins.packutil;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


/**
 * @author bei
 *
 */
@Mojo(
    name = "collect-nonmod-jars", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE,
    requiresProject = true)
public class CollectNonModJarsMojo extends AbstractHandleNonModJarsMojo {

    @Parameter( defaultValue = "${project.build.directory}/jars/automatic", required = true, readonly = false )
    private File outputDirectoryAutomaticJars;    

    @Parameter( defaultValue = "${project.build.directory}/jars/classpath", required = true, readonly = false )
    private File outputDirectoryClasspathJars;
    
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean onlyNamedAreAutomatic;
    
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
    	if ( ! outputDirectoryAutomaticJars.exists() ) {
    		if ( ! outputDirectoryAutomaticJars.mkdirs() ) {
    			throw new MojoExecutionException("directory can not be created:"+outputDirectoryAutomaticJars);
    		}
    	}
    	if ( ! outputDirectoryClasspathJars.exists() ) {
    		if ( ! outputDirectoryClasspathJars.mkdirs() ) {
    			throw new MojoExecutionException("directory can not be created:"+outputDirectoryClasspathJars);
    		}
    	}
    	super.execute();
    }

	@Override
	public AbstractHandleNonModJarsHandler createHandler() {
		return new CollectNonModJarsHandler(this, dependencyGraphBuilder, outputDirectoryAutomaticJars, outputDirectoryClasspathJars, onlyNamedAreAutomatic);
	}
    
}
