package org.xnap.commons.maven.gettext;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;

/**
 * Invokes the gettext:gettext goal and invokes msgattrib to update po files.
 *
 * @goal attrib
 * @phase generate-resources
 * @author Tammo van Lessen
 */
public class AttribMojo
    extends AbstractGettextMojo {
	
    /**
     * The msgattrib command.
     * @parameter property="msgattribCmd" default-value="msgattrib"
     * @required 
     */
    protected String msgattribCmd;
    
    public void execute()
        throws MojoExecutionException
    {
		getLog().info("Invoking msgattrib for po files in '"
				+ poDirectory.getAbsolutePath() + "'.");
		
		DirectoryScanner ds = new DirectoryScanner();
    	ds.setBasedir(poDirectory);
		if (includes != null && includes.length > 0) {
			ds.setIncludes(includes);
		} else {
			ds.setIncludes(new String[]{"**/*.po"});
		}
		if (excludes != null && excludes.length > 0) {
			ds.setExcludes(excludes);
		}
    	ds.scan();
    	String[] files = ds.getIncludedFiles();
    	for (int i = 0; i < files.length; i++) {
    		getLog().info("Processing " + files[i]);
    		Commandline cl = new Commandline();
    		cl.setExecutable(msgattribCmd);
			for (String arg : extraArgs) {
    			cl.createArg().setValue(arg);
			}
        	cl.createArg().setValue("-o");
        	cl.createArg().setFile(new File(poDirectory, files[i]));
        	cl.createArg().setFile(new File(poDirectory, files[i]));
        	
        	getLog().debug("Executing: " + cl.toString());
    		StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
    		StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
        	try {
    			CommandLineUtils.executeCommandLine(cl, out, err);
    		} catch (CommandLineException e) {
    			getLog().error("Could not execute " + msgattribCmd + ".", e);
    		}
    		if (!printPOTCreationDate) {
    			GettextUtils.removePotCreationDate(new File(poDirectory, files[i]), getLog());
    		}
    	}
    }
}
