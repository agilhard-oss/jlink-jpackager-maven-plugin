package net.agilhard.maven.plugins.jpacktool.base.filter;

import java.io.BufferedReader;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.shared.filtering.DefaultMavenFileFilter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.plexus.build.incremental.BuildContext;

import freemarker.template.TemplateException;
import net.agilhard.maven.plugins.jpacktool.base.template.AbstractGenerator;
import net.agilhard.maven.plugins.jpacktool.base.template.GeneratedFile;

import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.IOUtil;
import org.apache.maven.shared.utils.io.FileUtils.FilterWrapper;

@Component(role = MavenFileFilter.class, hint = "default")
public class JPacktoolMavenFileFilter extends DefaultMavenFileFilter {

    @Requirement
    private MavenReaderFilter readerFilter;
    
	@Requirement
	private BuildContext buildContext;

	private Map<String, Object> templateMap;

	public class TemplateGenerator extends AbstractGenerator {

		public TemplateGenerator(File templateDir) {
			if (templateDir != null) {
				if (templateDir.exists()) {
					templateDir.mkdirs();
				}
				setTemplateDirectory(templateDir);
			}
		}
	}

	/** {@inheritDoc} */
	public void defaultCopyFile(File from, File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
			String encoding, boolean overwrite) throws MavenFilteringException {
		try {
			if (filtering) {
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("filtering " + from.getPath() + " to " + to.getPath());
				}
				filterFile(from, to, encoding, filterWrappers);
			} else {
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("copy " + from.getPath() + " to " + to.getPath());
				}
				FileUtils.copyFile(from, to, encoding, new FileUtils.FilterWrapper[0], overwrite);
			}

			buildContext.refresh(to);
		} catch (IOException e) {
			throw new MavenFilteringException(e.getMessage(), e);
		}

	}

	public void copyFile(File from, File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
			String encoding, boolean overwrite) throws MavenFilteringException {
		if (templateMap != null) {
			customCopyFile(from, to, filtering, filterWrappers, encoding, overwrite);
		} else {
			defaultCopyFile(from, to, filtering, filterWrappers, encoding, overwrite);
		}
	}

	public void customCopyFile(File from, File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
			String encoding, boolean overwrite) throws MavenFilteringException {
		try {
			if (filtering) {
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("filtering " + from.getPath() + " to " + to.getPath());
				}
				customFilterFile(from, to, encoding, filterWrappers);
			} else {
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("copy " + from.getPath() + " to " + to.getPath());
				}
				FileUtils.copyFile(from, to, encoding, new FileUtils.FilterWrapper[0], overwrite);
			}

			buildContext.refresh(to);
		} catch (IOException e) {
			throw new MavenFilteringException(e.getMessage(), e);
		}
	}

	protected void customFilterFile(@Nonnull File from, @Nonnull File to, @Nullable String encoding,
			@Nullable List<FilterWrapper> wrappers) throws IOException, MavenFilteringException {

		TemplateGenerator templateGenerator = new TemplateGenerator(from.getParentFile());
		String templateName = from.getName();
		
		GeneratedFile genFile;
		try {
			genFile = new GeneratedFile(templateGenerator.createFreemarkerConfiguration(), getTemplateMap(),
					templateName, to);
		} catch (IOException e) {
			throw new MavenFilteringException("error to generate from template", e);
		}
		try {
			genFile.generate();
		} catch (IOException | TemplateException e) {
			throw new MavenFilteringException("error to generate from template", e);
		}

	}

	public Map<String, Object> getTemplateMap() {
		return templateMap;
	}

	public void setTemplateMap(Map<String, Object> templateMap) {
		this.templateMap = templateMap;
	}

	private void filterFile(@Nonnull File from, @Nonnull File to, @Nullable String encoding,
			@Nullable List<FilterWrapper> wrappers) throws IOException, MavenFilteringException {
		if (wrappers != null && wrappers.size() > 0) {
			Reader fileReader = null;
			Writer fileWriter = null;
			try {
				fileReader = getFileReader(encoding, from);
				fileWriter = getFileWriter(encoding, to);
				Reader src = readerFilter.filter(fileReader, true, wrappers);

				IOUtil.copy(src, fileWriter);
			} finally {
				IOUtil.close(fileReader);
				IOUtil.close(fileWriter);
			}
		} else {
			if (to.lastModified() < from.lastModified()) {
				FileUtils.copyFile(from, to);
			}
		}
	}

    private Writer getFileWriter( String encoding, File to )
            throws IOException
        {
            if ( StringUtils.isEmpty( encoding ) )
            {
                return new FileWriter( to );
            }
            else
            {
                FileOutputStream outstream = new FileOutputStream( to );

                return new OutputStreamWriter( outstream, encoding );
            }
        }

        private Reader getFileReader( String encoding, File from )
            throws FileNotFoundException, UnsupportedEncodingException
        {
            // buffer so it isn't reading a byte at a time!
            if ( StringUtils.isEmpty( encoding ) )
            {
                return new BufferedReader( new FileReader( from ) );
            }
            else
            {
                FileInputStream instream = new FileInputStream( from );
                return new BufferedReader( new InputStreamReader( instream, encoding ) );
            }
        }

}
