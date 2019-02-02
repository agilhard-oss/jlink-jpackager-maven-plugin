
package net.agilhard.maven.plugins.jpacktool.template;
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

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.IOException;

/**
 * Base class for generators.<br>
 */
public class AbstractGenerator {

  private File templateDirectory;

  /**
   * Sets the freemarker template directory.
   *
   * @param templateDirectory the template directory
   */
  public void setTemplateDirectory(File templateDirectory) {
    this.templateDirectory = templateDirectory;
  }

  /**
   * Creates the freemarker configuration.
   *
   * @return the config
   * @throws IOException if there's some trouble with the template directory
   */
  public Configuration createFreemarkerConfiguration() throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_28);
    cfg.setDirectoryForTemplateLoading(templateDirectory);
    cfg.setDefaultEncoding("UTF-8");
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(false);
    cfg.setWrapUncheckedExceptions(true);
    return cfg;
  }

}
