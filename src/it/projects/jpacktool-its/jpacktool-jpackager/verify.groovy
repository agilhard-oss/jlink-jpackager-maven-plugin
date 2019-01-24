
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

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.codehaus.plexus.util.*;

boolean result = true;

try
{
    File target = new File( basedir, "target" );
    if ( !target.exists() || !target.isDirectory() )
    {
        System.err.println( "target file is missing or not a directory." );
        return false;
    }
    File out = new File( target, "maven-jpackager-out" );
    if ( !out.exists() || !out.isDirectory() )
    {
        System.err.println( "maven-jpackager-out file is missing or not a directory." );
        return false;
    }


    File artifact;

    if ( Os.isName( "Linux" ) )
    {
        artifact  = new File( out, "jpacktool-its-jpacktool-jpackager-99.0-1.x86_64.rpm" );
        if ( !artifact.exists() || artifact.isDirectory() )
        {
            System.err.println( "jpacktool-its-jpacktool-jpackager-99.0-1.x86_64.rpm file is missing or is a directory." );
            return false;
        }
        artifact  = new File( out, "jpacktool-its-jpacktool-jpackager-99.0.deb" );
        if ( !artifact.exists() || artifact.isDirectory() )
        {
            System.err.println( "jpacktool-its-jpacktool-jpackager-99.0.deb file is missing or is a directory." );
            return false;
        }
    } 
    else if ( Os.isName( "Windows" ) )
    {
        artifact  = new File( out, "jpacktool-its-jpacktool-jpackager-99.0.msi" );
        if ( !artifact.exists() || artifact.isDirectory() )
        {
            System.err.println( "jpacktool-its-jpacktool-jpackager-99.0.msi file is missing or is a directory." );
            return false;
        }
    }
    else if ( Os.isName( "Mac" ) )
    {
        artifact  = new File( out, "jpacktool-its-jpacktool-jpackager-99.0.dmg" );
        if ( !artifact.exists() || artifact.isDirectory() )
        {
            System.err.println( "jpacktool-its-jpacktool-jpackager-99.0.dmg file is missing or is a directory." );
            return false;
        }
    }
}
catch( Throwable e )
{
    e.printStackTrace();
    result = false;
}

return result;
