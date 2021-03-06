
package net.agilhard.maven.plugins.jpacktool.base.mojo;

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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;

import net.agilhard.maven.plugins.jpacktool.base.handler.AbstractDependencyHandler;

/**
 * @author bei
 *
 */
public abstract class AbstractDependencyJarsMojo<T extends AbstractDependencyHandler> extends AbstractToolMojo {

    @Component(role = DependencyGraphBuilder.class, hint = "maven31")
    public DependencyGraphBuilder dependencyGraphBuilder;

    protected T handler;

    /**
     * Constructor
     */
    public AbstractDependencyJarsMojo() {
    }
    
    public abstract T createHandler() throws MojoExecutionException, MojoFailureException;
    
    
    /** {@inheritDoc} */
    @Override
    public void executeToolMain() throws MojoExecutionException, MojoFailureException
    {
        
        this.handler = createHandler();
        handler.execute();
        
    }

    public T getHandler() {
        return handler;
    }

	public DependencyGraphBuilder getDependencyGraphBuilder() {
		return dependencyGraphBuilder;
	}



}
