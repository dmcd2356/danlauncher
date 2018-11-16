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
import panels.BytecodeGraph;
import panels.DatabaseTable;
import panels.DebugLogger;
import panels.ParamTable;
import panels.SymbolTable;
import panels.SolutionTable;
import callgraph.CallGraph;
import util.CommandLauncher;
import util.ThreadLauncher;
import util.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
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
    DEBUG_FLAGS,      // the debug flags enabled
  }
  
  private enum ElapsedMode { OFF, RUN, RESET }

  // tab panel selections
  private enum PanelTabs { COMMAND, DATABASE, BYTECODE, BYTEFLOW, LOG, CALLGRAPH }
  
  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

  private static final GuiControls mainFrame = new GuiControls();
  private static final GuiControls graphSetupFrame = new GuiControls();
  private static final GuiControls debugSetupFrame = new GuiControls();
  private static CallGraph       callGraph;
  private static BytecodeViewer  bytecodeViewer;
  private static BytecodeGraph   bytecodeGraph;
  private static DebugLogger     debugLogger;
  private static ThreadLauncher  threadLauncher;
  private static DatabaseTable   dbtable;
  private static ParamTable      localVarTbl;
  private static SymbolTable     symbolTbl;
  private static SolutionTable   solutionTbl;
  private static NetworkServer   udpThread = null;
  private static NetworkListener networkListener = null;
  private static DebugInputListener inputListener;
  private static Visitor         makeConnection;
  private static Logger          commandLogger;
  private static JFileChooser    fileSelector;
  private static JComboBox       mainClassCombo;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static JCheckBoxMenuItem isServerTypeMenuItem;
  private static JCheckBoxMenuItem loadDanfigMenuItem;
  private static JMenuBar        launcherMenuBar;
  private static Timer           debugMsgTimer;
  private static Timer           graphTimer;
  private static long            elapsedStart;
  private static GraphHighlight  graphMode;
  private static String          projectPathName;
  private static String          projectName;
  private static int             debugPort;
  private static int             tabIndex = 0;
  private static String          inputAttempt = "";
  private static String          debugFlags = "";
  private static String          clientPort = "";
  private static String          javaHome;
  private static boolean         mainClassInitializing;
  private static boolean         runMode = false;
  
  private static final HashMap<String, JCheckBoxMenuItem> menuCheckboxes = new HashMap<>();
  private static final HashMap<String, JMenuItem> menuItems = new HashMap<>();
  private static ArrayList<String> fullMethList = new ArrayList<>();
  private static ArrayList<String> classList = new ArrayList<>();
  private static HashMap<String, ArrayList<String>>  clsMethMap = new HashMap<>(); // maps the list of methods to each class
  private static final HashMap<String, Integer>   tabSelect = new HashMap<>();
  private static final HashMap<String, FontInfo>     bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo>     debugFontTbl = new HashMap<>();
  private static final HashMap<PanelTabs, Component> tabbedPanels = new HashMap<>();
  private static ArrayList<MethodHistoryInfo> bytecodeHistory = new ArrayList<>();
  
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
  
  private static class MethodHistoryInfo {
    public String className;
    public String methodName;
    
    public MethodHistoryInfo(String cls, String meth) {
      className = cls;
      methodName = meth;
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
    graphMode = GraphHighlight.NONE;
    mainClassInitializing = false;

    // check for the global danlauncher properties file and init default values if not found
    systemProps = new PropertiesFile(HOMEPATH + "/" + PROJ_CONFIG, "SYSTEM_PROPERTIES");
    String projectPath = systemProps.getPropertiesItem(SystemProperties.PROJECT_PATH.toString(), HOMEPATH);
    String maxLogLength = systemProps.getPropertiesItem(SystemProperties.MAX_LOG_LENGTH.toString(), "500000");
    String myport = systemProps.getPropertiesItem(SystemProperties.DEBUG_PORT.toString(), "5000");
    if (myport == null) {
      myport = "5000";
    }
    debugPort = Integer.parseUnsignedInt(myport);
    javaHome = systemProps.getPropertiesItem(SystemProperties.JAVA_HOME.toString(), JAVA_HOME);
    
    // we need a filechooser and initialize it to the project path
    fileSelector = new JFileChooser();
    fileSelector.setCurrentDirectory(new File(projectPath));

    // create the main panel and controls
    createMainPanel();

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
}

  public static boolean isInstrumentedMethod(String methName) {
    return fullMethList.contains(methName);
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
  
  public static void printStatusClear() {
    mainFrame.getTextField("TXT_MESSAGES").setText("                   ");
  }
  
  public static void printStatusMessage(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText(message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  public static void printStatusError(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText("ERROR: " + message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  public static void printCommandMessage(String message) {
    commandLogger.printLine(message);
  }
  
  public static void printCommandError(String message) {
    commandLogger.printLine(message);
  }
  
  public static boolean isTabSelection(String select) {
    JTabbedPane tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");
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
    ArrayList<Integer> branchMarks = bytecodeViewer.highlightBranch(start, branch);
    bytecodeGraph.drawGraphHighlights(branchMarks);
  }
  
  public static void addLocalVariable(String name, String type, String slot, String start, String end) {
    localVarTbl.addEntry(name, type, slot, start, end);
  }

  public static String addSymbVariable(String meth, String name, String type, String slot,
                                     String start, String end, int opstrt, int oplast) {
    return symbolTbl.addEntry(meth, name, type, slot, start, end, opstrt, oplast);
  }

  public static String addSymbVariableByLine(String meth, String name, String type, String slot,
                                           int start, int end) {
    return symbolTbl.addEntryByLine(meth, name, type, slot, start, end);
  }

  public static void addSymbConstraint(String id, String type, String value) {
    symbolTbl.addConstraint(id, type, value);
  }
  
  public static void updateDanfigFile() {
    // init the background stuff
    String content = initDanfigInfo();
    
    // now add in the latest and greatest symbolic defs
    boolean validConstr = false;
    if (symbolTbl.getSymbolicEntry(0) != null) {
      content += "#" + Utils.NEWLINE;
      content += "# SYMBOLICS" + Utils.NEWLINE;
      content += "# Symbolic: <symbol_id> <method> <slot> <start range> <end range> <type>" + Utils.NEWLINE;
      for (int ix = 0; true; ix++) {
        SymbolTable.TableListInfo entry = symbolTbl.getSymbolicEntry(ix);
        if (entry == null) {
          break;
        }
        if (!entry.constraints.isEmpty()) {
          validConstr = true;
        }
        content += "Symbolic: " +  entry.name + " " + entry.method + " " + entry.slot + " " +
            entry.opStart + " " + entry.opEnd + " " + entry.type + Utils.NEWLINE;
      }
    }

    // and now the user-defined constraints
    if (validConstr) {
      content += "#" + Utils.NEWLINE;
      content += "# CONSTRAINTS" + Utils.NEWLINE;
      content += "# Constraint: <symbol_id>  <EQ|NE|GT|GE|LT|LE> <value>" + Utils.NEWLINE;
      for (int ix = 0; true; ix++) {
        SymbolTable.TableListInfo entry = symbolTbl.getSymbolicEntry(ix);
        if (entry == null) {
          break;
        }
        for (int conix = 0; conix < entry.constraints.size(); conix++) {
          SymbolTable.ConstraintInfo constraint = entry.constraints.get(conix);
          content += "Constraint: " +  entry.name + " " +
              constraint.comptype + " " + constraint.compvalue + Utils.NEWLINE;
        }
      }
    }

    // now save to the file
    Utils.saveTextFile(projectPathName + "danfig", content);
    printStatusMessage("Updated danfig file: " + symbolTbl.getSize() + " symbolic entries");
  }

  public static void setBytecodeSelections(String classSelect, String methodSelect) {
    String curClass = (String) classCombo.getSelectedItem();
    String curMeth = (String) methodCombo.getSelectedItem();

    if (!curClass.equals(classSelect) && !curMeth.equals(methodSelect)) {
      // push current entry onto history stack
      bytecodeHistory.add(new MethodHistoryInfo(curClass, curMeth));
      mainFrame.getButton("BTN_BACK").setVisible(true);
            
      // make new selections
      classCombo.setSelectedItem(classSelect);
      methodCombo.setSelectedItem(methodSelect);
    }
  }

  public static void runBytecodeViewer(String classSelect, String methodSelect) {
      // check if bytecode for this method already displayed
      if (bytecodeViewer.isMethodDisplayed(classSelect, methodSelect)) {
        // just clear any highlighting
        bytecodeViewer.highlightClear();
        printStatusMessage("Bytecode already loaded");
      } else {
        String content;
        String fname = projectPathName + JAVAPFILE_STORAGE + "/" + classSelect + ".txt";
        File file = new File(fname);
        if (file.isFile()) {
          // use the javap file already generated
          content = Utils.readTextFile(fname);
        } else {
          // need to run javap to generate the bytecode source
          content = generateBytecode(classSelect, methodSelect);
          if (content == null) {
            return;
          }
        }

        // if successful, load it in the Bytecode viewer
        runBytecodeParser(classSelect, methodSelect, content);
      }
      
      // swich tab to show bytecode
      String selected = getTabSelect();
      setTabSelect(PanelTabs.BYTECODE.toString());
      
      // this is an attempt to get the bytecode graph to update its display properly by
      // switching to different panel and back again
      String byteflowTab = PanelTabs.BYTEFLOW.toString();
      if (selected.equals(byteflowTab)) {
        setTabSelect(byteflowTab);
      }
  }

  public void createMainPanel() {
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
//    solutionList = new DefaultListModel();
    
    // create the frame
    JFrame frame = mainFrame.newFrame("danlauncher", 1200, 800, FrameSize.FULLSCREEN);
    frame.addWindowListener(new Window_MainListener());

    panel = null; // this creates the entries in the main frame
    mainFrame.makePanel      (panel, "PNL_MESSAGES" , "Status"    , LEFT, true);
    mainFrame.makePanel      (panel, "PNL_CONTAINER", ""          , LEFT, true);
    mainFrame.makeTabbedPanel(panel, "PNL_TABBED"   , ""          , LEFT, true);

    panel = "PNL_MESSAGES";
    mainFrame.makeTextField (panel, "TXT_MESSAGES"  , ""          , LEFT, true, "", 150, false);

    panel = "PNL_CONTAINER";
    mainFrame.makePanel     (panel, "PNL_CONTROLS"  , "Controls"  , LEFT, false, 600, 170);
    mainFrame.makePanel     (panel, "PNL_SOLUTIONS" , "Solutions" , LEFT, true, 400, 170);
    mainFrame.makePanel     (panel, "PNL_BYTECODE"  , "Bytecode"  , LEFT, true);

    panel = "PNL_SOLUTIONS";
    mainFrame.makeScrollTable(panel, "TBL_SOLUTIONS", "");

    panel = "PNL_BYTECODE";
    mainFrame.makeCombobox  (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    mainFrame.makeCombobox  (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    mainFrame.makeButton    (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, false);
    mainFrame.makeButton    (panel, "BTN_BACK"     , "Back"        , LEFT, true);

    panel = "PNL_CONTROLS";
    mainFrame.makeCombobox  (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    mainFrame.makeButton    (panel, "BTN_RUNTEST"  , "Run"         , LEFT, false);
    mainFrame.makeButton    (panel, "BTN_STOPTEST" , "STOP"        , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_ARGLIST"  , ""            , LEFT, true, "", 40, true);
    mainFrame.makeButton    (panel, "BTN_SEND"     , "Post"        , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_PORT"     , ""            , LEFT, false, "8080", 8, true);
    mainFrame.makeTextField (panel, "TXT_INPUT"    , ""            , LEFT, true, "", 40, true);
    mainFrame.makeButton    (panel, "BTN_SOLVER"   , "Solver"      , LEFT, false);

    // set color of STOP button
    mainFrame.getButton("BTN_STOPTEST").setBackground(Color.pink);

    // disable the back button initially
    mainFrame.getButton("BTN_BACK").setVisible(false);
            
    // setup the handlers for the controls
    mainFrame.getCombobox("COMBO_CLASS").addActionListener(new Action_BytecodeClassSelect());
    mainFrame.getCombobox("COMBO_METHOD").addActionListener(new Action_BytecodeMethodSelect());
    mainFrame.getCombobox("COMBO_MAINCLS").addActionListener(new Action_MainClassSelect());
    mainFrame.getButton("BTN_BYTECODE").addActionListener(new Action_RunBytecode());
    mainFrame.getButton("BTN_BACK").addActionListener(new Action_RunPrevBytecode());
    mainFrame.getButton("BTN_RUNTEST").addActionListener(new Action_RunTest());
    mainFrame.getButton("BTN_SOLVER").addActionListener(new Action_RunSolver());
    mainFrame.getButton("BTN_SEND").addActionListener(new Action_SendHttpMessage());
    mainFrame.getButton("BTN_STOPTEST").addActionListener(new Action_StopExecution());
    mainFrame.getTextField("TXT_ARGLIST").addActionListener(new Action_UpdateArglist());
    mainFrame.getTextField("TXT_ARGLIST").addFocusListener(new Focus_UpdateArglist());
    mainFrame.getTextField("TXT_PORT").addActionListener(new Action_UpdatePort());
    mainFrame.getTextField("TXT_PORT").addFocusListener(new Focus_UpdatePort());

    // add a menu to the frame
    launcherMenuBar = new JMenuBar();
    mainFrame.getFrame().setJMenuBar(launcherMenuBar);
    JMenu menuProject = launcherMenuBar.add(new JMenu("Project"));
    JMenu menuDebug   = launcherMenuBar.add(new JMenu("Debug"));
    JMenu menuGraphs  = launcherMenuBar.add(new JMenu("Graphs"));

    JMenu menu = menuProject; // selections for the Project Menu
    addMenuItem     (menu, "MENU_SEL_JAR"    , "Select Jar file", new Action_SelectJarFile());
    menu.addSeparator();
    addMenuCheckbox (menu, "MENU_SERVER_TYPE", "Input using Post (server app)", true,
                      new ItemListener_EnablePost());
    addMenuCheckbox (menu, "MENU_LOAD_DANFIG", "Load symbolics from danfig", true, null);
    addMenuItem     (menu, "MENU_SAVE_DANFIG", "Update danfig file", new Action_UpdateDanfigFile());
    menu.addSeparator();
    addMenuItem     (menu, "MENU_CLR_DBASE"  , "Clear DATABASE", new Action_ClearDatabase());
    addMenuItem     (menu, "MENU_CLR_LOG"    , "Clear LOG", new Action_ClearLog());
    addMenuItem     (menu, "MENU_CLR_SOL"    , "Clear SOLUTIONS", new Action_ClearSolutions());
    menu = menuDebug; // selections for the Debug Menu
    addMenuItem     (menu, "MENU_SETUP_DBUG" , "Debug Setup", new Action_DebugSetup());
    menu = menuGraphs; // selections for the Graphs Menu
    addMenuItem     (menu, "MENU_SETUP_GRAF" , "Callgraph Setup", new Action_CallgraphSetup());
    menu.addSeparator();
    addMenuItem     (menu, "MENU_SAVE_PNG"   , "Save Callgraph (PNG)", new Action_SaveGraphPNG());
    addMenuItem     (menu, "MENU_SAVE_JSON"  , "Save Callgraph (JSON)", new Action_SaveGraphJSON());
    addMenuItem     (menu, "MENU_SAVE_BCODE" , "Save Bytecode Graph", new Action_SaveByteFlowGraph());

    // setup access to menu controls
    isServerTypeMenuItem = getMenuCheckbox ("MENU_SERVER_TYPE");
    loadDanfigMenuItem = getMenuCheckbox ("MENU_LOAD_DANFIG");
    getMenuItem("MENU_SETUP_DBUG").setEnabled(false);
    getMenuItem("MENU_SAVE_PNG").setEnabled(false);
    getMenuItem("MENU_SAVE_JSON").setEnabled(false);
    getMenuItem("MENU_SAVE_BCODE").setEnabled(false);
    
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

    // create the the setup frames, but initially hide them
    createGraphSetupPanel();
    createDebugSelectPanel();
  
    // display the frame
    mainFrame.display();

    // create the panel classes
    commandLogger = new Logger(new JTextArea(), PanelTabs.COMMAND.toString(), null);
    debugLogger = new DebugLogger(PanelTabs.LOG.toString());
    bytecodeViewer = new BytecodeViewer(PanelTabs.BYTECODE.toString());
    bytecodeGraph = new BytecodeGraph(bytecodeViewer);
    callGraph = new CallGraph(PanelTabs.CALLGRAPH.toString());
    dbtable = new DatabaseTable(PanelTabs.DATABASE.toString());

    // wrap the bytecode logger in another pane to prevent line wrapping on a JTextPane
    JPanel noWrapBytecodePanel = new JPanel(new BorderLayout());
    noWrapBytecodePanel.add(bytecodeViewer.getTextPane());

    // create a split panel for sharing the local variables and symbolic parameters in a tab
    JTable paramList = new JTable();        // the bytecode param list
    JTable symbolList = new JTable();       // the symbolic parameter list
    String splitName = "SPLIT_PANE1";
    JSplitPane splitPane1 = mainFrame.makeSplitPane(splitName, false, 0.5);
    mainFrame.addSplitComponent(splitName, 0, "TBL_PARAMLIST", paramList, true);
    mainFrame.addSplitComponent(splitName, 1, "TBL_SYMBOLICS", symbolList, true);
//    paramList.setBorder(new TitledBorder("Local variables"));
//    sybolList.setBorder(new TitledBorder("Symbolics defined"));

    // now we're going to combine the BYTECODE entry with the parameter/symbolics split panel
    splitName = "SPLIT_MAIN";
    JSplitPane splitMain = mainFrame.makeSplitPane(splitName, true, 0.5);
    mainFrame.addSplitComponent(splitName, 0, "BYTECODE"  , noWrapBytecodePanel, true);
    mainFrame.addSplitComponent(splitName, 1, "PNL_PARAMS", splitPane1, false);
    
    // add the tabbed message panels and a listener to detect when a tab has been selected
    JTabbedPane tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");
    addPanelToTab(tabPanel, PanelTabs.COMMAND  , commandLogger.getPanel(), true);
    addPanelToTab(tabPanel, PanelTabs.DATABASE , dbtable.getPanel(), true);
    addPanelToTab(tabPanel, PanelTabs.BYTECODE , splitMain, false);
    addPanelToTab(tabPanel, PanelTabs.BYTEFLOW , bytecodeGraph.getPanel(), true);
    addPanelToTab(tabPanel, PanelTabs.LOG      , debugLogger.getPanel(), true);
    addPanelToTab(tabPanel, PanelTabs.CALLGRAPH, callGraph.getPanel(), true);
    tabPanel.addChangeListener(new Change_TabPanelSelect());

    // update divider locations in split frame now that it has been placed (and the dimensions are set)
    mainFrame.setSplitDivider("SPLIT_MAIN", 0.6);
    mainFrame.setSplitDivider("SPLIT_PANE1", 0.6);

    // init the local variable and symbolic list tables
    localVarTbl = new ParamTable(paramList);
    symbolTbl   = new SymbolTable(symbolList);
    solutionTbl = new SolutionTable(mainFrame.getTable("TBL_SOLUTIONS"));
  }

  private class Window_MainListener extends java.awt.event.WindowAdapter {
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
      if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
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

      // enable the debug setup selection
      getMenuItem("MENU_SETUP_DBUG").setEnabled(true);
    }
  }

  private class Action_UpdateDanfigFile implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateDanfigFile();
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
      clearDebugLogger();
    }
  }

  private class Action_ClearSolutions implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // clear out the debug logger
      solutionTbl.clear();
    }
  }

  private class Action_CallgraphSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      graphSetupFrame.display();
    }
  }

  private class Action_DebugSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      debugSetupFrame.display();
    }
  }
  
  private class Action_SaveGraphPNG implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveCallGraph("png");
    }
  }
      
  private class Action_SaveGraphJSON implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveCallGraph("json");
    }
  }
  
  private class Action_SaveByteFlowGraph implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveByteFlowGraph();
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
    }
  }
  
  private class Action_MainClassSelect implements ActionListener {
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
      boolean isServerType = ie.getStateChange() == ItemEvent.SELECTED;
      
      // enable/disable "send to port" controls accordingly
      mainFrame.getButton("BTN_SEND").setEnabled(isServerType);
      mainFrame.getTextField("TXT_PORT").setEnabled(isServerType);

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
      runBytecodeViewer(classSelect, methodSelect);
    }
  }

  private class Action_RunPrevBytecode implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      if (!bytecodeHistory.isEmpty()) {
        MethodHistoryInfo entry = bytecodeHistory.remove(bytecodeHistory.size() - 1);
        runBytecodeViewer(entry.className, entry.methodName);
        
        // update the selections
        classCombo.setSelectedItem(entry.className);
        methodCombo.setSelectedItem(entry.methodName);

        // disable back button if no more
        if (bytecodeHistory.isEmpty()) {
          mainFrame.getButton("BTN_BACK").setVisible(false);
        }
      }
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
        printCommandMessage("Terminating job " + threadInfo.jobid + ": pid " + threadInfo.pid);

        // send SIGTERM to process
        String[] command = { "term", "-15", threadInfo.pid.toString() };
        CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
        commandLauncher.start(command, null);
        
        // give it a few seconds and if it hasn't terminated, send SIGKILL
        try {
          Thread.sleep(3000);
        } catch (InterruptedException ex) { }
        if (runMode) {
          printCommandMessage("Killing job " + threadInfo.jobid + ": pid " + threadInfo.pid);
          String[] command2 = { "kill", "-9", threadInfo.pid.toString() };
          commandLauncher.start(command2, null);
        }
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
    
  private void createGraphSetupPanel() {
    if (graphSetupFrame.isValidFrame()) {
      return;
    }

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT   = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT  = GuiControls.Orient.RIGHT;
    
    // create the frame
    JFrame frame = graphSetupFrame.newFrame("Graph Setup", 350, 250, FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_GraphSetupListener());
  
    panel = null;
    graphSetupFrame.makePanel (panel, "PNL_HIGHLIGHT", "Graph Highlight"   , LEFT, false);
    graphSetupFrame.makePanel (panel, "PNL_ADJUST"   , ""                  , LEFT, true);

    panel = "PNL_ADJUST";
    graphSetupFrame.makeLabel (panel, "LBL_THREADS"  , "Threads: 0"        , CENTER, true);
    graphSetupFrame.makeLineGap(panel);
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
    graphSetupFrame.getRadiobutton("RB_THREAD"  ).addActionListener(new Action_GraphModeThread());
    graphSetupFrame.getRadiobutton("RB_ELAPSED" ).addActionListener(new Action_GraphModeElapsed());
    graphSetupFrame.getRadiobutton("RB_INSTRUCT").addActionListener(new Action_GraphModeInstruction());
    graphSetupFrame.getRadiobutton("RB_ITER"    ).addActionListener(new Action_GraphModeIteration());
    graphSetupFrame.getRadiobutton("RB_STATUS"  ).addActionListener(new Action_GraphModeStatus());
    graphSetupFrame.getRadiobutton("RB_NONE"    ).addActionListener(new Action_GraphModeNone());
    graphSetupFrame.getButton("BTN_TH_UP").addActionListener(new Action_ThreadUp());
    graphSetupFrame.getButton("BTN_TH_DN").addActionListener(new Action_ThreadDown());
    graphSetupFrame.getButton("BTN_RG_UP").addActionListener(new Action_RangeUp());
    graphSetupFrame.getButton("BTN_RG_DN").addActionListener(new Action_RangeDown());
}

  private class Window_GraphSetupListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      graphSetupFrame.hide();
    }
  }

  private class Action_GraphModeThread implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.THREAD);
        
      // enable the thread controls when this is selected
      setThreadControls(true);
    }
  }

  private class Action_GraphModeElapsed implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.TIME);
    }
  }

  private class Action_GraphModeInstruction implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.INSTRUCTION);
    }
  }

  private class Action_GraphModeIteration implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.ITERATION);
    }
  }

  private class Action_GraphModeStatus implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.STATUS);
    }
  }

  private class Action_GraphModeNone implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(GraphHighlight.NONE);
    }
  }

  private class Action_ThreadUp implements ActionListener {
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
        if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
          CallGraph.updateCallGraph(GraphHighlight.THREAD, true);
        }
      }
    }
  }

  private class Action_ThreadDown implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
      int value = Integer.parseInt(label.getText().trim());
      if (value > 0) {
        value--;
        label.setText("" + value);
          
        CallGraph.setThreadSelection(value);

        // if CallGraph is selected, update the graph
        if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
          CallGraph.updateCallGraph(GraphHighlight.THREAD, true);
        }
      }
    }
  }

  private class Action_RangeUp implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
      int step = Integer.parseInt(label.getText().trim());
      if (step < 20) {
        step++;
        label.setText("" + step);
          
        CallGraph.setRangeStepSize(step);

        // if CallGraph is selected, update the graph
        if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
          CallGraph.updateCallGraph(graphMode, true);
        }
      }
    }
  }

  private class Action_RangeDown implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
      int step = Integer.parseInt(label.getText().trim());
      if (step > 1) {
        step--;
        label.setText("" + step);
          
        CallGraph.setRangeStepSize(step);

        // if CallGraph is selected, update the graph
        if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
          CallGraph.updateCallGraph(graphMode, true);
        }
      }
    }
  }

  private void createDebugSelectPanel() {
    if (debugSetupFrame.isValidFrame()) {
      return;
    }

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT   = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT  = GuiControls.Orient.RIGHT;

    // create the frame
    JFrame frame = debugSetupFrame.newFrame("Debug Setup", 350, 250, FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_DebugSetupListener());
  
    panel = null;

    // create the entries in the main frame
    debugSetupFrame.makePanel (panel, "PNL_DBGFLAGS", "Debug Flags" , LEFT  , false);
    debugSetupFrame.makePanel (panel, "PNL_DEBUGOUT", "Debug Output", RIGHT , true);
    
    // now add controls to the sub-panels
    panel = "PNL_DBGFLAGS";
    debugSetupFrame.makeCheckbox(panel, "DBG_WARNING" , "Warnings"      , LEFT, false , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_CALL"    , "Call/Return"   , LEFT, true  , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_INFO"    , "Info"          , LEFT, false , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_THREAD"  , "Thread"        , LEFT, true  , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_COMMAND" , "Commands"      , LEFT, false , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_STACK"   , "Stack"         , LEFT, true  , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_AGENT"   , "Agent"         , LEFT, false , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_LOCALS"  , "Locals"        , LEFT, true  , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_BRANCH"  , "Branch"        , LEFT, false , 0);
    debugSetupFrame.makeCheckbox(panel, "DBG_SOLVER"  , "Solver"        , LEFT, true  , 0);

    panel = "PNL_DEBUGOUT";
    debugSetupFrame.makeTextField(panel, "TEXT_PORT"   , "Port"         , LEFT, true, debugPort + "", 8, true);
  }
  
  private class Window_DebugSetupListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      // save current settings
      String oldDebugFlags = debugFlags;
      int oldDebugPort = debugPort;
      
      // get the current debug selections
      debugFlags = "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_WARNING").isSelected() ? "WARN "    : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_CALL"   ).isSelected() ? "CALLS "   : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_INFO"   ).isSelected() ? "INFO "    : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_THREAD" ).isSelected() ? "THREAD "  : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_COMMAND").isSelected() ? "COMMAND " : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_STACK"  ).isSelected() ? "STACK "   : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_AGENT"  ).isSelected() ? "AGENT "   : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_LOCALS" ).isSelected() ? "LOCAL "   : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_BRANCH" ).isSelected() ? "BRANCH "  : "";
      debugFlags += debugSetupFrame.getCheckbox("DBG_SOLVER" ).isSelected() ? "SOLVE "   : "";
      debugFlags = debugFlags.trim();
      // update properties file
      if (projectProps != null) {
        projectProps.setPropertiesItem(ProjectProperties.DEBUG_FLAGS.toString(), debugFlags);
      }

      // get the current port selection (make sure it is valid)
      String portstr = debugSetupFrame.getTextField("TEXT_PORT").getText();
      int portint = 0;
      try {
        portint = Integer.parseUnsignedInt(portstr);
        if (portint < 100 || portint > 65535) {
          portint = 0;
        }
      } catch (NumberFormatException ex) { }
      if (portint <= 0) {
        // bad - restore previous value
        printStatusError("Invalid value for port: " + portstr);
        debugSetupFrame.getTextField("TEXT_PORT").setText(debugPort + "");
      } else {
        // valid - save value and update properties file
        debugPort = portint;
        systemProps.setPropertiesItem(SystemProperties.DEBUG_PORT.toString(), debugPort + "");
      }
      
      // now put this frame back into hiding
      debugSetupFrame.hide();
      
      if (oldDebugPort == debugPort && oldDebugFlags.equals(debugFlags)) {
        System.out.println("No changes to debug settings");
        return;
      }
      
      // ask user if he wants to update the danfig file so his changes will take effect
      // the next time he runs the application
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Do you wish to update the 'danfig' file" + Utils.NEWLINE +
        "with your changes so that they will take" + Utils.NEWLINE +
        "effect the next time the application is run?",
        "Update danfig", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        updateDanfigFile();
      }
    }
  }

  private static void addMenuItem(JMenu menucat, String id, String title, ActionListener action) {
    if (menuItems.containsKey(id)) {
      System.err.println("Menu Item '" + id + "' already defined!");
      return;
    }
    JMenuItem item = new JMenuItem(title);
    item.addActionListener(action);
    menucat.add(item);
    menuItems.put(id, item);
  }

  private static JMenuItem getMenuItem(String name) {
    return menuItems.get(name);
  }
  
  private static void addMenuCheckbox(JMenu menucat, String id, String title, boolean dflt, ItemListener action) {
    if (menuCheckboxes.containsKey(id)) {
      System.err.println("Menu Checkbox '" + id + "' already defined!");
      return;
    }
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(title);
    item.setSelected(dflt);
    if (action != null) {
      item.addItemListener(action);
    }
    menucat.add(item);
    menuCheckboxes.put(id, item);
  }
  
  private static JCheckBoxMenuItem getMenuCheckbox(String name) {
    return menuCheckboxes.get(name);
  }
  
  private void addPanelToTab(JTabbedPane tabpane, PanelTabs tabname, Component panel, boolean scrollable) {
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
      tabpane.addTab(tabname.toString(), scrollPanel);
    } else {
      // or add the original pane to the tabbed pane
      tabpane.addTab(tabname.toString(), panel);
    }
    
    // save access to panel by name
    tabSelect.put(tabname.toString(), tabIndex++);
    tabbedPanels.put(tabname, panel);
  }

  private static Component getTabPanel(PanelTabs tabname) {
    if (!tabbedPanels.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }
    return tabbedPanels.get(tabname);
  }

  private static String getTabSelect() {
    JTabbedPane tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");
    if (tabPanel == null || tabSelect.isEmpty()) {
      return null;
    }

    int curTab = tabPanel.getSelectedIndex();
    for (HashMap.Entry pair : tabSelect.entrySet()) {
      if ((Integer) pair.getValue() == curTab) {
        return (String) pair.getKey();
      }
    }
    return null;
  }
  
  private static void setTabSelect(String tabname) {
    Integer index = tabSelect.get(tabname);
    if (index == null) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }

    JTabbedPane tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");
    tabPanel.setSelectedIndex(index);
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
      enable = isServerTypeMenuItem.isSelected();
    }
    mainFrame.getButton("BTN_SEND").setEnabled(enable);
    mainFrame.getTextField("TXT_PORT").setEnabled(enable);
  }  

  private static void setDebugFromProperties() {
    debugSetupFrame.getCheckbox("DBG_WARNING").setSelected(debugFlags.contains("WARN"));
    debugSetupFrame.getCheckbox("DBG_CALL"   ).setSelected(debugFlags.contains("CALLS"));
    debugSetupFrame.getCheckbox("DBG_INFO"   ).setSelected(debugFlags.contains("INFO"));
    debugSetupFrame.getCheckbox("DBG_THREAD" ).setSelected(debugFlags.contains("THREAD"));
    debugSetupFrame.getCheckbox("DBG_COMMAND").setSelected(debugFlags.contains("COMMAND"));
    debugSetupFrame.getCheckbox("DBG_STACK"  ).setSelected(debugFlags.contains("STACK"));
    debugSetupFrame.getCheckbox("DBG_AGENT"  ).setSelected(debugFlags.contains("AGENT"));
    debugSetupFrame.getCheckbox("DBG_LOCALS" ).setSelected(debugFlags.contains("LOCAL"));
    debugSetupFrame.getCheckbox("DBG_BRANCH" ).setSelected(debugFlags.contains("BRANCH"));
    debugSetupFrame.getCheckbox("DBG_SOLVER" ).setSelected(debugFlags.contains("SOLVE"));
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
    if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
      CallGraph.updateCallGraph(graphMode, false);
    }
  }
  
  private static void clearDebugLogger() {
    // clear out the debug logger
    debugLogger.clear();

    // reset debug input from network in case some messages are pending and clear buffer
    if (udpThread != null) {
      udpThread.resetInput();
      udpThread.clear();
    }

    // clear the graphics panel
    CallGraph.clearGraphAndMethodList();
    if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
      CallGraph.updateCallGraph(GraphHighlight.NONE, false);
    }

    // force the highlight selection back to NONE
    setHighlightMode(GraphHighlight.NONE);

    // init thread selection in highlighting to OFF
    setThreadEnabled(false);
  }
  
  private static void saveCallGraph(String type) {
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
  
  private static void saveByteFlowGraph() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    Date date = new Date();
    String defaultName = dateFormat.format(date) + "callgraph";
    FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG Files", "png");
    String fileExtension = ".png";
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
      File pngFile = new File(basename + fileExtension);
      pngFile.delete();
      bytecodeGraph.saveAsImageFile(pngFile);
    }
  }
  
  private static void startDebugPort(String projectPath) {
    // disable timers while we are setting this up
    enableUpdateTimers(false);

    if (udpThread != null) {
      // server is already running, see if no change in parameters
      if (debugPort != udpThread.getPort()) {
        // port change: we have to close the current server so we can create a new one using a different port
        udpThread.exit();
        System.out.println("Changinng NetworkServer port from " + udpThread.getPort() + " to " + debugPort);
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
      udpThread = new NetworkServer(debugPort, true);
    } catch (IOException ex) {
      System.err.println("ERROR: unable to start NetworkServer. " + ex);
    }

    System.out.println("danlauncher receiving port: " + debugPort);
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
    System.out.println(classList.size() + " classes and " + fullMethList.size() + " methods found");
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
    updateProjectProperty("COMBO_MAINCLS");
    
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
//    GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
//  private static void updateElapsedTime() {
//    long elapsed = System.currentTimeMillis() - elapsedStart;
//    if (elapsed > 0) {
//      Integer msec = (int)(elapsed % 1000);
//      elapsed = elapsed / 1000;
//      Integer secs = (int)(elapsed % 60);
//      Integer mins = (int)(elapsed / 60);
//      String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
//                         ((secs < 10) ? "0" : "") + secs.toString(); // + "." +
//                         //((msec < 10) ? "00" : (msec < 100) ? "0" : "") + msec.toString();
////      mainFrame.getTextField("FIELD_ELAPSED").setText(timestamp);
//    }
//  }
  
  private static void initProjectProperties(String pathname) {
    // create the properties file if not already created
//    if (projectProps == null) {
      projectProps = new PropertiesFile(pathname + ".danlauncher", "PROJECT_PROPERTIES", commandLogger);
//    }

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
        printCommandMessage("Project updated " + ctlName + " to value: " + val);
        mainFrame.setInputControl(ctlName, ctlType, val);
      }
    }
    
    // other params not covered by the PROJ_PROP_TBL:
    String dflt = isServerTypeMenuItem.isSelected() ? "true" : "false";
    String val = projectProps.getPropertiesItem(ProjectProperties.IS_SERVER_TYPE.toString(), dflt);
    if (!val.equals(dflt)) {
      // update the GUI selection
      printCommandMessage("Project updated IS_SERVER_TYPE to value: " + val);
      isServerTypeMenuItem.setSelected(val.equals("true"));
    }

    // this uses the current setting of debugFlags as a default value, so if the entry is defined
    // in projectProperties it will use that, otherwise it will keep its current setting.
    String prevFlags = debugFlags;
    debugFlags = projectProps.getPropertiesItem(ProjectProperties.DEBUG_FLAGS.toString(), prevFlags);
    if (!prevFlags.equals(debugFlags)) {
      printCommandMessage("Project updated DEBUG_FLAGS to value: " + debugFlags);
    }
    setDebugFromProperties();
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
    
    // clear out the symbolic parameter list and the history list for bytecode viewer
    // as well as the bytecode data, byteflow graph, solutions and debug/call graph info.
    symbolTbl.clear();
    localVarTbl.clear("");
    solutionTbl.clear();
    bytecodeHistory.clear();
    bytecodeViewer.clear();
    bytecodeGraph.clear();
    clearDebugLogger();

    String content = initDanfigInfo();
    
    // read the symbolic parameter definitions from danfig file (if present)
    if (loadDanfigMenuItem.isSelected()) {
      readSymbolicList(content);
    }
  
    String localpath = "*";
    if (new File(projectPathName + "lib").isDirectory()) {
      localpath += ":lib/*";
    }
    if (new File(projectPathName + "libs").isDirectory()) {
      localpath += ":libs/*";
    }

    String mainclass = "danalyzer.instrumenter.Instrumenter";
    String classpath = DSEPATH + "danalyzer/dist/danalyzer.jar";
    classpath += ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar";
    classpath += ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar";
    classpath += ":" + localpath;

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
    return 0;
  }

  private void runTest(String arglist) {
    printStatusClear();

    String instrJarFile = projectName.substring(0, projectName.lastIndexOf(".")) + "-dan-ed.jar";
    
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
    if (new File(projectPathName + "lib").isDirectory()) {
      localpath += ":lib/*";
    }
    if (new File(projectPathName + "libs").isDirectory()) {
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

    if (!isServerTypeMenuItem.isSelected()) {
      inputAttempt = arglist;
      elapsedStart = System.currentTimeMillis();
    }

    printStatusMessage("Run command started...");
    
    // run the instrumented jar file
    String[] argarray = arglist.split("\\s+"); // need to seperate arg list into seperate entries
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass };
    ArrayList<String> cmdlist = new ArrayList(Arrays.asList(command));
    cmdlist.addAll(Arrays.asList(argarray));
    String[] fullcmd = new String[cmdlist.size()];
    fullcmd = cmdlist.toArray(fullcmd);

    runMode = true;
    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(fullcmd, projectPathName, "run_" + projectName, null);

    // disable the Run and Get Bytecode buttons until the code has terminated
    mainFrame.getButton("BTN_RUNTEST").setEnabled(false);
    mainFrame.getButton("BTN_BYTECODE").setEnabled(false);
    
    // allow user to terminate the test
    mainFrame.getButton("BTN_STOPTEST").setEnabled(true);
  }

  private static void runBytecodeParser(String classSelect, String methodSelect, String content) {
    bytecodeViewer.parseJavap(classSelect, methodSelect, content);
    if (bytecodeViewer.isValidBytecode()) {
      printStatusMessage("Successfully generated bytecode for method");
      bytecodeGraph.drawGraphNormal();
      getMenuItem("MENU_SAVE_BCODE").setEnabled(bytecodeGraph.isValid());
    } else {
      printStatusMessage("Generated class bytecode, but method not found");
    }
  }
  
  private static void executePost(String targetURL, String urlParameters) {
    printStatusMessage("Posting message to: " + targetURL);
    printCommandMessage("Message contents: " + urlParameters);

    HttpURLConnection connection = null;
    
    // get starting time
    if (isServerTypeMenuItem.isSelected()) {
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
      solutionTbl.addEntry(inputAttempt, "" + (System.currentTimeMillis() - elapsedStart));
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
  
  private static String generateBytecode(String classSelect, String methodSelect) {
    printStatusClear();

    // first we have to extract the class file from the jar file
    File jarfile = new File(projectPathName + projectName);
    if (!jarfile.isFile()) {
      printStatusError("Jar file not found: " + jarfile);
      return null;
    }
    try {
      // extract the selected class file
      extractClassFile(jarfile, classSelect);
    } catch (IOException ex) {
      printStatusError(ex.getMessage());
      return null;
    }

    // clear out the local variable list
    localVarTbl.clear(classSelect + "." + methodSelect);

    // decompile the selected class file
    String[] command = { "javap", "-p", "-c", "-s", "-l", CLASSFILE_STORAGE + "/" + classSelect + ".class" };
    // this creates a command launcher that runs on the current thread
    CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
    int retcode = commandLauncher.start(command, projectPathName);
    if (retcode != 0) {
      printStatusError("running javap on file: " + classSelect + ".class");
      return null;
    }

    // success - save the output as a file
    String content = commandLauncher.getResponse();
    Utils.saveTextFile(projectPathName + JAVAPFILE_STORAGE + "/" + classSelect + ".txt", content);
    return content;
  }

  private static void extractClassFile(File jarfile, String className) throws IOException {
    // get the path relative to the application directory
    int offset;
    String relpathname = "";
    className = className + ".class";
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
    String content = "#! DANALYZER SYMBOLIC EXPRESSION LIST" + Utils.NEWLINE;
    content += "# DANLAUNCHER_VERSION" + Utils.NEWLINE;
    content += "IPAddress: localhost" + Utils.NEWLINE;
    content += "DebugPort: " + debugPort + Utils.NEWLINE;
    content += "DebugMode: TCPPORT" + Utils.NEWLINE;
    content += "DebugFlags: " + debugFlags + Utils.NEWLINE;
    content += Utils.NEWLINE;
    return content;
  }
  
  private static void readSymbolicList(String content) {

    boolean updateFile = false;
    boolean makeBackup = true;
    
    File file = new File(projectPathName + "danfig");
    if (!file.isFile()) {
      printCommandError("danfig file not found at path: " + projectPathName);
      printCommandMessage("No symbolic parameters");
      updateFile = true;
    } else {
      try {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        dbtable.initSymbolic();
        printCommandMessage("Reading danfig selections");
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("# DANLAUNCHER_VERSION")) {
            // determine if this file was created by danlauncher (else, it was a danalyzer original)
            printCommandMessage("danfig file was generated by danlauncher");
            makeBackup = false;
          } else if (line.startsWith("Symbolic:")) {
            // extract symbolic definition
            String word[] = line.split("\\s+");
            String method, index, start, end, name, type;
            switch(word.length - 1) {
              case 2: // method, slot
                name   = "";
                method = word[1].trim().replace(".","/");
                index  = word[2].trim();
                start  = "0";
                end    = "0"; // this will default to entire method range
                type   = "";
                break;
              case 3: // method, slot, name
                name   = word[1].trim();
                method = word[2].trim().replace(".","/");
                index  = word[3].trim();
                start  = "0";
                end    = "0"; // this will default to entire method range
                type   = "";
                break;
              case 6: // index, method, start range, end range, name, data type
                name   = word[1].trim();
                method = word[2].trim().replace(".","/");
                index  = word[3].trim();
                start  = word[4].trim();
                end    = word[5].trim();
                type   = word[6].trim();
                break;
              default:
                printCommandError("ERROR: Invalid Symbolic word count (" + word.length + ") :" + line);
                return;
            }
            int lineStart = Integer.parseUnsignedInt(start);
            int lineEnd   = Integer.parseUnsignedInt(end);
            name = addSymbVariableByLine(method, name, type, index, lineStart, lineEnd);
            printCommandMessage("Symbol added - id: '" + name + "', method: " + method + ", slot: " + index +
                ", type: " + type + ", range { " + start + ", " + end + " }");

            // add entry to list 
            dbtable.addSymbolic(name);

            // save line content
            content += line + Utils.NEWLINE;
            updateFile = true;
          } else if (line.startsWith("Constraint:")) {
            // extract symbolic constraint
            String word[] = line.split("\\s+");
            if (word.length != 4) {
              System.err.println("ERROR: Invalid Constraint word count (" + word.length + ") : " + line);
              return;
            }
            // get the entries in the line
            String id = word[1].trim();
            String compare = word[2].trim();
            String constrVal = word[3].trim();
            printCommandMessage("Constraint added - id: '" + id + "' : " + compare + " " + constrVal);

            // add entry to list 
            addSymbConstraint(id, compare, constrVal);
            
            // save line content
            content += line + Utils.NEWLINE;
            updateFile = true;
          }
        }
        fileReader.close();
      } catch (IOException ex) {
        printCommandError(ex.getMessage());
      }
    }

    // if a change is needed, rename the old file and write the modified file back
    if (updateFile) {
      printCommandMessage("Updating danfig file: " + symbolTbl.getSize() + " symbolics added");
      
      // backup current file if it was not one we made just for danlauncher
      if (makeBackup) {
        printCommandMessage("Making backup of original danfig file");
        file.renameTo(new File(projectPathName + "danfig.save"));
      }
      
      // create the new file
      Utils.saveTextFile(projectPathName + "danfig", content);
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
          if (!isServerTypeMenuItem.isSelected()) {
            // TODO: need to get the actual cost here
            solutionTbl.addEntry(inputAttempt, "" + (System.currentTimeMillis() - elapsedStart));
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
      
      // disable stop key abd re-enable the Run and Get Bytecode buttons
      runMode = false;
      mainFrame.getButton("BTN_STOPTEST").setEnabled(false);
      mainFrame.getButton("BTN_RUNTEST").setEnabled(true);
      mainFrame.getButton("BTN_BYTECODE").setEnabled(true);
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (isTabSelection(PanelTabs.CALLGRAPH.toString())) {
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
          int methodCount = debugLogger.processMessage(message);
          // enable/disable Call Graph save buttons based on whether there is anything to save
          getMenuItem("MENU_SAVE_PNG").setEnabled(methodCount > 0);
          getMenuItem("MENU_SAVE_JSON").setEnabled(methodCount > 0);
          
          // update the thread count display
          int threads = debugLogger.getThreadCount();
          graphSetupFrame.getLabel("LBL_THREADS").setText("Threads: " + threads);
        }
      }
    }
  }

}
