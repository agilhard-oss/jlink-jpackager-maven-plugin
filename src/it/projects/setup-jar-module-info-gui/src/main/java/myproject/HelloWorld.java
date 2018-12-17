package myproject;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Just another Hello World App.
 */
public class HelloWorld
{

	JFrame frame;
	
	HelloWorld( String[] args ) {
        // Invoked on the event dispatching thread.
        // Do any initialization here.
		frame = new JFrame( "Hello World" );
		frame.add( new JLabel( "Hello World!", JLabel.CENTER ) );
		frame.setSize(new Dimension(640,480));
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    public void show()
    {
    	frame.setVisible( true );
    }

    public static void main( final String[] args )
    {
        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        SwingUtilities.invokeLater(new Runnable() 
        {
            public void run() 
            {
                new HelloWorld( args ).show();
            }
        });
    }
}