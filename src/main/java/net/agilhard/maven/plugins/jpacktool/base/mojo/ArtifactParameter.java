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

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

public class ArtifactParameter extends DefaultArtifact {
	
	private static DefaultArtifactHandler artifactHandler;
	
	private static DefaultArtifactHandler getArtifactHandlerInstance() {
		if ( artifactHandler == null) {
			artifactHandler = new DefaultArtifactHandler();
		}
		return artifactHandler;
	}

	public ArtifactParameter() {
		super("net.agilhard.dummy","net.agilhard.dummy","1.0",(String)null,"jar", (String)null, getArtifactHandlerInstance());
	}
	
	public ArtifactParameter(String groupId, String artifactId, String version, String scope, String type,
			String classifier, ArtifactHandler artifactHandler) {
		super(groupId, artifactId, version, scope, type, classifier, artifactHandler);
	}

	public ArtifactParameter(String groupId, String artifactId, VersionRange versionRange, String scope, String type,
			String classifier, ArtifactHandler artifactHandler) {
		super(groupId, artifactId, versionRange, scope, type, classifier, artifactHandler);
	}

	public ArtifactParameter(String groupId, String artifactId, VersionRange versionRange, String scope, String type,
			String classifier, ArtifactHandler artifactHandler, boolean optional) {
		super(groupId, artifactId, versionRange, scope, type, classifier, artifactHandler, optional);
	}

}
