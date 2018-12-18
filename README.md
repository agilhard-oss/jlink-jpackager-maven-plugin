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

Alternatively you can also use the JDK-11 backported Jpackager tool wich is mentioned in
[Filling the Packager gap - OpenJDK mailing list - Java.net](http://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html)

Note: This is just a first SNAPSHOT not all jpackager goal Configuration Options are fully working yet.


