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

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Invokes xgettext to extract messages from source code and store them in the keys.pot file.
 *
 * @goal gettext
 * @phase generate-resources
 */
public class GettextMojo
        extends AbstractGettextMojo {

    /**
     * The encoding of the source Java files. utf-8 is a superset of ascii.
     *
     * @parameter property="encoding" default-value="utf-8"
     */
    protected String encoding;

    /**
     * The keywords the xgettext parser will look for to extract messages. The default value works
     * with the Gettext Commons library.
     *
     * @parameter property="keywords" default-value="-ktrc:1c,2 -ktrnc:1c,2,3 -ktr -kmarktr
     * -ktrn:1,2 -k"
     * @required
     */
    protected String keywords;

    /**
     * The xgettext command.
     *
     * @parameter property="xgettextCmd" default-value="xgettext"
     * @required
     */
    protected String xgettextCmd;

    /**
     * Sort extracted messages, can be "output" or "by-file"
     *
     * @parameter property="sort" default-value="by-file"
     * @required
     */
    protected String sort;

    /**
     * Do not break long message lines, longer than the output page width, into several lines
     *
     * @parameter property="nowrap" default-value="false"
     * @required
     */
    protected boolean nowrap;

    /**
     * An optional set of source files that should be parsed with xgettext.
     * <pre>
     * {@code
     * <extraSourceFiles>
     *   <directory>$basedir</directory>
     *   <includes>
     *      <include>** /*.jsp</include>
     *    </includes>
     *    <excludes>
     *      <exclude>** /*.txt</exclude>
     *    </excludes>
     * </extraSourceFiles>
     * }
     * </pre>
     *
     * @parameter property="extraSourceFiles"
     */
    protected FileSet extraSourceFiles;

    public void execute()
            throws MojoExecutionException {
        getLog().info("Invoking xgettext for Java files in '"
                + sourceDirectory.getAbsolutePath() + "'.");

        Commandline cl = new Commandline();
        cl.setExecutable(xgettextCmd);
        for (String arg : extraArgs) {
            cl.createArg().setValue(arg);
        }
        cl.createArg().setValue("--from-code=" + encoding);
        File messagesPotFile = new File(poDirectory, keysFile);
        cl.createArg().setValue("--output=" + messagesPotFile.getAbsolutePath());
        cl.createArg().setValue("--language=Java");
        if (nowrap) {
            cl.createArg().setValue("--no-wrap");
        }
        cl.createArg().setLine(keywords);
        cl.createArg().setValue("by-file".equalsIgnoreCase(sort) ? "-F" : "-s");
        cl.setWorkingDirectory(sourceDirectory.getAbsolutePath());

        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(sourceDirectory);
        if (includes != null && includes.length > 0) {
            ds.setIncludes(includes);
        } else {
            ds.setIncludes(new String[]{"**/*.java"});
        }
        if (excludes != null && excludes.length > 0) {
            ds.setExcludes(excludes);
        }
        ds.scan();
        String[] files = ds.getIncludedFiles();
        List fileNameList = Collections.emptyList();
        if (extraSourceFiles.getDirectory() != null) {
            try {
                fileNameList = FileUtils.getFileNames(new File(extraSourceFiles.getDirectory()),
                        StringUtils.join(extraSourceFiles.getIncludes().iterator(), ","),
                        StringUtils.join(extraSourceFiles.getExcludes().iterator(), ","), false);
            } catch (IOException e) {
                throw new MojoExecutionException("error finding extra source files", e);
            }
        }

        File file = createListFile(files, fileNameList);
        if (file != null) {
            cl.createArg().setValue("--files-from=" + file.getAbsolutePath());
        } else {
            for (int i = 0; i < files.length; i++) {
                cl.createArg().setValue(getAbsolutePath(files[i]));
            }
        }

        getLog().debug("Executing: " + cl.toString());
        StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
        StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
        try {
            CommandLineUtils.executeCommandLine(cl, out, err);
        } catch (CommandLineException e) {
            getLog().error("Could not execute " + xgettextCmd + ".", e);
        }
        if (!printPOTCreationDate) {
            GettextUtils.removePotCreationDate(messagesPotFile, getLog());
        }
    }

    private File createListFile(String[] files, List fileList) {
        try {
            File listFile = File.createTempFile("maven", null);
            listFile.deleteOnExit();

            BufferedWriter writer = new BufferedWriter(new FileWriter(listFile));
            try {
                for (int i = 0; i < files.length; i++) {
                    writer.write(toUnixPath(files[i]));
                    writer.newLine();
                }
                for (Iterator i = fileList.iterator(); i.hasNext(); ) {
                    writer.write(toUnixPath((String) i.next()));
                    writer.newLine();
                }
            } finally {
                writer.close();
            }

            return listFile;
        } catch (IOException e) {
            getLog().error("Could not create list file.", e);
            return null;
        }
    }

    private String getAbsolutePath(String path) {
        return sourceDirectory.getAbsolutePath() + File.separator + path;
    }

    private String toUnixPath(String path) {
        if (File.separatorChar != '/') {
            return path.replace(File.separatorChar, '/');
        }
        return path;
    }

}
