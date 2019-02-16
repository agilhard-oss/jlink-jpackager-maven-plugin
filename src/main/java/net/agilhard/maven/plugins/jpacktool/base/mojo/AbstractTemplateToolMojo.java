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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import freemarker.template.TemplateException;
import net.agilhard.maven.plugins.jpacktool.base.template.AbstractGenerator;
import net.agilhard.maven.plugins.jpacktool.base.template.GeneratedFile;


/**
 * Base class for Mojos operating with templates.
 * 
 * <p>
 * The <a href="https://freemarker.apache.org/">Freemarker Java Template Engine</a>
 * is used for the templates.
 * </p>
 * 
 * @author Bernd Eilers
 *
 */
public abstract class AbstractTemplateToolMojo extends AbstractToolMojo {


	/**
	 * Internal class for the template generator.
	 * @author bei
	 *
	 */
	public class TemplateGenerator extends AbstractGenerator {

		public TemplateGenerator() {
			if (outputDirectoyTemplates != null) {
				if (outputDirectoyTemplates.exists()) {
					outputDirectoyTemplates.mkdirs();
				}
				setTemplateDirectory(outputDirectoyTemplates);
			}
		}
	}

	
	/**
	 * Output directory where templates are stored when they have been read from internal resource: URLs.
	 */
	@Parameter(defaultValue = "${project.build.directory}/jpacktool/templates", required = true, readonly = true)
	protected File outputDirectoyTemplates;

	protected TemplateGenerator templateGenerator;


	/**
	 * Constructor.
	 */
	public AbstractTemplateToolMojo() {
		
	}

	/**
	 * Initialize Templates copy them from resource: URLs to the filesystem.
	 * 
	 * <p>
	 * Can be implemented in derived classes the base class method does nothing.
	 * </p>
	 * 
	 * @throws MojoFailureException on plugin failure
	 */
	protected void initTemplates() throws MojoFailureException {
		// no default templates
	}
	
	protected String loadResourceFileIntoString(String path) throws MojoFailureException {
		InputStream inputStream = getClass().getResourceAsStream(path);
		if (inputStream == null) {
			throw new MojoFailureException("no such resource: " + path);
		}
		BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream));
		return buffer.lines().collect(Collectors.joining(System.getProperty("line.separator")));
	}

	protected String initTemplate(String res, String template) throws MojoFailureException {
		if (res == null) {
			return null;
		}
		String newRes = res;

		if (res.startsWith("resource:")) {
			if (!outputDirectoyTemplates.exists()) {
				outputDirectoyTemplates.mkdirs();
			}

			File file = new File(outputDirectoyTemplates, template);

			try (FileOutputStream fout = new FileOutputStream(file)) {
				newRes = file.getCanonicalPath();
				String path = res.substring(9);
				this.getLog().debug("resource=" + path);

				String text = loadResourceFileIntoString(path);
				try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
					ps.print(text);
					if (isVerbose()) {
						getLog().info("installed template " + template);
					}
				} catch (IOException e) {
					throw new MojoFailureException("cannot install template " + res, e);
				}

			} catch (FileNotFoundException e) {
				throw new MojoFailureException("file not found", e);
			} catch (IOException e) {
				throw new MojoFailureException("i/o error", e);
			}

		}
		return newRes;
	}

	protected Map<String, Object> getTemplateMap() {
		if (templateMap == null) {

			templateMap = new HashMap<String, Object>();

			templateMap.putAll(jpacktoolModel);

			Properties properties = getProject().getProperties();

			for (final String name : properties.stringPropertyNames()) {
				templateMap.put(name, properties.getProperty(name));
			}

		}
		return templateMap;
	}

	protected void generateFromTemplate(String templateName, File outputFile) throws MojoFailureException {
		GeneratedFile genFile;
		if (outputFile != null) {
			File dir = outputFile.getParentFile();
			if ( ! dir.exists() ) {
				dir.mkdirs();
			}
			try {
				genFile = new GeneratedFile(getTemplateGenerator().createFreemarkerConfiguration(), getTemplateMap(),
						templateName, outputFile);
			} catch (IOException e) {
				throw new MojoFailureException("error to generate from template", e);
			}
			try {
				genFile.generate();
			} catch (IOException | TemplateException e) {
				throw new MojoFailureException("error to generate from template", e);
			}
		}
	}

	public TemplateGenerator getTemplateGenerator() {
		if (templateGenerator == null) {
			templateGenerator = new TemplateGenerator();
		}
		return templateGenerator;
	}

}
