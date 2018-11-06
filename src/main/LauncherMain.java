/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import gui.GuiControls;
import gui.GuiControls.FrameSize;
import gui.GuiControls.InputControl;
import logging.FontInfo;
import logging.Logger;
import panels.BytecodeViewer;
import panels.DatabaseTable;
import panels.DebugLogger;
import panels.ParamTable;
import panels.SymbolTable;
import callgraph.CallGraph;
import util.CommandLauncher;
import util.ThreadLauncher;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author dmcd2356
 */
public final class LauncherMain {

  private static final String NEWLINE = System.getProperty("line.separator");
  private static final PrintStream STANDARD_OUT = System.out;
  private static final PrintStream STANDARD_ERR = System.err;
  
  // locations to store output files created in the project dir
  private static final String PROJ_CONFIG = ".danlauncher";
  private static final String CLASSFILE_STORAGE = "classes";
  private static final String JAVAPFILE_STORAGE = "javap";

  // default location for jre for running instrumented project
  private static final String JAVA_HOME = "/usr/lib/jvm/java-8-oracle"; // /usr/lib/jvm/java-8-openjdk-amd64";

  // location of danalyzer program
  private static final String DSEPATH = "/home/dan/Projects/isstac/dse/";
  
  // location of this program
  private static final String HOMEPATH = System.getProperty("user.dir");

  // Properties that are specific to the danlauncher system
  private enum SystemProperties { 
    JAVA_HOME,        // location of jvm path used for running instrumented jar
    PROJECT_PATH,     // last project path location (to start location to look for jar file to load)
    MAX_LOG_LENGTH,   // max log file length to maintain in debugLogger
    DEBUG_PORT,       // the port to receive debug info from the application
  }
  
  // Properties that are project-specific
  private enum ProjectProperties { 
    RUN_ARGUMENTS,    // the argument list for the application to run
    MAIN_CLASS,       // the Main Class for the application
    APP_SERVER_PORT,  // the server port to send messages to in the application
    IS_SERVER_TYPE,   // whether the application is a stand-alone or server type
  }
  
  private enum ElapsedMode { OFF, RUN, RESET }

  // tab panel selections
  private enum PanelTabs { COMMAND, DATABASE, BYTECODE, BYTEFLOW, LOG, CALLGRAPH }
  
  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

  private static final GuiControls mainFrame = new GuiControls();
  private static final GuiControls graphSetupFrame = new GuiControls();
  private static JTabbedPane     tabPanel;
  private static JFileChooser    fileSelector;
  private static JComboBox       mainClassCombo;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static Logger          commandLogger;
  private static BytecodeViewer  bytecodeLogger;
  private static DebugLogger     debugLogger;
  private static Timer           debugMsgTimer;
  private static Timer           graphTimer;
  private static long            elapsedStart;
  private static ElapsedMode     elapsedMode;
  private static GraphHighlight  graphMode;
  private static String          projectPathName;
  private static String          projectName;
  private static String          debugPort;
  private static int             tabIndex = 0;
  private static ThreadLauncher  threadLauncher;
  private static DatabaseTable   dbtable;
  private static ParamTable      localVarTbl;
  private static SymbolTable     symbolTbl;
  private static String          inputAttempt = "";
  private static DefaultListModel solutionList;
  private static Visitor         makeConnection;
  private static String          clientPort = "";
  private static NetworkServer   udpThread = null;
  private static NetworkListener networkListener = null;
  private static DebugInputListener inputListener;
  private static String          javaHome;
  private static boolean         mainClassInitializing;
  private static boolean         isServerType = true;
  private static JCheckBoxMenuItem isServerTypeMenuItem;
  
  private static ArrayList<String> fullMethList = new ArrayList<>();
  private static ArrayList<String> classList = new ArrayList<>();
  private static HashMap<String, ArrayList<String>>  clsMethMap = new HashMap<>(); // maps the list of methods to each class
  private static final HashMap<PanelTabs, Integer>   tabSelect = new HashMap<>();
  private static final HashMap<String, FontInfo>     bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo>     debugFontTbl = new HashMap<>();
  private static final HashMap<PanelTabs, Component> tabbedPanels = new HashMap<>();
  private static final HashMap<String, Long>         solutionMap = new HashMap<>();
  
  // configuration file settings
  private static PropertiesFile  systemProps;   // this is for the generic properties for the user
  private static PropertiesFile  projectProps;  // this is for the project-specific properties

  // this defines the project properties that have corresponding user controls
  private static final PropertiesTable[] PROJ_PROP_TBL = {
    new PropertiesTable (ProjectProperties.RUN_ARGUMENTS  , InputControl.TextField, "TXT_ARGLIST"),
    new PropertiesTable (ProjectProperties.APP_SERVER_PORT, InputControl.TextField, "TXT_PORT"),
    new PropertiesTable (ProjectProperties.MAIN_CLASS     , InputControl.ComboBox , "COMBO_MAINCLS"),
  };

  private static class PropertiesTable {
    public ProjectProperties tag;         // the tag used to access the Properties entry
    public InputControl      controlType; // the type of user-input widget
    public String            controlName; // name of the widget corresponding to this property
    
    public PropertiesTable(ProjectProperties propname, InputControl type, String name) {
      tag = propname;
      controlType = type;
      controlName = name;
    }
  }
  
  // allow ServerThread to indicate on panel when a connection has been made for TCP
  public interface Visitor {
    void showConnection(String connection);
    void resetConnection();
  }

  /**
   * @param args the command line arguments
   * @throws java.io.IOException
   */
  public static void main(String[] args) throws IOException {
    // start the debug message panel
    LauncherMain gui = new LauncherMain();
  }

  public LauncherMain() {
    makeConnection = new Visitor() {
      @Override
      public void showConnection(String connection) {
        clientPort = connection;
        printCommandMessage("connected to  " + clientPort);
      }

      @Override
      public void resetConnection() {
        printCommandMessage("connected to  " + clientPort + "  (CONNECTION CLOSED)");
      }
    };

    classList = new ArrayList<>();
    clsMethMap = new HashMap<>();
    elapsedStart = 0;
    elapsedMode = ElapsedMode.OFF;
    graphMode = GraphHighlight.NONE;
    mainClassInitializing = false;

    // check for the global danlauncher properties file and init default values if not found
    systemProps = new PropertiesFile(HOMEPATH + "/" + PROJ_CONFIG, "SYSTEM_PROPERTIES");
    String projectPath = systemProps.getPropertiesItem(SystemProperties.PROJECT_PATH.toString(), HOMEPATH);
    String maxLogLength = systemProps.getPropertiesItem(SystemProperties.MAX_LOG_LENGTH.toString(), "500000");
    debugPort = systemProps.getPropertiesItem(SystemProperties.DEBUG_PORT.toString(), "5000");
    javaHome = systemProps.getPropertiesItem(SystemProperties.JAVA_HOME.toString(), JAVA_HOME);
    
    // we need a filechooser and initialize it to the project path
    fileSelector = new JFileChooser();
    fileSelector.setCurrentDirectory(new File(projectPath));

    // create the main panel and controls
    createDebugPanel();

    if (debugLogger != null && !maxLogLength.isEmpty()) {
      debugLogger.setMaxBufferSize(Integer.parseUnsignedInt(maxLogLength));
    }

    // this creates a command launcher that can run on a separate thread
    threadLauncher = new ThreadLauncher((JTextArea) getTabPanel(PanelTabs.COMMAND));

    // create a timer for reading and displaying the messages received (from either network or file)
    inputListener = new DebugInputListener();
    debugMsgTimer = new Timer(1, inputListener);

    // create a slow timer for updating the call graph
    graphTimer = new Timer(1000, new GraphUpdateListener());

    // start timer when 1st line is received from port
    elapsedMode = ElapsedMode.RESET;
}

  public void createDebugPanel() {
    // if a panel already exists, close the old one
    if (mainFrame.isValidFrame()) {
      mainFrame.close();
    }

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // init the solutions tried to none
    solutionList = new DefaultListModel();
    
    // create the frame
    mainFrame.newFrame("danlauncher", 1200, 800, FrameSize.FULLSCREEN);

    panel = null; // this creates the entries in the main frame
    mainFrame.makePanel      (panel, "PNL_MESSAGES" , "Status"    , LEFT, true);
    mainFrame.makePanel      (panel, "PNL_CONTAINER", ""          , LEFT, true);
    mainFrame.makeTabbedPanel(panel, "PNL_TABBED"   , ""          , LEFT, true);

    panel = "PNL_MESSAGES";
    mainFrame.makeTextField (panel, "TXT_MESSAGES"  , ""          , LEFT, true, "", 150, false);

    panel = "PNL_CONTAINER";
    mainFrame.makePanel     (panel, "PNL_CONTROLS"  , "Controls"  , LEFT, false, 600, 170);
    mainFrame.makePanel     (panel, "PNL_SOLUTIONS" , "Solutions" , LEFT, true, 400, 170);
    mainFrame.makePanel     (panel, "PNL_BYTECODE"  , "Bytecode"   , LEFT, true);

    panel = "PNL_SOLUTIONS";
    mainFrame.makeScrollList(panel, "LIST_SOLUTIONS", "" , solutionList);

    panel = "PNL_BYTECODE";
    mainFrame.makeCombobox  (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    mainFrame.makeCombobox  (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    mainFrame.makeButton    (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, true);

    panel = "PNL_CONTROLS";
    mainFrame.makeCombobox  (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    mainFrame.makeButton    (panel, "BTN_RUNTEST"  , "Run"         , LEFT, false);
    mainFrame.makeButton    (panel, "BTN_STOPTEST" , "STOP"        , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_ARGLIST"  , ""            , LEFT, true, "", 40, true);
    mainFrame.makeButton    (panel, "BTN_SEND"     , "Post"        , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_PORT"     , ""            , LEFT, false, "8080", 8, true);
    mainFrame.makeTextField (panel, "TXT_INPUT"    , ""            , LEFT, true, "", 40, true);
    mainFrame.makeButton    (panel, "BTN_SOLVER"   , "Solver"      , LEFT, false);

    // add a menu to the frame
    JMenuBar menuBar = new JMenuBar();
    JMenu menuProject = new JMenu("Project");
    menuBar.add(menuProject);
    JMenu menuCallgraph = new JMenu("Callgraph");
    menuBar.add(menuCallgraph);
    mainFrame.getFrame().setJMenuBar(menuBar);

    // define entries in Project header
    JMenuItem menuItem = new JMenuItem("Select Jar file");
    menuItem.addActionListener(new Action_SelectJarFile());
    menuProject.add(menuItem);
    menuProject.addSeparator();
    
    isServerTypeMenuItem = new JCheckBoxMenuItem("Input using Post (server app)");
    isServerTypeMenuItem.setMnemonic(KeyEvent.VK_P);
    isServerTypeMenuItem.setDisplayedMnemonicIndex(12);
    isServerTypeMenuItem.setSelected(isServerType);
    isServerTypeMenuItem.addItemListener(new ItemListener_EnablePost());
    menuProject.add(isServerTypeMenuItem);

    menuItem = new JMenuItem("Update danfig file");
    menuItem.addActionListener(new Action_UpdateDanfigFile());
    menuProject.add(menuItem);
    menuProject.addSeparator();

    menuItem = new JMenuItem("Clear DATABASE");
    menuItem.addActionListener(new Action_ClearDatabase());
    menuProject.add(menuItem);

    menuItem = new JMenuItem("Clear LOG");
    menuItem.addActionListener(new Action_ClearLog());
    menuProject.add(menuItem);

    // define entries in Callgraph header
    menuItem = new JMenuItem("Setup");
    menuItem.addActionListener(new Action_CallgraphSetup());
    menuCallgraph.add(menuItem);
    menuCallgraph.addSeparator();

    menuItem = new JMenuItem("Save graph (PNG)");
    menuItem.addActionListener(new Action_SaveGraphPNG());
    menuCallgraph.add(menuItem);

    menuItem = new JMenuItem("Save graph (JSON)");
    menuItem.addActionListener(new Action_SaveGraphJSON());
    menuCallgraph.add(menuItem);

    // initially disable the class/method select and generating bytecode
    mainClassCombo = mainFrame.getCombobox ("COMBO_MAINCLS");
    classCombo  = mainFrame.getCombobox ("COMBO_CLASS");
    methodCombo = mainFrame.getCombobox ("COMBO_METHOD");
    mainClassCombo.setEnabled(false);
    classCombo.setEnabled(false);
    methodCombo.setEnabled(false);
    enableControlSelections(false);

    // initially disable STOP button
    mainFrame.getButton("BTN_STOPTEST").setEnabled(false);

    // save reference to tabbed panel
    tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");

    // create the graph setup frame, but initially hide it
    createGraphSetupPanel();
  
    // setup the control actions
    mainFrame.getFrame().addWindowListener(new Window_Listener());
    classCombo.addActionListener(new Action_BytecodeClassSelect());
    mainClassCombo.addActionListener(new Action_BytecodeMethodSelect());
    (mainFrame.getButton("BTN_BYTECODE")).addActionListener(new Action_RunBytecode());
    (mainFrame.getButton("BTN_RUNTEST")).addActionListener(new Action_RunTest());
    (mainFrame.getButton("BTN_SOLVER")).addActionListener(new Action_RunSolver());
    (mainFrame.getButton("BTN_SEND")).addActionListener(new Action_SendHttpMessage());
    (mainFrame.getButton("BTN_STOPTEST")).addActionListener(new Action_StopExecution());
    (mainFrame.getTextField("TXT_ARGLIST")).addActionListener(new Action_UpdateArglist());
    (mainFrame.getTextField("TXT_ARGLIST")).addFocusListener(new Focus_UpdateArglist());
    (mainFrame.getTextField("TXT_PORT")).addActionListener(new Action_UpdatePort());
    (mainFrame.getTextField("TXT_PORT")).addFocusListener(new Focus_UpdatePort());

    // display the frame
    mainFrame.display();

    // create the special text loggers
    debugLogger = new DebugLogger(PanelTabs.LOG.toString());
    bytecodeLogger = new BytecodeViewer(PanelTabs.BYTECODE.toString());

    // create a split panel for sharing the local variables and symbolic parameters in a tab
    JTable paramList = new JTable();        // the bytecode param list
    JTable sybolList = new JTable();        // the symbolic parameter list
    String splitName = "SPLIT_PANE1";
    JSplitPane splitPane1 = mainFrame.makeSplitPane(splitName, false, 0.5);
    mainFrame.addSplitComponent(splitName, 0, "TBL_PARAMLIST", paramList, true);
    mainFrame.addSplitComponent(splitName, 1, "TBL_SYMBOLICS", sybolList, true);
//    paramList.setBorder(new TitledBorder("Local variables"));
//    sybolList.setBorder(new TitledBorder("Symbolics defined"));

    // wrap the bytecode logger in another pane to prevent line wrapping on a JTextPane
    JTextPane bytecodePane = bytecodeLogger.getTextPane();
    JPanel noWrapBytecodePanel = new JPanel(new BorderLayout());
    noWrapBytecodePanel.add(bytecodePane);

    // we're going to combine the BYTECODE entry with the parameter/symbolics split panel
    splitName = "SPLIT_MAIN";
    JSplitPane splitMain = mainFrame.makeSplitPane(splitName, true, 0.5);
    mainFrame.addSplitComponent(splitName, 0, "BYTECODE"  , noWrapBytecodePanel, true);
    mainFrame.addSplitComponent(splitName, 1, "PNL_PARAMS", splitPane1, false);
    
    // add the tabbed message panels and a listener to detect when a tab has been selected
    addPanelToTab(PanelTabs.COMMAND  , new JTextArea(), true);
    addPanelToTab(PanelTabs.DATABASE , new JTable(), true);
    addPanelToTab(PanelTabs.BYTECODE , splitMain, false);
    addPanelToTab(PanelTabs.BYTEFLOW , new JPanel(), true);
    addPanelToTab(PanelTabs.LOG      , debugLogger.getTextPane(), true);
    addPanelToTab(PanelTabs.CALLGRAPH, new JPanel(), true);
    tabPanel.addChangeListener(new Change_TabPanelSelect());

    // create the message logging for the text panels
    commandLogger  = createTextLogger(PanelTabs.COMMAND , null);

    // update divider locations in split frame now that it has been placed (and the dimensions are set)
    mainFrame.setSplitDivider("SPLIT_MAIN", 0.6);
    mainFrame.setSplitDivider("SPLIT_PANE1", 0.6);
    
    // init the CallGraph panel
    CallGraph.initCallGraph((JPanel) getTabPanel(PanelTabs.CALLGRAPH));
    bytecodeLogger.initGraph((JPanel) getTabPanel(PanelTabs.BYTEFLOW));

    // init the local variable and symbolic list tables
    localVarTbl = new ParamTable(paramList);
    symbolTbl = new SymbolTable(sybolList);
            
    // init the database table panel
    dbtable = new DatabaseTable((JTable) getTabPanel(PanelTabs.DATABASE));
  }

  private class Window_Listener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      enableUpdateTimers(false);
      if (networkListener != null) {
        networkListener.exit();
      }
      dbtable.exit();
      mainFrame.close();
      System.exit(0);
    }
  }

  private class Change_TabPanelSelect implements ChangeListener{
    @Override
    public void stateChanged(ChangeEvent e) {
      // if we switched to the graph display tab, update the graph
      if (isTabSelection_CALLGRAPH()) {
        if (CallGraph.updateCallGraph(graphMode, false)) {
//          mainFrame.repack();
        }
      }
    }
  }
    
  private class Action_SelectJarFile implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // load the selected file
      int rc = loadJarFile();
      if (rc == 0) {
        mainFrame.getFrame().setTitle(projectPathName + projectName);
      }
        
      // enable the class and method selections
      mainClassCombo.setEnabled(true);
      classCombo.setEnabled(true);
      methodCombo.setEnabled(true);
      enableControlSelections(true);
    }
  }

  private class Action_UpdateDanfigFile implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // init the background stuff
      String content = initDanfigInfo();
    
      // now add in the latest and greatest symbolic defs
      content += symbolTbl.getSymbolicList();

      // now save to the file
      saveProjectTextFile("danfig", content);
      printStatusMessage("Updated danfig file");
    }
  }
  
  private class Action_ClearDatabase implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      dbtable.clearDB();
    }
  }
  
  private class Action_ClearLog implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // clear out the debug logger
      debugLogger.clear();

      // reset debug input from network in case some messages are pending
      if (udpThread != null) {
        udpThread.resetInput();
        resetCapturedInput();
      }

      // force the highlight selection back to NONE
      setHighlightMode(GraphHighlight.NONE);

      // init thread selection in highlighting to OFF
      setThreadEnabled(false);
    }
  }
  
  private class Action_CallgraphSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      graphSetupFrame.display();
    }
  }
  
  private class Action_SaveGraphPNG implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // only enable setup graph button if graph is not empty
      if (CallGraph.getMethodCount() == 0) {
        saveGraphButtonActionPerformed("png");
      } else {
        printStatusMessage("WARNING: No graphics to save!");
      }
    }
  }
      
  private class Action_SaveGraphJSON implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // only enable setup graph button if graph is not empty
      if (CallGraph.getMethodCount() == 0) {
        saveGraphButtonActionPerformed("json");
      } else {
        printStatusMessage("WARNING: No graphics to save!");
      }
    }
  }
  
  private class Action_BytecodeClassSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JComboBox cbSelect = (JComboBox) evt.getSource();
      String classSelect = (String) cbSelect.getSelectedItem();
      setMethodSelections(classSelect);
//      mainFrame.getFrame().pack(); // need to update frame in case width requirements change
    }
  }

  private class Action_BytecodeMethodSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      //if (evt.getActionCommand().equals("comboBoxChanged")) {
      if (!mainClassInitializing) {
        updateProjectProperty("COMBO_MAINCLS");
      }
    }
  }
  
  private class ItemListener_EnablePost implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent ie) {
      isServerType = ie.getStateChange() == ItemEvent.SELECTED;
      
      // enable/disable "send to port" controls accordingly
      mainFrame.getButton("BTN_SEND").setEnabled(isServerType);
      mainFrame.getTextField("TXT_PORT").setEnabled(isServerType);
      mainFrame.getLabel("TXT_PORT").setEnabled(isServerType);

      if (projectProps != null) {
        projectProps.setPropertiesItem(ProjectProperties.IS_SERVER_TYPE.toString(),
              isServerType ? "true" : "false");
      }
    }
  }
    
  private class Action_SendHttpMessage implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String targetURL = "http://localhost:" + mainFrame.getTextField("TXT_PORT").getText();
      String input = mainFrame.getTextField("TXT_INPUT").getText();
      executePost(targetURL, input);
    }
  }
      
  private class Action_RunBytecode implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String classSelect  = (String) classCombo.getSelectedItem();
      String methodSelect = (String) methodCombo.getSelectedItem();
      generateBytecode(classSelect, methodSelect);
    }
  }

  private class Action_RunTest implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String arglist = mainFrame.getTextField("TXT_ARGLIST").getText();
      runTest(arglist);
    }
  }

  private class Action_RunSolver implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      dansolver.Dansolver.main(null);
    }
  }

  private class Action_StopExecution implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // stop the running process
      ThreadLauncher.ThreadInfo threadInfo = threadLauncher.stopAll();
      if (threadInfo.pid >= 0) {
        printCommandMessage("Killing job " + threadInfo.jobid + ": pid " + threadInfo.pid);

        String[] command = { "kill", "-15", threadInfo.pid.toString() };
        CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
        commandLauncher.start(command, null);
      }
    }
  }
  
  private class Action_UpdateArglist implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateProjectProperty("TXT_ARGLIST");
    }
  }

  private class Focus_UpdateArglist implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
    }
    @Override
    public void focusLost(FocusEvent e) {
      updateProjectProperty("TXT_ARGLIST");
    }
  }
    
  private class Action_UpdatePort implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateProjectProperty("TXT_PORT");
    }
  }

  private class Focus_UpdatePort implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
    }
    @Override
    public void focusLost(FocusEvent e) {
      updateProjectProperty("TXT_PORT");
    }
  }
    
  private void enableControlSelections(boolean enable) {
    mainFrame.getLabel("COMBO_MAINCLS").setEnabled(enable);
    mainFrame.getLabel("COMBO_CLASS").setEnabled(enable);
    mainFrame.getLabel("COMBO_METHOD").setEnabled(enable);
    mainFrame.getButton("BTN_BYTECODE").setEnabled(enable);
    mainFrame.getButton("BTN_RUNTEST").setEnabled(enable);
    mainFrame.getButton("BTN_SOLVER").setEnabled(enable);
    mainFrame.getTextField("TXT_ARGLIST").setEnabled(enable);
    mainFrame.getTextField("TXT_INPUT").setEnabled(enable);

    // only enable these if the "Post message" is also enabled
    if (enable) {
      enable = isServerType;
    }
    mainFrame.getButton("BTN_SEND").setEnabled(enable);
    mainFrame.getTextField("TXT_PORT").setEnabled(enable);
//    mainFrame.getLabel("TXT_PORT").setEnabled(enable);
  }  

  private static void createGraphSetupPanel() {
    if (graphSetupFrame.isValidFrame()) {
      return;
    }

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT   = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT  = GuiControls.Orient.RIGHT;
    
    // create the frame
    graphSetupFrame.newFrame("Graph Setup", 350, 250, FrameSize.FIXEDSIZE);
  
    panel = null;
    graphSetupFrame.makePanel (panel, "PNL_HIGHLIGHT", "Graph Highlight"   , LEFT, false);
    graphSetupFrame.makePanel (panel, "PNL_ADJUST"   , ""                  , LEFT, true);

    panel = "PNL_ADJUST";
    graphSetupFrame.makePanel (panel, "PNL_THREAD"   , "Thread Select"     , LEFT, true);
    graphSetupFrame.makeLineGap(panel);
    graphSetupFrame.makePanel (panel, "PNL_RANGE"    , "Highlight Range"   , LEFT, true);
    
    panel = "PNL_HIGHLIGHT";
    graphSetupFrame.makeRadiobutton(panel, "RB_THREAD"    , "Thread"          , LEFT, true, 0);
    graphSetupFrame.makeRadiobutton(panel, "RB_ELAPSED"   , "Elapsed Time"    , LEFT, true, 0);
    graphSetupFrame.makeRadiobutton(panel, "RB_INSTRUCT"  , "Instructions"    , LEFT, true, 0);
    graphSetupFrame.makeRadiobutton(panel, "RB_ITER"      , "Iterations Used" , LEFT, true, 0);
    graphSetupFrame.makeRadiobutton(panel, "RB_STATUS"    , "Status"          , LEFT, true, 0);
    graphSetupFrame.makeRadiobutton(panel, "RB_NONE"      , "Off"             , LEFT, true, 1);

    panel = "PNL_THREAD";
    //graphSetupFrame.makeCheckbox   (panel, "CB_SHOW_ALL", "Show all threads", LEFT, true, 1);
    graphSetupFrame.makeLabel      (panel, "TXT_TH_SEL" , "0"  , LEFT, false);
    graphSetupFrame.makeButton     (panel, "BTN_TH_UP"  , "UP" , LEFT, false);
    graphSetupFrame.makeButton     (panel, "BTN_TH_DN"  , "DN" , LEFT, true);
    
    panel = "PNL_RANGE";
    graphSetupFrame.makeLabel      (panel, "TXT_RANGE"  , "20" , LEFT, false);
    graphSetupFrame.makeButton     (panel, "BTN_RG_UP"  , "UP" , LEFT, false);
    graphSetupFrame.makeButton     (panel, "BTN_RG_DN"  , "DN" , LEFT, true);

    // init thread selection in highlighting to OFF
    graphSetupFrame.getRadiobutton("RB_THREAD").setEnabled(false);
    setThreadControls(false);
    
    // setup the control actions
    graphSetupFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        graphSetupFrame.hide();
      }
    });
    (graphSetupFrame.getRadiobutton("RB_THREAD")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.THREAD);
        
        // enable the thread controls when this is selected
        setThreadControls(true);
      }
    });
    (graphSetupFrame.getRadiobutton("RB_ELAPSED")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.TIME);
      }
    });
    (graphSetupFrame.getRadiobutton("RB_INSTRUCT")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.INSTRUCTION);
      }
    });
    (graphSetupFrame.getRadiobutton("RB_ITER")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.ITERATION);
      }
    });
    (graphSetupFrame.getRadiobutton("RB_STATUS")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.STATUS);
      }
    });
    (graphSetupFrame.getRadiobutton("RB_NONE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(GraphHighlight.NONE);
      }
    });
    (graphSetupFrame.getButton("BTN_TH_UP")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
        int threadCount = debugLogger.getThreadCount();
        int value = Integer.parseInt(label.getText().trim());
        if (value < threadCount - 1) {
          value++;
          label.setText("" + value);
          
          CallGraph.setThreadSelection(value);

          // if CallGraph is selected, update the graph
          if (isTabSelection(PanelTabs.CALLGRAPH)) {
            CallGraph.updateCallGraph(GraphHighlight.THREAD, true);
          }
        }
      }
    });
    (graphSetupFrame.getButton("BTN_TH_DN")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
        int value = Integer.parseInt(label.getText().trim());
        if (value > 0) {
          value--;
          label.setText("" + value);
          
          CallGraph.setThreadSelection(value);

          // if CallGraph is selected, update the graph
          if (isTabSelection(PanelTabs.CALLGRAPH)) {
            CallGraph.updateCallGraph(GraphHighlight.THREAD, true);
          }
        }
      }
    });
    (graphSetupFrame.getButton("BTN_RG_UP")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
        int step = Integer.parseInt(label.getText().trim());
        if (step < 20) {
          step++;
          label.setText("" + step);
          
          CallGraph.setRangeStepSize(step);

          // if CallGraph is selected, update the graph
          if (isTabSelection(PanelTabs.CALLGRAPH)) {
            CallGraph.updateCallGraph(graphMode, true);
          }
        }
      }
    });
    (graphSetupFrame.getButton("BTN_RG_DN")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
        int step = Integer.parseInt(label.getText().trim());
        if (step > 1) {
          step--;
          label.setText("" + step);
          
          CallGraph.setRangeStepSize(step);

          // if CallGraph is selected, update the graph
          if (isTabSelection(PanelTabs.CALLGRAPH)) {
            CallGraph.updateCallGraph(graphMode, true);
          }
        }
      }
    });
}

  public static boolean isInstrumentedMethod(String methName) {
    return fullMethList.contains(methName);
  }
  
  private Logger createTextLogger(PanelTabs tabname, HashMap<String, FontInfo> fontmap) {
    return new Logger(getTabPanel(tabname), tabname.toString(), fontmap);
  }
  
  private void addPanelToTab(PanelTabs tabname, Component panel, boolean scrollable) {
    // make sure we don't already have the entry
    if (tabbedPanels.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel already defined in tabs");
      System.exit(1);
    }
    
    // add the textPane to a scrollPane
    if (scrollable) {
      JScrollPane scrollPanel;
      scrollPanel = new JScrollPane(panel);
      scrollPanel.setBorder(BorderFactory.createTitledBorder(""));

      // now add the scroll pane to the tabbed pane
      tabPanel.addTab(tabname.toString(), scrollPanel);
    } else {
      // or add the original pane to the tabbed pane
      tabPanel.addTab(tabname.toString(), panel);
    }
    
    // save access to panel by name
    tabSelect.put(tabname, tabIndex++);
    tabbedPanels.put(tabname, panel);
  }

  private static Component getTabPanel(PanelTabs tabname) {
    if (!tabbedPanels.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }
    return tabbedPanels.get(tabname);
  }

  private static PanelTabs getTabSelect() {
    if (tabPanel == null || tabSelect.isEmpty()) {
      return null;
    }

    int curTab = tabPanel.getSelectedIndex();
    for (HashMap.Entry pair : tabSelect.entrySet()) {
      if ((Integer) pair.getValue() == curTab) {
        return (PanelTabs) pair.getKey();
      }
    }
    return null;
  }
  
  private static void setTabSelect(PanelTabs tabname) {
    Integer index = tabSelect.get(tabname);
    if (index == null) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }

    tabPanel.setSelectedIndex(index);
  }
  
  private static void setHighlightMode(GraphHighlight mode) {
    JRadioButton threadSelBtn = graphSetupFrame.getRadiobutton("RB_THREAD");
    JRadioButton timeSelBtn   = graphSetupFrame.getRadiobutton("RB_ELAPSED");
    JRadioButton instrSelBtn  = graphSetupFrame.getRadiobutton("RB_INSTRUCT");
    JRadioButton iterSelBtn   = graphSetupFrame.getRadiobutton("RB_ITER");
    JRadioButton statSelBtn   = graphSetupFrame.getRadiobutton("RB_STATUS");
    JRadioButton noneSelBtn   = graphSetupFrame.getRadiobutton("RB_NONE");

    // turn on the selected mode and turn off all others
    switch(mode) {
      case THREAD:
        threadSelBtn.setSelected(true);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);

        // send the thread selection to the CallGraph
        JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
        int select = Integer.parseInt(label.getText().trim());
        CallGraph.setThreadSelection(select);
        break;
      case TIME:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(true);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case INSTRUCTION:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(true);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case ITERATION:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(true);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case STATUS:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(true);
        noneSelBtn.setSelected(false);
        break;
      case NONE:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(true);
      default:
        break;
    }

    // set the mode flag & update graph
    graphMode = mode;
    if (isTabSelection(PanelTabs.CALLGRAPH)) {
      CallGraph.updateCallGraph(graphMode, false);
    }
  }
  
  private static void saveGraphButtonActionPerformed(String type) {
    if (!type.equals("json")) {
      type = "png";
    }
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    Date date = new Date();
    String defaultName = dateFormat.format(date) + "callgraph";
    FileNameExtensionFilter filter = new FileNameExtensionFilter(type.toUpperCase() + " Files", type);
    String fileExtension = "." + type;
    fileSelector.setFileFilter(filter);
    fileSelector.setApproveButtonText("Save");
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setSelectedFile(new File(defaultName + fileExtension));
    int retVal = fileSelector.showOpenDialog(graphSetupFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = fileSelector.getSelectedFile();
      String basename = file.getAbsolutePath();
      
      // get the base name without extension so we can create matching json and png files
      int offset = basename.lastIndexOf('.');
      if (offset > 0) {
        basename = basename.substring(0, offset);
      }

      // remove any pre-existing file and convert method list to appropriate file
      if (type.equals("json")) {
        File graphFile = new File(basename + fileExtension);
        graphFile.delete();
        CallGraph.saveAsJSONFile(graphFile, true);
      } else {
        File pngFile = new File(basename + fileExtension);
        pngFile.delete();
        CallGraph.saveAsImageFile(pngFile);
      }
    }
  }
  
  private static void startDebugPort(String projectPath) {
    int port = 5000; // init to default value
    if (debugPort != null && !debugPort.isEmpty()) {
      try {
        port = Integer.parseUnsignedInt(debugPort);
      } catch (NumberFormatException ex) {
        System.err.println("ERROR: Invalid port value for NetworkServer: " + debugPort);
      }
    }

    enableUpdateTimers(false);

    if (udpThread != null) {
      // server is already running, see if no change in parameters
      if (port != udpThread.getPort()) {
        // port change: we have to close the current server so we can create a new one using a different port
        udpThread.exit();
        System.out.println("Changinng NetworkServer port from " + udpThread.getPort() + " to " + port);
        // continue onto starting a new server
      } else if (!projectPath.equals(udpThread.getOutputFile())) {
        // storage location changed - easy, we can just change the setting on the fly
        System.out.println("Changing NetworkServer log location to: " + projectPath + "debug.log");
        udpThread.setBufferFile(projectPath + "debug.log");
        return;
      } else {
        // no change - even easier. just exit.
        System.out.println("No change in NetworkServer.");
        return;
      }
    }
    
    try {
      udpThread = new NetworkServer(port, true);
    } catch (IOException ex) {
      System.err.println("ERROR: unable to start NetworkServer. " + ex);
    }

    System.out.println("danlauncher receiving port: " + port);
    udpThread.start();
    udpThread.setLoggingCallback(makeConnection);
    udpThread.setBufferFile(projectPath + "/debug.log");

//    // get this server's ip address
//    String ipaddr = "<unknown>";
//    try(final DatagramSocket socket = new DatagramSocket()){
//      socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
//      ipaddr = socket.getLocalAddress().getHostAddress();
//    } catch (SocketException | UnknownHostException ex) {  }

    networkListener = udpThread; // this allows us to signal the network listener

    enableUpdateTimers(true);
  }
  
  private static void enableUpdateTimers(boolean enable) {
    if (enable) {
      if (debugMsgTimer != null) {
        debugMsgTimer.start();
      }
      if (graphTimer != null) {
        graphTimer.start();
      }
    } else {
      if (debugMsgTimer != null) {
        debugMsgTimer.stop();
      }
      if (graphTimer != null) {
        graphTimer.stop();
      }
    }
  }
  
  public static boolean isElapsedModeReset() {
    return elapsedMode == ElapsedMode.RESET;
  }
  
  public static void setThreadEnabled(boolean enabled) {
    JRadioButton button = graphSetupFrame.getRadiobutton("RB_THREAD");
    button.setEnabled(enabled);
    setThreadControls(button.isSelected());
  }
    
  public static void setThreadControls(boolean enabled) {
    JLabel label = graphSetupFrame.getLabel ("TXT_TH_SEL");
    label.setText("0");
    label.setEnabled(enabled);

    graphSetupFrame.getButton("BTN_TH_UP").setEnabled(enabled);
    graphSetupFrame.getButton("BTN_TH_DN").setEnabled(enabled);
  }
  
  public static boolean isTabSelection_CALLGRAPH() {
    return isTabSelection(PanelTabs.CALLGRAPH);
  }

  public static boolean isTabSelection_DATABASE() {
    return isTabSelection(PanelTabs.DATABASE);
  }

  private static boolean isTabSelection(PanelTabs select) {
    if (tabPanel == null || tabSelect.isEmpty()) {
      return false;
    }
    if (!tabSelect.containsKey(select)) {
      System.err.println("Tab selection '" + select + "' not found!");
      return false;
    }

    int graphTab = tabSelect.get(select);
    int curTab = tabPanel.getSelectedIndex();
    return curTab == graphTab;
  }

  public static void highlightBranch(int start, boolean branch) {
    bytecodeLogger.highlightBranch(start, branch);
  }
  
  public static void resetLoggedTime() {
    // this adds log entry to file for demarcation
    if (udpThread != null) {
      udpThread.resetInput();
    }
    
    // this resets and starts the timer
    startElapsedTime();
  }
  
  public static void resetCapturedInput() {
    // clear the packet buffer and statistics
    if (udpThread != null) {
      udpThread.clear();
    }

    // clear the graphics panel
    CallGraph.clearGraphAndMethodList();
    if (isTabSelection_CALLGRAPH()) {
      CallGraph.updateCallGraph(GraphHighlight.NONE, false);
    }
          
    // reset the elapsed time
//    resetElapsedTime();
  }
  
  public static void addLocalVariable(String name, String type, String slot, String start, String end) {
    localVarTbl.addEntry(name, type, slot, start, end);
  }

  public static void addSymbVariable(String meth, String name, String type, String slot, String start, String end) {
    symbolTbl.addEntry(meth, name, type, slot, start, end);
  }

  public static Integer byteOffsetToLineNumber(int offset) {
    return bytecodeLogger.byteOffsetToLineNumber(offset);
  }
  
  
  /**
   * finds the classes in a jar file & sets the Class ComboBox to these values.
   */
  private static void setupClassList (String pathname) {
    // init the class list
    clsMethMap = new HashMap<>();
    classList = new ArrayList<>();
    fullMethList = new ArrayList<>();

    // read the list of methods from the "methodlist.txt" file created by Instrumentor
    try {
      File file = new File(pathname + "methodlist.txt");
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        fullMethList.add(line);
      }
      fileReader.close();
    } catch (IOException ex) {
      System.err.println("ERROR: <GuiPanel.setupClassList> " + ex);
      return;
    }

    // exit if no methods were found
    if (fullMethList.isEmpty()) {
      System.err.println("ERROR: <GuiPanel.setupClassList> No methods found in methodlist.txt file!");
      return;
    }
        
    // now map the entries from the method list to the corresponding class name
    Collections.sort(fullMethList);
    String curClass = "";
    ArrayList<String> methList = new ArrayList<>();
    for (String fullMethodName : fullMethList) {
      int methOffset = fullMethodName.indexOf(".");
      if (methOffset > 0) {
        String className = fullMethodName.substring(0, methOffset);
        String methName = fullMethodName.substring(methOffset + 1);
        // if new class was found and a method list was valid, save the class and classMap
        if (!curClass.equals(className) && !methList.isEmpty()) {
          classList.add(curClass);
          clsMethMap.put(curClass, methList);
          methList = new ArrayList<>();
        }

        // save the class name for the list and add the method name to it
        curClass = className;
        methList.add(methName);
      }
    }

    // save the remaining class
    if (!methList.isEmpty()) {
      classList.add(curClass);
      clsMethMap.put(curClass, methList);
    }

    // setup the class and method selections
    setClassSelections();
    System.out.println(classList.size() + " classes and " +
        fullMethList.size() + " methods found");
  }

  private static void setClassSelections() {
    // temp disable update of properties from main class selection, since all we are doing here
    // is filling the entries in. The user has not made a selection yet.
    mainClassInitializing = true;
    
    classCombo.removeAllItems();
    mainClassCombo.removeAllItems();
    for (int ix = 0; ix < classList.size(); ix++) {
      String cls = classList.get(ix);
      classCombo.addItem(cls);

      // now get the methods for the class and check if it has a "main"
      ArrayList<String> methodSelection = clsMethMap.get(cls);
      if (methodSelection != null && methodSelection.contains("main([Ljava/lang/String;)V")) {
        mainClassCombo.addItem(cls);
      }
    }

    // init class selection to 1st item
    classCombo.setSelectedIndex(0);
    
    // set main class selection from properties setting (wasn't done earlier because there may
    // not have been any selection yet).
    String val = projectProps.getPropertiesItem(ProjectProperties.MAIN_CLASS.toString(), "");
    if (!val.isEmpty()) {
      mainClassCombo.setSelectedItem(val);
    }
    
    // now we can re-enable changes in the main class causing the properties entry to be updated
    mainClassInitializing = false;
    
    // now update the method selections
    setMethodSelections((String) classCombo.getSelectedItem());

    // in case selections require expansion, adjust frame packing
    // TODO: skip this, since it causes the frame size to get extremely large for some reason
//    GuiControls.getFrame().pack();
  }

  private static void setMethodSelections(String clsname) {
    // init the method selection list
    methodCombo.removeAllItems();

    // make sure we have a valid class selection
    if (clsname == null || clsname.isEmpty()) {
      return;
    }
    
    if (clsname.endsWith(".class")) {
      clsname = clsname.substring(0, clsname.length()-".class".length());
    }

    // get the list of methods for the selected class from the hash map
    ArrayList<String> methodSelection = clsMethMap.get(clsname);
    if (methodSelection != null) {
      // now get the methods for the class and place in the method selection combobox
      for (String method : methodSelection) {
        methodCombo.addItem(method);
      }

      // set the 1st entry as the default selection
      if (methodCombo.getItemCount() > 0) {
        methodCombo.setSelectedIndex(0);
      }
    }
  }
  
  private static void startElapsedTime() {
    elapsedStart = System.currentTimeMillis();
    elapsedMode = ElapsedMode.RUN;
//    GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void resetElapsedTime() {
    elapsedStart = 0;
    elapsedMode = ElapsedMode.RESET;
//    GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void updateElapsedTime() {
    if (elapsedMode == ElapsedMode.RUN) {
      long elapsed = System.currentTimeMillis() - elapsedStart;
      if (elapsed > 0) {
        Integer msec = (int)(elapsed % 1000);
        elapsed = elapsed / 1000;
        Integer secs = (int)(elapsed % 60);
        Integer mins = (int)(elapsed / 60);
        String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
                           ((secs < 10) ? "0" : "") + secs.toString(); // + "." +
                           //((msec < 10) ? "00" : (msec < 100) ? "0" : "") + msec.toString();
//        mainFrame.getTextField("FIELD_ELAPSED").setText(timestamp);
      }
    }
  }
  
  private static void printStatusClear() {
    mainFrame.getTextField("TXT_MESSAGES").setText("                   ");
  }
  
  private static void printStatusMessage(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText(message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  private static void printStatusError(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText("ERROR: " + message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  public static void printCommandMessage(String message) {
    commandLogger.printLine(message);
  }
  
  private static void printCommandError(String message) {
    commandLogger.printLine(message);
  }
  
  private static void initProjectProperties(String pathname) {
    // create the properties file if not already created
    if (projectProps == null) {
      projectProps = new PropertiesFile(pathname + ".danlauncher", "PROJECT_PROPERTIES", commandLogger);
    }

    // loop thru the properties table and set up each entry
    for (PropertiesTable propEntry : PROJ_PROP_TBL) {
      ProjectProperties tag = propEntry.tag;
      String ctlName = propEntry.controlName;
      InputControl ctlType = propEntry.controlType;
      
      // get properties values if defined (use the current gui control value as the default if not)
      String setting = mainFrame.getInputControl(ctlName, ctlType);
      String val = projectProps.getPropertiesItem(tag.toString(), setting);
      if (!setting.equals(val)) {
        // if property value was found & differs from gui setting, update the gui
        mainFrame.setInputControl(ctlName, ctlType, val);
      }
    }
    
    // other params not covered by the PROJ_PROP_TBL:
    String dflt = isServerType ? "true" : "false";
    String val = projectProps.getPropertiesItem(ProjectProperties.IS_SERVER_TYPE.toString(), dflt);
    if (!val.equals(dflt)) {
      // update the GUI selection
      isServerType = (val.equals("true"));
      isServerTypeMenuItem.setSelected(isServerType);
    }
  }
  
  private static void updateProjectProperty(String ctlName) {
    for (PropertiesTable propEntry : PROJ_PROP_TBL) {
      if (ctlName.equals(propEntry.controlName)) {
        String val = mainFrame.getInputControl(ctlName, propEntry.controlType);
        if(projectProps == null) {
          System.err.println("ERROR: PROJECT_PROPERTIES not set up when updating: " + ctlName);
        } else {
          projectProps.setPropertiesItem(propEntry.tag.toString(), val);
        }
        return;
      }
    }
    System.err.println("ERROR: User control '" + ctlName + "' not found");
  }
  
  private static boolean fileCheck(String fname) {
    if (new File(fname).isFile()) {
      return true;
    }

    printStatusError("Missing file: " + fname);
    return false;
  }
  
  private static void addSolution(String solution, long cost) {
    if(solutionMap.containsKey(solution)) {
      solutionMap.replace(solution, cost);
      System.out.println("Replaced solution " + solution + " with cost: " + cost);

      // let's clear the field and redo them all
      solutionList.clear();
      Iterator it = solutionMap.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pair = (Map.Entry)it.next();
        String coststr = cost < 0 ? "---" : "" + pair.getValue();
        solutionList.addElement(pair.getKey() + "    " + coststr);
        //it.remove(); // avoids a ConcurrentModificationException
      }
    } else {
      solutionMap.put(solution, cost);
      System.out.println("Added solution " + solution + " at cost: " + cost);

      // can simply add the entry
      String coststr = cost < 0 ? "---" : "" + cost;
      solutionList.addElement(solution + "    " + coststr);
    }
  }
  
  private static int loadJarFile() {
    printStatusClear();

    FileNameExtensionFilter filter = new FileNameExtensionFilter("Jar Files", "jar");
    fileSelector.setFileFilter(filter);
    //fileSelector.setSelectedFile(new File("TestMain.jar"));
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setApproveButtonText("Load");
    int retVal = fileSelector.showOpenDialog(mainFrame.getFrame());
    if (retVal != JFileChooser.APPROVE_OPTION) {
      return 1;
    }

    // read the file
    File file = fileSelector.getSelectedFile();
    projectName = file.getName();
    projectPathName = file.getParentFile().getAbsolutePath() + "/";
      
    // save location of project selection
    systemProps.setPropertiesItem(SystemProperties.PROJECT_PATH.toString(), projectPathName);
        
    // init project config file to current settings
    initProjectProperties(projectPathName);
        
    // verify all the required files exist
    if (!fileCheck(projectPathName + projectName) ||
        !fileCheck(DSEPATH + "danalyzer/dist/danalyzer.jar") ||
        !fileCheck(DSEPATH + "danalyzer/lib/commons-io-2.5.jar") ||
        !fileCheck(DSEPATH + "danalyzer/lib/asm-all-5.2.jar")) {
      return -1;
    }
    
    // clear out the symbolic parameter list
    symbolTbl.clear();

    // read the symbolic parameter definitions from danfig file (if present)
    readSymbolicList();
  
    String mainclass = "danalyzer.instrumenter.Instrumenter";
    String classpath = DSEPATH + "danalyzer/dist/danalyzer.jar";
    classpath += ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar";
    classpath += ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar";
    classpath += ":/*:/lib/*";

    // remove any existing class and javap files in the location of the jar file
    try {
      FileUtils.deleteDirectory(new File(projectPathName + CLASSFILE_STORAGE));
      FileUtils.deleteDirectory(new File(projectPathName + JAVAPFILE_STORAGE));
    } catch (IOException ex) {
      printCommandError(ex.getMessage());
    }
      
    // determine if jar file needs instrumenting
    if (projectName.endsWith("-dan-ed.jar")) {
      // file is already instrumented - use it as-is
      printCommandMessage("Input file already instrumented - using as is.");
      projectName = projectName.substring(0, projectName.indexOf("-dan-ed.jar")) + ".jar";
    } else {
      int retcode = 0;
      String baseName = projectName.substring(0, projectName.indexOf(".jar"));

      // strip out any debug info (it screws up the agent)
      // this will transform the temp file and name it the same as the original instrumented file
      printCommandMessage("Stripping debug info from uninstrumented jar file");
      String outputName = baseName + "-strip.jar";
      String[] command2 = { "pack200", "-r", "-G", projectPathName + outputName, projectPathName + projectName };
      // this creates a command launcher that runs on the current thread
      CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
      retcode = commandLauncher.start(command2, projectPathName);
      if (retcode == 0) {
        printStatusMessage("Debug stripping was successful");
        printCommandMessage(commandLauncher.getResponse());
      } else {
        printStatusError("stripping file: " + projectName);
        return -1;
      }
        
      // instrument the jar file
      printCommandMessage("Instrumenting stripped file: " + outputName);
      String[] command = { "java", "-cp", classpath, mainclass, outputName };
      retcode = commandLauncher.start(command, projectPathName);
      if (retcode == 0) {
        printStatusMessage("Instrumentation successful");
        printCommandMessage(commandLauncher.getResponse());
        outputName = baseName + "-strip-dan-ed.jar";
      } else {
        printStatusError("instrumenting file: " + outputName);
        return -1;
      }
        
      // rename the instrumented file to the correct name
      String tempjar = outputName;
      outputName = baseName + "-dan-ed.jar";
      printCommandMessage("Renaming " + tempjar + " to " + outputName);
      File tempfile = new File(projectPathName + tempjar);
      if (!tempfile.isFile()) {
        printStatusError("instrumented file not found: " + tempjar);
        return -1;
      }
      File newfile = new File(projectPathName + outputName);
      tempfile.renameTo(newfile);
        
      // remove temp file
      tempjar = baseName + "-strip.jar";
      tempfile = new File(projectPathName + tempjar);
      if (tempfile.isFile()) {
        printCommandMessage("Removing " + tempjar);
        tempfile.delete();
      }
    }
      
    // update the class and method selections
    setupClassList(projectPathName);
        
    // setup access to the network listener thread
    startDebugPort(projectPathName);
        
    // reset the soultion list
    solutionMap.clear();
    solutionList.clear();
    return 0;
  }

  private void runTest(String arglist) {
    printStatusClear();

    String instrJarFile = projectName.substring(0, projectName.indexOf(".")) + "-dan-ed.jar";
    
    // verify all the required files exist
    if (!fileCheck(projectPathName + instrJarFile) ||
        !fileCheck(DSEPATH + "danalyzer/dist/danalyzer.jar") ||
        !fileCheck(DSEPATH + "danalyzer/lib/com.microsoft.z3.jar") ||
        !fileCheck(DSEPATH + "danalyzer/lib/commons-io-2.5.jar") ||
        !fileCheck(DSEPATH + "danalyzer/lib/asm-all-5.2.jar") ||
        !fileCheck(DSEPATH + "danhelper/libdanhelper.so")) {
      return;
    }
    
    // get the user-supplied main class and input value
    String mainclass = (String) mainClassCombo.getSelectedItem();
    if (mainclass == null) {
      printStatusError("no main class found!");
      return;
    }
    mainclass = mainclass.replaceAll("/", ".");

    // build up the command to run
    String localpath = "*";
    if (new File("lib").isDirectory()) {
      localpath += ":lib/*";
    }
    if (new File("libs").isDirectory()) {
      localpath += ":libs/*";
    }
    String mongolib = DSEPATH + "danalyzer/lib/mongodb-driver-core-3.8.2.jar"
              + ":" + DSEPATH + "danalyzer/lib/mongodb-driver-sync-3.8.2.jar"
              + ":" + DSEPATH + "danalyzer/lib/bson-3.8.2.jar";
    String options = "-Xverify:none";
    String bootlpath = "-Dsun.boot.library.path=" + javaHome + "/bin:/usr/lib";
    String bootcpath ="-Xbootclasspath/a"
              + ":" + DSEPATH + "danalyzer/dist/danalyzer.jar"
              + ":" + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar"
              + ":" + mongolib;
    String agentpath ="-agentpath:" + DSEPATH + "danhelper/libdanhelper.so";
    String classpath = instrJarFile
              + ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar"
              + ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar"
              + ":" + mongolib
              + ":" + localpath;

    if (!isServerType) {
      inputAttempt = arglist;
      elapsedStart = System.currentTimeMillis();
    }

    printStatusMessage("Run command started...");
    
    // run the instrumented jar file
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass, arglist };

    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(command, projectPathName, "run_" + projectName, null);

    // allow user to terminate the test
    mainFrame.getButton("BTN_STOPTEST").setEnabled(true);
  }

  public static void generateBytecode(String classSelect, String methodSelect) {
    printStatusClear();

    // get the current tab selection
    PanelTabs selected = getTabSelect();

    // check if bytecode for this method already displayed
    if (bytecodeLogger.isMethodDisplayed(classSelect, methodSelect)) {
      printStatusMessage("Bytecode already loaded");

      // clear any highlighting
      bytecodeLogger.highlightClear();
      
      // swich tab to show bytecode
      if (selected != PanelTabs.BYTECODE && selected != PanelTabs.BYTEFLOW) {
        setTabSelect(PanelTabs.BYTECODE);
      }
      return;
    }
    
    // first we have to pull off the class files from the jar file
    File jarfile = new File(projectPathName + projectName);
    if (!jarfile.isFile()) {
      printStatusError("Jar file not found: " + jarfile);
      return;
    }
    try {
      // extract the selected class file
      extractClassFile(jarfile, classSelect);
    } catch (IOException ex) {
      printStatusError(ex.getMessage());
      return;
    }

    // clear out the local variable list
    localVarTbl.clear(classSelect + "." + methodSelect);

    // decompile the selected class file
    String[] command = { "javap", "-p", "-c", "-s", "-l", CLASSFILE_STORAGE + "/" + classSelect + ".class" };
    // this creates a command launcher that runs on the current thread
    CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
    int retcode = commandLauncher.start(command, projectPathName);
    if (retcode == 0) {
      String content = commandLauncher.getResponse();
      bytecodeLogger.parseJavap(classSelect, methodSelect, content);
      if (bytecodeLogger.isValidBytecode()) {
        printStatusMessage("Successfully generated bytecode for method");
        bytecodeLogger.clearGraph();
        bytecodeLogger.drawGraph();
        bytecodeLogger.updateCallGraph();
      } else {
        printStatusMessage("Generated class bytecode, but method not found");
      }

      // save the file in with the class
      saveProjectTextFile(JAVAPFILE_STORAGE + "/" + classSelect + ".txt", content);
      
      // swich tab to show bytecode
      if (selected != PanelTabs.BYTECODE && selected != PanelTabs.BYTEFLOW) {
        setTabSelect(PanelTabs.BYTECODE);
      }
    } else {
      printStatusError("running javap on file: " + classSelect + ".class");
    }
  }

  private static void executePost(String targetURL, String urlParameters) {
    printStatusMessage("Posting message to: " + targetURL);
    printCommandMessage("Message contents: " + urlParameters);

    HttpURLConnection connection = null;
    
    // get starting time
    if (isServerType) {
      inputAttempt = urlParameters;
      elapsedStart = System.currentTimeMillis();
    }

    try {
      // Create connection
      URL url = new URL(targetURL);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
      connection.setRequestProperty("Content-Language", "en-US");  
      connection.setUseCaches(false);
      connection.setDoOutput(true);

      // Send request
      DataOutputStream wr = new DataOutputStream (connection.getOutputStream());
      wr.writeBytes(urlParameters);
      wr.close();

      // Get Response  
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      // display response.toString()
      printStatusMessage("Post successful");
      printCommandMessage("Post RESPONSE: " + response.toString());

      // add input value to solutions tried
      // TODO: this is not really the elapsed time - we need to read from the debug output to
      // determine when the terminating condition has been reached.
      addSolution(inputAttempt, System.currentTimeMillis() - elapsedStart);
    } catch (IOException ex) {
      // display error
      printStatusMessage("Post failure");
      printCommandError(ex.getMessage());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
  
  private static void extractClassFile(File jarfile, String className) throws IOException {
    // get the path relative to the application directory
    int offset;
    String relpathname = "";
    className = className + ".class";
//    if (!CLASSFILE_STORAGE.isEmpty()) {
//      offset = className.indexOf(CLASSFILE_STORAGE);
//      if (offset >= 0) {
//        className = className.substring(offset + CLASSFILE_STORAGE.length());
//      }
//    }
    offset = className.lastIndexOf('/');
    if (offset > 0)  {
      relpathname = className.substring(0, offset + 1);
      className = className.substring(offset + 1);
    }
    
    // get the location of the jar file (where we will extract the class files to)
    String jarpathname = jarfile.getAbsolutePath();
    offset = jarpathname.lastIndexOf('/');
    if (offset > 0) {
      jarpathname = jarpathname.substring(0, offset + 1);
    }
    
    JarFile jar = new JarFile(jarfile.getAbsoluteFile());
    Enumeration enumEntries = jar.entries();

    // look for specified class file in the jar
    while (enumEntries.hasMoreElements()) {
      JarEntry file = (JarEntry) enumEntries.nextElement();
      String fullname = file.getName();

      if (fullname.equals(relpathname + className)) {
//        String fullpath = jarpathname + relpathname;
        String fullpath = jarpathname + CLASSFILE_STORAGE + "/" + relpathname;
        File fout = new File(fullpath + className);
        // skip if file already exists
        if (fout.isFile()) {
          printStatusMessage("File '" + className + "' already created");
        } else {
          // make sure to create the entire dir path needed
          File relpath = new File(fullpath);
          if (!relpath.isDirectory()) {
            relpath.mkdirs();
          }

          // extract the file to its new location
          InputStream istream = jar.getInputStream(file);
          FileOutputStream fos = new FileOutputStream(fout);
          while (istream.available() > 0) {
            // write contents of 'istream' to 'fos'
            fos.write(istream.read());
          }
        }
        return;
      }
    }
    
    printStatusError("'" + className + "' not found in " + jarfile.getAbsoluteFile());
  }
  
  private static String initDanfigInfo() {
    // initialize replacement config info
    String content = "#! DANALYZER SYMBOLIC EXPRESSION LIST" + NEWLINE;
    content += "# DANLAUNCHER_VERSION" + NEWLINE;
    content += "IPAddress: localhost" + NEWLINE;
    content += "DebugPort: " + debugPort + NEWLINE;
    content += "DebugMode: TCPPORT" + NEWLINE;
    content += "DebugFlags: WARN SOLVE BRANCH CALLS" + NEWLINE;
    content += "TriggerOnCall: 0" + NEWLINE;
    content += "TriggerOnReturn: 0" + NEWLINE;
    content += "TriggerOnInstr: 0" + NEWLINE;
    content += "TriggerOnError: 0" + NEWLINE;
    content += "TriggerOnException: 0" + NEWLINE;
    content += NEWLINE;
    return content;
  }
  
  private static void readSymbolicList() {

    boolean needChange = false;
    boolean noChange = false;
    
    String content = initDanfigInfo();
    
    File file = new File(projectPathName + "danfig");
    if (!file.isFile()) {
      printCommandError("danfig file not found at path: " + projectPathName);
      printCommandMessage("No symbolic parameters");
      needChange = true;
    } else {
      try {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        dbtable.initSymbolic();
        printCommandMessage("Symbolic parameters:");
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("# DANLAUNCHER_VERSION")) {
            printCommandMessage("danfig file was generated by danlauncher");
            noChange = true;
          } else if (line.startsWith("Symbolic:")) {
//            line = line.substring("Symbolic:".length()).trim();
            String word[] = line.substring("Symbolic:".length()).trim().split(",");
            if (word.length < 2) {
              printCommandError("ERROR: invalid symbolic definition - " + line);
              return;
            }
            String symname = word[1].trim() + "_" + word[0].trim().replace(".","/");
            addSymbVariable(word[0].trim().replace(".","/"), "---", "---", word[1].trim(), "0", "0");
            printCommandMessage(" - " + symname);

            // add entry to list 
            dbtable.addSymbolic(symname);
          } else if (!line.startsWith("Constraint:")) {
            line = "";
            if (!line.startsWith("#")) {
              needChange = true;
            }
          }
          if (!line.isEmpty()) {
            content += line + NEWLINE;
          }
        }
        fileReader.close();
      } catch (IOException ex) {
        printCommandError(ex.getMessage());
      }
    }

    // if a change is needed, rename the old file and write the modified file back
    if (needChange && !noChange) {
      printCommandMessage("Updating danfig file");
      
      // rename current file so we can modify it
      file.renameTo(new File(projectPathName + "danfig.save"));
      
      // create the new file
      saveProjectTextFile("danfig", content);
    }
  }

  private static void saveProjectTextFile(String filename, String content) {
    String path = projectPathName;
    
    // make sure all dir paths exist
    int offset = filename.lastIndexOf("/");
    if (offset > 0) {
      String newpathname = filename.substring(0, offset);
      File newpath = new File(projectPathName + newpathname);
      if (!newpath.isDirectory()) {
        newpath.mkdirs();
      }
    }
    
    // delete file if it already exists
    File file = new File(projectPathName + filename);
    if (file.isFile()) {
      file.delete();
    }
    
    // create a new file and copy text contents to it
    BufferedWriter bw = null;
    try {
      FileWriter fw = new FileWriter(file);
      bw = new BufferedWriter(fw);
      bw.write(content);
    } catch (IOException ex) {
      printCommandError(ex.getMessage());
    } finally {
      if (bw != null) {
        try {
          bw.close();
        } catch (IOException ex) {
          printCommandError(ex.getMessage());
        }
      }
    }
  }
  
  /**
   * This performs the actions to take upon completion of the thread command.
   */
  private class ThreadTermination implements ThreadLauncher.ThreadAction {

    @Override
    public void allcompleted(ThreadLauncher.ThreadInfo threadInfo) {
      // restore the stdout and stderr
      System.out.flush();
      System.err.flush();
      System.setOut(STANDARD_OUT);
      System.setErr(STANDARD_ERR);
    }

    @Override
    public void jobprestart(ThreadLauncher.ThreadInfo threadInfo) {
//      printCommandMessage("jobprestart - job " + threadInfo.jobid + ": " + threadInfo.jobname);
    }

    @Override
    public void jobstarted(ThreadLauncher.ThreadInfo threadInfo) {
      printCommandMessage("jobstart - " + threadInfo.jobname + ": pid " + threadInfo.pid);
    }
        
    @Override
    public void jobfinished(ThreadLauncher.ThreadInfo threadInfo) {
      printCommandMessage("jobfinished - " + threadInfo.jobname + ": status = " + threadInfo.exitcode);
      switch (threadInfo.exitcode) {
        case 0:
          if (!isServerType) {
            // TODO: need to get the actual cost here
            addSolution(inputAttempt, System.currentTimeMillis() - elapsedStart);
          }
          printStatusMessage(threadInfo.jobname + " command (pid " + threadInfo.pid + ") completed successfully");
          break;
        case 137:
          printStatusMessage(threadInfo.jobname + " command terminated with SIGKILL");
          break;
        case 143:
          printStatusMessage(threadInfo.jobname + " command terminated with SIGTERM");
          break;
        default:
          printStatusMessage("Failure executing command: " + threadInfo.jobname);
          break;
      }
      
      // disable stop key
      mainFrame.getButton("BTN_STOPTEST").setEnabled(false);
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (isTabSelection_CALLGRAPH()) {
        if (CallGraph.updateCallGraph(graphMode, false)) {
//          mainFrame.repack();
        }
      }
    }
  }

  private class DebugInputListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // read & process next message
      if (udpThread != null) {
        String message = udpThread.getNextMessage();
        if (message != null) {
          DebugLogger.processMessage(message);
        }
      }
    }
  }

}
