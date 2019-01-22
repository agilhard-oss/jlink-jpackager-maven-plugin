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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;

/**
 * @author bei
 *
 */
public abstract class AbstractHandleNonModJarsMojo extends AbstractToolMojo {

	@Component(role = DependencyGraphBuilder.class, hint = "maven31")
	public DependencyGraphBuilder dependencyGraphBuilder;


	/**
     * Constructor
     */
    public AbstractHandleNonModJarsMojo() {
    }
    
    public abstract AbstractHandleNonModJarsHandler createHandler();
    
    
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
    	
        AbstractHandleNonModJarsHandler handler = createHandler();
        handler.execute();
        
    }



}
