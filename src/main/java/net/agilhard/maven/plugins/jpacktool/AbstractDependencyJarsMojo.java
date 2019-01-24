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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;

/**
 * @author bei
 *
 */
public abstract class AbstractDependencyJarsMojo<T extends AbstractDependencyJarsHandler> extends AbstractToolMojo {

	@Component(role = DependencyGraphBuilder.class, hint = "maven31")
	public DependencyGraphBuilder dependencyGraphBuilder;

	private T handler;

	/**
     * Constructor
     */
    public AbstractDependencyJarsMojo() {
    }
    
    public abstract T createHandler() throws MojoExecutionException, MojoFailureException;
    
    
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
    	
        this.handler = createHandler();
        handler.execute();
        
    }

	public T getHandler() {
		return handler;
	}



}
