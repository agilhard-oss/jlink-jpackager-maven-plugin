package net.agilhard.maven.plugins.packutil;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;

public abstract class AbstractHandleNonModJarsHandler {
	public HashSet<String> handledNodes;
	final AbstractToolMojo mojo;
	DependencyGraphBuilder dependencyGraphBuilder;
	
	public AbstractHandleNonModJarsHandler(AbstractToolMojo mojo, DependencyGraphBuilder dependencyGraphBuilder) {
		this.mojo = mojo;
		this.handledNodes = new HashSet<>();
		this.dependencyGraphBuilder = dependencyGraphBuilder; 
	}
	
	public Log getLog() {
		return mojo.getLog();
	}
	
    protected abstract void handleNonModJar(final DependencyNode dependencyNode, final Artifact artifact, Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException;
	
    protected void handleNonModJarIfNotAlreadyHandled(final DependencyNode dependencyNode, final Artifact artifact, Map.Entry<File, JavaModuleDescriptor> entry) throws MojoExecutionException, MojoFailureException {
    	String key=dependencyNode.toNodeString();
    	
    	if ( ! handledNodes.contains(key) ) {
    		handledNodes.add(key);
    		handleNonModJar(dependencyNode, artifact, entry);
    	}
    	
    }

    
    protected void handleDependencyNode(final DependencyNode dependencyNode) throws MojoExecutionException, MojoFailureException {

        Artifact artifact = dependencyNode.getArtifact();
        
        final File file = artifact.getFile();

        final ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(file);

        final Toolchain toolchain = mojo.getToolchain();
        if (toolchain != null && toolchain instanceof DefaultJavaToolChain) {
            request.setJdkHome(new File(((DefaultJavaToolChain) toolchain).getJavaHome()));
        }

        ResolvePathsResult<File> resolvePathsResult;
        try {
            resolvePathsResult = mojo.locationManager.resolvePaths(request);
        } catch (final IOException e) {
            this.getLog().error("convertToModule -> IOException", e);
            throw new MojoExecutionException("convertToModule: IOException", e);
        }

        if (resolvePathsResult.getPathElements().entrySet().size() == 0) {
            this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, null);

        } else {

            for (final Map.Entry<File, JavaModuleDescriptor> entry : resolvePathsResult.getPathElements().entrySet()) {
                if (entry.getValue() == null) {
                    this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, entry);
                } else if (entry.getValue().isAutomatic()) {
                    this.handleNonModJarIfNotAlreadyHandled(dependencyNode, artifact, entry);
                } else {
                    this.getLog().debug("file is a module: " + file);
                }
            }
        }
    }

    protected void handleDependencyRoot(final DependencyNode dependencyNode) throws MojoExecutionException, MojoFailureException
    {

        dependencyNode.accept(new DependencyNodeVisitor() {

            /**
             * Starts the visit to the specified dependency node.
             *
             * @param node
             *            the dependency node to visit
             * @return <code>true</code> to visit the specified dependency node's children, <code>false</code> to skip
             *         the.resolvePat
             *         specified dependency node's children and proceed to its next sibling
             */
            public boolean visit(final DependencyNode node) {
                return true;
            }

            /**
             * Ends the visit to to the specified dependency node.
             *
             * @param node
             *            the dependency node to visit
             * @return <code>true</code> to visit the specified dependency node's next sibling, <code>false</code> to
             *         skip the
             *         specified dependency node's next siblings and proceed to its parent
             */
            @Override
            public boolean endVisit(final DependencyNode node) {

                final String type=node.getArtifact().getType();
                if ("jar".equals(type)) {
                    try {
                        handleDependencyNode(node);
                    } catch (final MojoExecutionException e) {
                       getLog().error("endVisit -> MojoExecutionException", e);
                    } catch (final MojoFailureException e) {
                        getLog().error("endVisit -> MojoFailureException", e);
                    }
                }

                return true;
            }
        });

    }

    public void execute() throws MojoExecutionException, MojoFailureException
    {
    
        final ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(mojo.session.getProjectBuildingRequest());

            buildingRequest.setProject(mojo.project);

            this.getLog().info("building dependency graph for project " + mojo.project.getArtifact());

        try {
            // No need to filter our search. We want to resolve all artifacts.

            final DependencyNode dependencyNode =
            		dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);

            this.handleDependencyRoot(dependencyNode);

        } catch (final DependencyGraphBuilderException e) {
            throw new MojoExecutionException("Could not resolve dependencies for project: " + mojo.project, e);
        }
    }
}

