package com.mucommander.conf;

import java.util.*;
import java.io.*;

import com.mucommander.PlatformManager;


/**
 * Handles configuration.
 * <p>
 * When it is first accessed, this class will try to load the configuration file.
 * </p>
 * <p>
 * Once the configuration file has been loaded in memory, variables can be set or
 * retrieved through the {@link #getVariable(String)} and {@link #setVariable(String,String)}
 * methods.<br>
 * Configuration variable names follow the same syntax as the Java System properties. Each
 * variable is contained in a section, which can itself be contained in a section. Each section
 * name is separated by a '.' character. For example: <i>mucommander.someSection.someVariable</i> refers to
 * the <i>someVariable</i> variable found in the <i>someSection</i> section of <i>mucommander</i>.
 * </p>
 * <p>
 * It is possible to monitor configuration file changes with a system of listeners.<br>
 * Any class implementing the {com.mucommander.conf.ConfigurationListener} interface
 * can be registered through the {@link #addConfigurationListener(ConfigurationListener)}
 * method. It will then be warned as soon as a configuration variable has been modified.<br>
 * </p>
 * @author Nicolas Rinaudo, Maxence Bernard
 */
public class ConfigurationManager {

    /** Contains all the registered configuration listeners. */
    private static LinkedList listeners = new LinkedList();

    /** Name of the configuration file */
    private static final String CONFIGURATION_FILENAME = "preferences.xml";

    /** Instance of ConfigurationManager used to enfore configuration file loading. */
    private static ConfigurationManager singleton = new ConfigurationManager();

    /** Holds the content of the configuration file. */
    private static ConfigurationTree tree;


    /* ------------------------ */
    /*      Initialisation      */
    /* ------------------------ */
    /**
     * Constructor used to ensure that the configuration file is loaded at boot time.
     */
    private ConfigurationManager() {
        tree = new ConfigurationTree("root");
        
		// Load configuration
		loadConfiguration();
		
        // Sets muCommander version corresponding to this configuration file
        setVariable("prefs.conf_version", com.mucommander.Launcher.MUCOMMANDER_VERSION);
    }

    /* ------------------------ */
    /*       File handling      */
    /* ------------------------ */

    /**
     * Returns the path to the configuration file on the current platform.
     */
    private static String getConfigurationFilePath() {
        return new File(PlatformManager.getPreferencesFolder(), CONFIGURATION_FILENAME).getAbsolutePath();
    }
	
    /**
     * Returns the generic path to the configuration file.
     */
    private static String getGenericConfigurationFilePath() {
        return new File(PlatformManager.getGenericPreferencesFolder(), CONFIGURATION_FILENAME).getAbsolutePath();
    }	

	
    /**
     * Loads the specified configuration file in memory.
     * @param path path to the configuration file to load in memory.
     */
    private static synchronized void loadConfiguration(String path) throws Exception {
        ConfigurationParser parser = new ConfigurationParser(new ConfigurationLoader());
        parser.parse(path);
    }

	
    /**
     * Loads the configuration file in memory.
     */
    public static synchronized boolean loadConfiguration() {
		
		// under Mac OS X, since v0.6 : try to open preferences file from ~/Library/muCommander/
		// and if it failed, try to open file from ~/.mucommander/

		try {
			loadConfiguration(getConfigurationFilePath());
			if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("Found and loaded configuration file: "+getConfigurationFilePath());						
			return true;
		}
		catch(Exception e) {
			if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("No configuration file found at "+getConfigurationFilePath());			
		}

		if(PlatformManager.getOSFamily()==PlatformManager.MAC_OS_X) {
			try {
				loadConfiguration(getGenericConfigurationFilePath());
				if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("Found and loaded configuration file: "+getGenericConfigurationFilePath());						
				return true;
			}
			catch(Exception e) {
				if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("No configuration file found at "+getGenericConfigurationFilePath());			
			}
		}
		
		return false;
	}

	
    /**
     * Writes the configuration to the configuration file.
     */
    public static synchronized void writeConfiguration() {
        PrintWriter out = null;
		try {
			ConfigurationWriter writer = new ConfigurationWriter();
			String filePath = getConfigurationFilePath();
			if(com.mucommander.Debug.ON) com.mucommander.Debug.trace("Writing preferences file: "+filePath);						
			
			// Use UTF-8 encoding
			out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"));
			writer.writeXML(out);
		}
		catch(IOException e) {
			// Notify user that preferences file could not be written
			System.out.println("muCommander was unable to write preferences file: "+e);
		}
		finally {
			if(out!=null)
				out.close();
		}
    }

		
    /* ------------------------ */
    /*      Variable access     */
    /* ------------------------ */
    /**
     * Builds a configuration tree starting at the specified node.
     * @param builder builder used to create the tree.
     * @param node    root of the tree.
     */
    private static void buildConfigurationTree(ConfigurationTreeBuilder builder, ConfigurationTree node) {
        Iterator          iterator;
        ConfigurationLeaf leaf;

        builder.addNode(node.getName());
        iterator = node.getLeafs();
        while(iterator.hasNext()) {
            leaf = (ConfigurationLeaf)iterator.next();
            builder.addLeaf(leaf.getName(), leaf.getValue());
        }
        iterator = node.getNodes();
        while(iterator.hasNext())
            buildConfigurationTree(builder, (ConfigurationTree)iterator.next());
        builder.closeNode(node.getName());
    }

    /**
     * Builds a configuration tree.
     * @param builder builder used to create the tree.
     */
    public static synchronized void buildConfigurationTree(ConfigurationTreeBuilder builder) {buildConfigurationTree(builder, tree);}


	/**
	 * Returns true if the given variable has a value (not null and not equals to "" after being trimmed).
	 * @param var the name of the variable to test.
	 */
	public static boolean isVariableSet(String var) {
		String value = getVariable(var);
		return value!=null && !value.trim().equals("");
	}


    /**
     * Retrieves the value of the specified configuration variable.
     * @param  var name of the variable to retrieve.
     * @return the value of the specified configuration variable.
     */
    public static synchronized String getVariable(String var) {
        StringTokenizer   parser;
        ConfigurationTree node;
        String            buffer;

        parser = new StringTokenizer(var, ".");
        node   = tree;
        while(parser.hasMoreTokens()) {
            buffer = parser.nextToken();
            if(parser.hasMoreTokens()) {
                if((node = node.getNode(buffer)) == null)
                    return null;
            }
            else
                return node.getLeaf(buffer);
        }
        return null;
    }

	
    /**
     * Retrieves the value of the specified configuration variable and assigns it
	 * a given default value if the the value returned by {@link #getVariable(String)} is
	 * <code>null</code>.
     *
	 * @param var name of the variable to retrieve.
     * @param defaultValue defaultValue assigned if the variable's value is <code>null</code>.
	 *
	 * @return the value of the specified configuration variable.
     */
    public static synchronized String getVariable(String var, String defaultValue) {
    	String value = getVariable(var);
		
		if (value==null) {
			setVariable(var, defaultValue);
			return defaultValue;
		}
		return value;
	}

	
	/**
	 * Returns the value of the given configuration variable, <code>-1</code>
	 * if the variable has no value OR if the variable cannot be parsed as an integer.
	 *
	 * @param  var name of the variable to retrieve.
     * @return the value of the specified configuration variable.
	 */
	public static synchronized int getVariableInt(String var) {
		String val = getVariable(var);
		if(val==null)
			return -1;
		
		try {
			return Integer.parseInt(val);
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}


	/**
	 * Retrieves the value of the given configuration variable and assigns it
	 * a given default value if the the value returned by {@link #getVariable(String)} is
	 * <code>null</code>. 
	 *
	 * <p>Returns <code>-1</code> if the variable cannot be parsed as an integer.</p>
	 *
	 * @param  var name of the variable to retrieve.
     * @param defaultValue defaultValue assigned if the variable's value is <code>null</code>.
     * @return the value of the specified configuration variable.
	 */
	public static synchronized int getVariableInt(String var, int defaultValue) {
		try {
			return Integer.parseInt(getVariable(var, ""+defaultValue));
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}
	

    /**
     * Sets the value of the specified configuration variable.
     * @param var   name of the variable to set.
     * @param value value for the specified variable.
     */
    public static synchronized void setVariable(String var, String value) {

		StringTokenizer   parser;
        String            buffer;
        ConfigurationTree node;
        ConfigurationTree temporaryNode;
        String            oldValue;
        
		parser = new StringTokenizer(var, ".");
        node   = tree;

        while(parser.hasMoreTokens()) {
            buffer = parser.nextToken();
            if(parser.hasMoreTokens()) {
                if((temporaryNode = node.getNode(buffer)) == null)
                    node = node.createNode(buffer);
                else
                    node = temporaryNode;
            }
            else {
                oldValue = node.getLeaf(buffer);
				
				// Since 0.8 beta2: do nothing (return) if value hasn't changed
				if((oldValue==null && value==null) || (oldValue!=null && oldValue.equals(value)))
					return;
					
				if(node.setLeaf(buffer, value))
                    if(!fireConfigurationEvent(new ConfigurationEvent(var, value)))
                        node.setLeaf(buffer, oldValue);
            }
        }
    }

	
    /**
     * Sets the value of the specified configuration variable.
     * @param var   name of the variable to set.
     * @param value value for the specified variable.
     */
    public static synchronized void setVariableInt(String var, int value) {
		setVariable(var, ""+value);
	}
	
		
    /**
     * Adds the specified configuration listener to the list of registered listeners.
     * @param listener listener to insert in the list.
     */
    public static synchronized void addConfigurationListener(ConfigurationListener listener) {
		listeners.add(listener);
		if(com.mucommander.Debug.ON)
			com.mucommander.Debug.trace(listeners.size()+" listeners");
	}

    /**
     * Removes the specified configuration listener from the list of registered listeners.
     * @param listener listener to remove from the list.
     */
    public static synchronized void removeConfigurationListener(ConfigurationListener listener) {
		listeners.remove(listener);
		if(com.mucommander.Debug.ON)
			com.mucommander.Debug.trace(listeners.size()+" listeners");
	}

    /**
     * Notifies all the registered configuration listeners of a configuration change event.
     * @param  event describes the configuration change.
     * @return true if the change wasn't vetoed, false otherwise.
     */
    static synchronized boolean fireConfigurationEvent(ConfigurationEvent event) {
        Iterator iterator;

        iterator = listeners.iterator();
        while(iterator.hasNext())
            if(!((ConfigurationListener)iterator.next()).configurationChanged(event))
                return false;
        return true;
    }
}

