/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2007 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.job;

import com.mucommander.Debug;
import com.mucommander.PlatformManager;
import com.mucommander.file.AbstractFile;
import com.mucommander.file.filter.AttributeFileFilter;
import com.mucommander.file.filter.EqualsFilenameFilter;
import com.mucommander.file.filter.ExtensionFilenameFilter;
import com.mucommander.file.filter.OrFileFilter;
import com.mucommander.file.util.FileSet;
import com.mucommander.file.util.ResourceLoader;
import com.mucommander.process.ProcessRunner;
import com.mucommander.text.Translator;
import com.mucommander.ui.dialog.file.FileCollisionDialog;
import com.mucommander.ui.dialog.file.ProgressDialog;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.main.WindowManager;
import com.mucommander.util.StringUtils;

import java.io.IOException;

/**
 * This job self-updates the muCommmander with a new JAR file that is fetched from a specified remote file.
 * The update process boils down to the following steps:
 * <ul>
 *  <li>First, all classes of the existing JAR files are loaded in memory, to ensure that the shutdown sequence has all
 * the classes it needs, including those that haven't been used yet.</li>
 *  <li>The new JAR file is downloaded (using {@link CopyJob}) to a temporary location</li>
 *  <li>The new JAR file is moved from the temporary location to the main application JAR file, overwriting the previous
 * JAR file.
 *  <li>muCommander is restarted: this involves starting a new application instance and shutting down the current one.
 *  The specifics of this step are platform-dependant.</li>
 * </ul>
 *
 * @author Maxence Bernard
 */
public class SelfUpdateJob extends CopyJob {

    /** The JAR file to be updated */
    private AbstractFile destJarFile;

    /** The ClassLoader to use for loading all classes from the JAR file */
    private ClassLoader classLoader;

    /** Filters directories and class files, used for loading classes from the JAR file */
    private OrFileFilter directoryOrClassFileFilter;

    /** True if classes haven't been loaded yet */ 
    private boolean loadingClasses =  true;


    public SelfUpdateJob(ProgressDialog progressDialog, MainFrame mainFrame, AbstractFile remoteJarFile) {
        this(progressDialog, mainFrame, new FileSet(remoteJarFile.getParentSilently(), remoteJarFile), getDestJarFile());
    }

    private SelfUpdateJob(ProgressDialog progressDialog, MainFrame mainFrame, FileSet files, AbstractFile destJarFile) {
        // Todo: copy to temp first, then to JAR
        super(progressDialog, mainFrame, files, destJarFile.getParentSilently(), destJarFile.getName(), CopyJob.DOWNLOAD_MODE, FileCollisionDialog.OVERWRITE_ACTION);

        this.destJarFile = destJarFile;
        this.classLoader = getClass().getClassLoader();

        directoryOrClassFileFilter = new OrFileFilter();
        directoryOrClassFileFilter.addFileFilter(new AttributeFileFilter(AttributeFileFilter.DIRECTORY));
        directoryOrClassFileFilter.addFileFilter(new ExtensionFilenameFilter(".class"));
    }

    /**
     * Returns the JAR file to update.
     *
     * @return the JAR file to update
     */
    private static AbstractFile getDestJarFile() {
        return ResourceLoader.getRootPackageAsFile(SelfUpdateJob.class);
    }

    /**
     * Loads all the class files contained in the given JAR file recursively.
     *
     * @param file the JAR file from which to load the classes
     * @throws Exception if an error occurred while loading the classes
     */
    private void loadClassRecurse(AbstractFile file) throws Exception {
        if(file.isBrowsable()) {
            AbstractFile[] children = file.ls(directoryOrClassFileFilter);
            for(int i=0; i<children.length; i++)
                loadClassRecurse(children[i]);
        }
        else {          // .class file

            String classname = file.getAbsolutePath(false);
            // Strip off the JAR file's path and ".class" extension
            classname = classname.substring(destJarFile.getAbsolutePath(true).length(), classname.length()-6);
            // Replace separator characters by '.'
            classname = StringUtils.replaceCompat(classname, destJarFile.getSeparator(), ".");
            // We now have a class name, e.g. "com.mucommander.Launcher"

            try {
                classLoader.loadClass(classname);

                if(Debug.ON) Debug.trace("Loaded class "+classname);
            }
            catch(java.lang.NoClassDefFoundError e) {
                if(Debug.ON) Debug.trace("Caught an error while loading class "+classname+" : "+e);
            }
        }
    }


    ////////////////////////
    // Overridden methods //
    ////////////////////////

    public String getStatusString() {
        if(loadingClasses) {
            // Todo: localize me
            return Translator.get("Preparing install...");
        }

        return super.getStatusString();
    }

    protected void jobStarted() {
        super.jobStarted();

        try {
            // Loads all classes from the JAR file before the new JAR file is installed.
            // This will ensure that the shutdown sequence, which invokes so not-yet-loaded classes goes down smoothly.
            loadClassRecurse(destJarFile);
            loadingClasses = false;
        }
        catch(Exception e) {
            if(Debug.ON) Debug.trace("Caught exception: "+e);

            // Todo: display an error message
            interrupt();
        }
    }

    protected void jobCompleted() {
        try {
            AbstractFile parent;
            // Mac OS X
            if(PlatformManager.getOsFamily()==PlatformManager.MAC_OS_X) {
                parent = destJarFile.getParent();

                // Find the .app container that encloses the JAR file
                if(parent.getName().equals("Java")
                &&(parent=parent.getParent())!=null && parent.getName().equals("Resources")
                &&(parent=parent.getParent())!=null && parent.getName().equals("Contents")
                &&(parent=parent.getParent())!=null && "app".equals(parent.getExtension())) {

                    if(Debug.ON) Debug.trace("Executing open "+parent.getAbsolutePath());

                    String appPath = parent.getAbsolutePath();
                    // Open -W wait for the current muCommander .app to terminate, before re-opening it
                    ProcessRunner.execute(new String[]{"/bin/sh", "-c", "open -W "+appPath+" && open "+appPath});

                    return;
                }
            }
            // Windows
            else if(PlatformManager.isWindowsFamily()) {
                parent = destJarFile.getParent();

                // Find the muCommander.exe launcher located in the same folder as the JAR file
                EqualsFilenameFilter exeFilter = new EqualsFilenameFilter("muCommander.exe", false);
                AbstractFile[] exeFile = parent.ls(exeFilter);

                if(exeFile!=null && exeFile.length==1) {
                    PlatformManager.open(exeFile[0]);

                    return;
                }
            }

            // Todo: preserve KDE/Gnome desktop properties and other properties
            ProcessRunner.execute(new String[]{"java", "-jar", destJarFile.getAbsolutePath()});
        }
        catch(IOException e) {
            if(Debug.ON) Debug.trace("Caught exception: "+e);
            // Todo: we might want to do something about this
        }
        finally {
            WindowManager.quit();
        }
    }
}