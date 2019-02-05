
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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import net.agilhard.maven.plugins.jpacktool.base.handler.CollectJarsHandler;


/**
 * Collect jars and jmods from maven dependencies and copy them to a target folder.
 *
 * @author bei
 *
 */
@Mojo(
    name = "collect-jars", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    requiresProject = true)
public class CollectJarsMojo extends AbstractDependencyJarsMojo<CollectJarsHandler> {

    /** {@inheritDoc} */
    @Override
    public void executeToolMain() throws MojoExecutionException, MojoFailureException
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
        if ( ! outputDirectoryModules.exists() ) {
            if ( ! outputDirectoryModules.mkdirs() ) {
                throw new MojoExecutionException("directory can not be created:"+outputDirectoryModules);
            }
        }
        super.executeToolMain();
    }

    @Override
    public CollectJarsHandler createHandler() {
		return new CollectJarsHandler(this, dependencyGraphBuilder);
    }
    
}
