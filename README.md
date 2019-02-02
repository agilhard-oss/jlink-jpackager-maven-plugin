<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# jlink-jpackager-maven-plugin

Combined maven plugin to call the new jlink and jpackager tools from maven.

Based on Apache [maven-jlink-plugin](https://github.com/apache/maven-jlink-plugin)

[Maven Plugin Documentation](https://agilhard-oss.github.io/jlink-jpackager-maven-plugin/site/index.html)

## Introduction

  The JLink-JPackager Maven plugin is intended to create [Modular Run-Time Images](http://openjdk.java.net/jeps/220) with **JDK 9**
  and above or native installable packages via [jpackage](http://openjdk.java.net/jeps/343) with **JDK 12** and above.
  
  Although the jlink and jpackage tools have some limitations in regards to using non module jars 
  and automatic module jars the JLink-JPackager Maven plugin
  can create ZIP files for [Modular Run-Time Images](http://openjdk.java.net/jeps/220)
  or package installers with Java Runtimes and mixed real and automatic modules and non modular jars.

  It does this by analyzing java module depdencies of all maven dependencies using the **jdeps** java tool and modifying command line parameters
  for the **jlink** the **jpackage** and the **java** executable based on the findings.
  
  NOTE: This is an alpha release which means everything can change until we reach the first
  milestone release.

  The JLink-JPackager Maven Plugin is available on GitHub: [jlink-jpackager-maven-plugin](https://github.com/agilhard-oss/jlink-jpackager-maven-plugin)

## Usage of the JLink-JPackager Maven Plugin

Usually you will use the Maven JLink-JPackager Maven Plugin to create
a Run Time Image or an installable Package from one or more modules within 
a multi module build.

In other words it is not possible to create a Run Time Image or Installation Package
from a single Maven Project within the same single Maven Project.


## jlink goal

The JLink goal is intended to create a Java Run Time Image file based on
[http://openjdk.java.net/jeps/282](http://openjdk.java.net/jeps/282),
[http://openjdk.java.net/jeps/220](http://openjdk.java.net/jeps/220).
  

You need at least Java-9 to use this goal.

 
## jpackager goal

The JPackager goal is intended to create a native installer package file based on
[http://openjdk.java.net/jeps/343](http://openjdk.java.net/jeps/343).

You need to use the special JDK-12 Early Access build that includes JPackager support to use this goal.

This JPackager JDK-12 Early Access build can be downloaded from 
[https://jdk.java.net/jpackage/](https://jdk.java.net/jpackage/)

Alternatively you can also use the JDK-11 backported JPackager tool wich is mentioned in
[Filling the Packager gap - OpenJDK mailing list - Java.net](http://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html)

Note: This is just a first SNAPSHOT not all jpackager goal Configuration Options are fully working yet.

## Prerequisites

- [JDK](http://jdk.java.net/)
- [Maven](https://maven.apache.org/)

You need to use the special JDK-12 Early Access build that includes JPackager support to use this example.

This JPackager JDK-12 Early Access build can be downloaded from 
[https://jdk.java.net/jpackage/](https://jdk.java.net/jpackage/)

Alternatively you can also use the JDK-11 backported JPackager tool wich is mentioned in
[Filling the Packager gap - OpenJDK mailing list - Java.net](http://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html)

The [link-jpackager-maven-plugin](https://github.com/agilhard-oss/jlink-jpackager-maven-plugin) is not (yet?)
available on maven central you must download,
compile and install that to your maven Repository before you can use this example.

Native packages will be generated using tools on the target platform. 

For Linux and Mac make sure you have the packaging tools for the used packaging type installed.

For Windows, there are two additional tools that developers will need to install if they want to generate native packages:

- exe — Inno Setup, a third-party tool, is required to generate exe installers
- msi — Wix, a third-party tool, is required to generate msi installers

[Inno Setup](http://www.jrsoftware.org/isinfo.php)
[Inno Setup Download](http://www.jrsoftware.org/isdl.php)

[Wix Toolset](http://wixtoolset.org)
[Wix Toolset Downloads](http://wixtoolset.org/releases/)



## Examples

Example for jlink-jpackager-maven-plugin how to build a distroless docker image using the Java jlink tool:

[jlink-distroless-maven-example](https://github.com/agilhard-oss/jlink-distroless-maven-example)


Minimalistic Example for jlink-jpackager-maven-plugin how to build a installable package using the Java jpackage tool:

[jpackager-maven-example](https://github.com/agilhard-oss/jpackager-maven-example)
