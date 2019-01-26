package net.agilhard.maven.plugins.jpacktool;

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

import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.apache.maven.plugin.logging.Log;

public class ExecuteCommand {

    private ExecuteCommand() {
        // private constructor for utility class
    }

    protected static void executeCommand(final boolean verbose, final Log log, final Commandline cmd, OutputStream outputStream) throws MojoExecutionException {
        if ( log.isDebugEnabled() )
        {
            // no quoted arguments ???
            log.debug( CommandLineUtils.toString( cmd.getCommandline() ).replaceAll( "'", "" ) );
        }

        final PrintStream printStream = new PrintStream(outputStream);
        
        final CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
        final CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
        try
        {
            final int exitCode = CommandLineUtils.executeCommandLine( cmd, out, err );

            final String output = ( StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim() );

            if ( StringUtils.isNotEmpty( output ) )
            {
                for ( final String outputLine : output.split( "\\n" ) )
                {
                    if ( outputLine.length() > 0) {
                        printStream.println(outputLine);
                        if ( verbose ) { 
                            log.info( outputLine );
                        }
                    }
                }
            }
            printStream.close();
            
            if ( exitCode != 0 )
            {    
                final StringBuilder msg = new StringBuilder( "\nExit code: " );
                msg.append( exitCode );
                if ( StringUtils.isNotEmpty( err.getOutput() ) )
                {
                    msg.append( " - " ).append( err.getOutput() );
                }
                msg.append( '\n' );
                msg.append( "Command line was: " ).append( cmd ).append( '\n' ).append( '\n' );

                throw new MojoExecutionException( msg.toString() );
            }

        }
        catch ( final CommandLineException e )
        {
            throw new MojoExecutionException( "Unable to execute command: " + e.getMessage(), e );
        }

    }

    protected static void executeCommand(final boolean verbose, final Log log, final Commandline cmd) throws MojoExecutionException {
        if ( log.isDebugEnabled() )
        {
            // no quoted arguments ???
            log.debug( CommandLineUtils.toString( cmd.getCommandline() ).replaceAll( "'", "" ) );
        }

        final CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
        final CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
        try
        {
            final int exitCode = CommandLineUtils.executeCommandLine( cmd, out, err );

            final String output = ( StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim() );

            if ( exitCode != 0 )
            {

                if ( StringUtils.isNotEmpty( output ) )
                {
                    // Reconsider to use WARN / ERROR ?
                   //  getLog().error( output );
                    for ( final String outputLine : output.split( "\\n" ) )
                    {
                        log.error( outputLine );
                    }
                }

                final StringBuilder msg = new StringBuilder( "\nExit code: " );
                msg.append( exitCode );
                if ( StringUtils.isNotEmpty( err.getOutput() ) )
                {
                    msg.append( " - " ).append( err.getOutput() );
                }
                msg.append( '\n' );
                msg.append( "Command line was: " ).append( cmd ).append( '\n' ).append( '\n' );

                throw new MojoExecutionException( msg.toString() );
            }

            if ( StringUtils.isNotEmpty( output ) && verbose )
            {
                //getLog().info( output );
                for ( final String outputLine : output.split( "\n" ) )
                {
                    log.info( outputLine );
                }
            }
        }
        catch ( final CommandLineException e )
        {
            throw new MojoExecutionException( "Unable to execute command: " + e.getMessage(), e );
        }

    }

}
