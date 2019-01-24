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
package net.agilhard.maven.plugins.jpacktool;

import java.io.IOException;
import java.util.Properties;

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
    name = "jpacktool-prepare", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    requiresProject = true)
public class JPackToolPrepareMojo extends AbstractDependencyJarsMojo<JPackToolHandler> {
   
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean copyAutomaticJars;
    
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean copyClassPathJars;

    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean copyModuleJars;
    
    
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean generateAutomaticJdeps;
    
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean generateClassPathJdeps;
    
    @Parameter( defaultValue = "true", required = true, readonly=false)
    private boolean generateModuleJdeps;
    /**
     * The jdeps Java Tool Executable.
     */
    private String jdepsExecutable;
    
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {

    	if ( (! outputDirectoryAutomaticJars.exists() ) && copyAutomaticJars ) {
    		if ( ! outputDirectoryAutomaticJars.mkdirs() ) {
    			throw new MojoExecutionException("directory can not be created:"+outputDirectoryAutomaticJars);
    		}
    	}
    	if ( (! outputDirectoryClasspathJars.exists() ) && copyClassPathJars ) {
    		if ( ! outputDirectoryClasspathJars.mkdirs() ) {
    			throw new MojoExecutionException("directory can not be created:"+outputDirectoryClasspathJars);
    		}
    	}
    	if ( (! outputDirectoryModules.exists() ) && copyModuleJars ) {
    		if ( ! outputDirectoryModules.mkdirs() ) {
    			throw new MojoExecutionException("directory can not be created:"+outputDirectoryModules);
    		}
    	}
    	
    	try {
			jdepsExecutable = this.getToolExecutable("jdeps");
		} catch (IOException e) {
			throw new MojoFailureException("i/o error", e);
		}
    	
    	super.execute();
    	
    	JPackToolHandler handler = getHandler();
    	
    	Properties props = this.project.getProperties();

    	String pfx=this.jpacktoolPropertyPrefix;
    	props.put(pfx+".used",Boolean.TRUE);
    	
    	for ( String nodeString : handler.getNodeStrings() ) {
    		getLog().info("--------------------");
    		getLog().info("Dependencies for "+nodeString);
    		
        	getLog().info("Dependency Modules:" + ( handler.getAllModulesMap().get(nodeString) == null ? "" : String.join(",", handler.getAllModulesMap().get(nodeString) ) ) );   
        	getLog().info("Dependency System Modules:" + ( handler.getLinkedSystemModulesMap().get(nodeString) == null ? "" : String.join(",", handler.getLinkedSystemModulesMap().get(nodeString) ) ) );   
        	getLog().info("Dependency Linked Modules:" + ( handler.getLinkedModulesMap().get(nodeString) == null ? "" : String.join(",", handler.getLinkedModulesMap().get(nodeString) ) ) );   
        	getLog().info("Dependency Automatic Modules:" + ( handler.getAutomaticModulesMap().get(nodeString)== null ? "" : String.join(",", handler.getAutomaticModulesMap().get(nodeString) ) ) );   
    	}
    	
    	props.put(pfx+".allModulesMap", handler.getAllModulesMap());
    	props.put(pfx+".linkedSystemModulesMap", handler.getLinkedSystemModulesMap());
    	props.put(pfx+".linkedModulesMap", handler.getLinkedModulesMap());
    	props.put(pfx+".automaticModulesMap", handler.getAutomaticModulesMap());
    	
    	props.put(pfx+".nodeStrings", handler.getNodeStrings());

		getLog().info("--------------------");

    	getLog().info("All Modules:" + String.join(",", handler.getAllModules()) ); 
    	props.put(pfx+".allModules", handler.getAllModules());
    	
    	getLog().info("Linked System Modules:" + String.join(",", handler.getLinkedSystemModules()) );   
    	props.put(pfx+".linkedSystemModules", handler.getLinkedSystemModules());
    	
    	getLog().info("Linked Modules:" + String.join(",", handler.getLinkedModules()) );   
    	props.put(pfx+".linkedModules", handler.getLinkedModules());
    			
    	getLog().info("Automatic Modules:" + String.join(",", handler.getAutomaticModules()) );   
    	props.put(pfx+".automaticModules", handler.getAutomaticModules());
    	
    	getLog().info("Jars on Classpath:" + String.join(",", handler.getJarsOnClassPath()) );   
    	props.put(pfx+".jarsOnClassPath", handler.getJarsOnClassPath());

    	if ( handler.getWarnings().size() > 0 ) {
    		getLog().warn("--------------------");
    		getLog().warn("Warnings from jdep calls");
    		getLog().warn("--------------------");
    		for ( String warn : handler.getWarnings() ) {
    			getLog().warn(warn);
    		}
    	}
    	if ( handler.getErrors().size() > 0 ) {
    		getLog().error("--------------------");
    		getLog().error("Errors from jdep calls");
    		getLog().error("--------------------");
    		for ( String err : handler.getErrors() ) {
    			getLog().error(err);
    		}
    		
    		throw new MojoFailureException("errors on jdep calls");
    	}
    }

	@Override
	public JPackToolHandler createHandler() throws MojoExecutionException, MojoFailureException
	{
		return new JPackToolHandler(this, dependencyGraphBuilder, 
				copyAutomaticJars ? outputDirectoryAutomaticJars : null,
				copyClassPathJars ? outputDirectoryClasspathJars : null,
				copyModuleJars ? outputDirectoryModules : null, jdepsExecutable,
			    generateAutomaticJdeps, generateClassPathJdeps, generateModuleJdeps);
		
	}
    
}
