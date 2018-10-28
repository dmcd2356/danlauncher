/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import callgraph.CallGraph;
import util.CommandLauncher;
import util.ThreadLauncher;
import gui.GuiControls;
import gui.GuiControls.FrameSize;
import gui.GuiControls.InputControl;
import logging.FontInfo;
import logging.Logger;
import panels.BytecodeViewer;
import panels.DatabaseTable;
import panels.DebugLogger;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author dmcd2356
 */
public final class LauncherMain {

  private static final String NEWLINE = System.getProperty("line.separator");
  private static final PrintStream STANDARD_OUT = System.out;
  private static final PrintStream STANDARD_ERR = System.err;         
  //private static final String CLASSFILE_STORAGE = ""; // application/";

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
  private enum PanelTabs { COMMAND, DATABASE, BYTECODE, PARAMS, LOG, GRAPH }
  
  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

//  private static final GuiControls     mainFrame = new GuiControls();
  private static JTabbedPane     tabPanel;
  private static JFileChooser    fileSelector;
  private static JComboBox       mainClassCombo;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static Logger          commandLogger;
  private static Logger          paramLogger;
  private static BytecodeViewer  bytecodeLogger;
  private static DebugLogger     debugLogger;
  private static Timer           pktTimer;
  private static Timer           graphTimer;
  private static long            elapsedStart;
  private static ElapsedMode     elapsedMode;
  private static GraphHighlight  graphMode;
  private static String          projectPathName;
  private static String          projectName;
  private static int             tabIndex = 0;
  private static ThreadLauncher  threadLauncher;
  private static DatabaseTable   dbtable;
  private static String          inputAttempt = "";
  private static DefaultListModel solutionList;
  private static Visitor         makeConnection;
  private static String          clientPort = "";
  private static NetworkServer   udpThread;
  private static NetworkListener networkListener;
  private static MsgListener     inputListener;
  private static String          javaHome;
  private static boolean         mainClassInitializing;
  
  private static ArrayList<String>  classList;
  private static HashMap<String, ArrayList<String>>  clsMethMap; // maps the list of methods to each class
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
    new PropertiesTable (ProjectProperties.IS_SERVER_TYPE , InputControl.CheckBox , "CBOX_POST"),
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
        printCommandMessage("connected to  " + LauncherMain.clientPort);
      }

      @Override
      public void resetConnection() {
        printCommandMessage("connected to  " + LauncherMain.clientPort + "  (CONNECTION CLOSED)");
      }
    };

    classList = new ArrayList<>();
    clsMethMap = new HashMap<>();
    elapsedStart = 0;
    elapsedMode = ElapsedMode.OFF;
    graphMode = GraphHighlight.NONE;
    mainClassInitializing = false;

    // check for the global danlauncher properties file and init default values if not found
    systemProps = new PropertiesFile(HOMEPATH + "/.danlauncher", "SYSTEM_PROPERTIES");
    String projectPath = systemProps.getPropertiesItem(SystemProperties.PROJECT_PATH.toString(), HOMEPATH);
    String maxLogLength = systemProps.getPropertiesItem(SystemProperties.MAX_LOG_LENGTH.toString(), "500000");
    String debugPort = systemProps.getPropertiesItem(SystemProperties.DEBUG_PORT.toString(), "5000");
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

    // setup access to the network listener thread
    // start the TCP/UDP listener thread
    try {
      int port = Integer.parseUnsignedInt(debugPort);
      udpThread = new NetworkServer(port, true);
      System.out.println("danlauncher receiving port: " + port);
    } catch (IOException | NumberFormatException ex) {
      System.out.println(ex.getMessage());
      System.exit(1);
    }
    startDebugPort(projectPath);
    
    // create a timer for reading and displaying the messages received (from either network or file)
    inputListener = new MsgListener();
    pktTimer = new Timer(1, inputListener);
    pktTimer.start();

    // create a slow timer for updating the call graph
    graphTimer = new Timer(1000, new GraphUpdateListener());
    graphTimer.start();

    // start timer when 1st line is received from port
    elapsedMode = ElapsedMode.RESET;
}

  public void startDebugPort(String projectPath) {
    udpThread.start();
    udpThread.setLoggingCallback(makeConnection);
    udpThread.setBufferFile(projectPath + "/debug.log");

//    // get this server's ip address
//    String ipaddr = "<unknown>";
//    try(final DatagramSocket socket = new DatagramSocket()){
//      socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
//      ipaddr = socket.getLocalAddress().getHostAddress();
//    } catch (SocketException | UnknownHostException ex) {  }

    networkListener = LauncherMain.udpThread; // this allows us to signal the network listener
  }
  
  public void createDebugPanel() {
    // if a panel already exists, close the old one
    if (GuiControls.isValidFrame()) {
      GuiControls.close();
    }

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // init the solutions tried to none
    solutionList = new DefaultListModel();
    
    // create the frame
    GuiControls.newFrame("danlauncher", 1200, 800, FrameSize.FULLSCREEN);

    panel = null; // this creates the entries in the main frame
    GuiControls.makePanel      (panel, "PNL_MESSAGES" , "Status"    , LEFT, true);
    GuiControls.makePanel      (panel, "PNL_CONTROLS" , ""          , LEFT, true);
//    GuiControls.makePanel      (panel, "PNL_SOLUTIONS", "Solutions" , LEFT, true, 300, 275);
    GuiControls.makeTabbedPanel(panel, "PNL_TABBED"   , ""          , LEFT, true);

    panel = "PNL_MESSAGES";
    GuiControls.makeTextField (panel, "TXT_MESSAGES"  , ""          , LEFT, true, "", 150, false);

    panel = "PNL_CONTROLS";
    GuiControls.makePanel     (panel, "PNL_SELECTS"   , ""          , LEFT, false);
    GuiControls.makePanel     (panel, "PNL_ACTION"    , "Controls"  , LEFT, false, 300, 250);
    GuiControls.makePanel     (panel, "PNL_SOLUTIONS" , "Solutions" , LEFT, true, 300, 250);

    panel = "PNL_SELECTS";
    GuiControls.makePanel     (panel, "PNL_FILE_SEL"  , "File Select", LEFT, true);
    GuiControls.makePanel     (panel, "PNL_BYTECODE"  , "Bytecode"   , LEFT, true);

    panel = "PNL_SOLUTIONS";
    GuiControls.makeScrollList(panel, "LIST_SOLUTIONS", "" , solutionList);

    panel = "PNL_FILE_SEL";
    GuiControls.makeButton    (panel, "BTN_LOADFILE" , "Select Jar"  , LEFT, false);
    GuiControls.makeLabel     (panel, "LBL_JARFILE"  , "           " , LEFT, true);
    GuiControls.makeCombobox  (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    GuiControls.makeCheckbox  (panel, "CBOX_POST"    , "Input using Post (server application)", LEFT, true, 0);

    panel = "PNL_BYTECODE";
    GuiControls.makeCombobox  (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    GuiControls.makeCombobox  (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    GuiControls.makeButton    (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, true);

    panel = "PNL_ACTION";
    GuiControls.makeButton    (panel, "BTN_RUNTEST"  , "Run code"    , LEFT, false);
    GuiControls.makeTextField (panel, "TXT_ARGLIST"  , ""            , LEFT, true, "", 40, true);
    GuiControls.makeButton    (panel, "BTN_STOPTEST" , "STOP"        , LEFT, true);
    GuiControls.makeButton    (panel, "BTN_SEND"     , "Post Data"   , LEFT, false);
    GuiControls.makeTextField (panel, "TXT_INPUT"    , ""            , LEFT, true, "", 40, true);
    GuiControls.makeTextField (panel, "TXT_PORT"     , "Server Port" , LEFT, true, "8080", 8, true);
    GuiControls.makeButton    (panel, "BTN_SOLVER"   , "Run Solver"  , LEFT, true);
    GuiControls.makeButton    (panel, "BTN_DB_CLEAR" , "Clear DB"    , LEFT, false);
    GuiControls.makeButton    (panel, "BTN_LOG_CLEAR", "Clear Log"   , LEFT, true);

    // initially disable the class/method select and generating bytecode
    mainClassCombo = GuiControls.getCombobox ("COMBO_MAINCLS");
    classCombo  = GuiControls.getCombobox ("COMBO_CLASS");
    methodCombo = GuiControls.getCombobox ("COMBO_METHOD");
    mainClassCombo.setEnabled(false);
    classCombo.setEnabled(false);
    methodCombo.setEnabled(false);
    GuiControls.getCheckbox("CBOX_POST").setEnabled(false);
    GuiControls.getLabel("COMBO_MAINCLS").setEnabled(false);
    GuiControls.getLabel("COMBO_CLASS").setEnabled(false);
    GuiControls.getLabel("COMBO_METHOD").setEnabled(false);
    GuiControls.getButton("BTN_BYTECODE").setEnabled(false);
    GuiControls.getButton("BTN_RUNTEST").setEnabled(false);
    GuiControls.getButton("BTN_SOLVER").setEnabled(false);
    GuiControls.getButton("BTN_STOPTEST").setEnabled(false);
    GuiControls.getButton("BTN_LOG_CLEAR").setEnabled(false);
    GuiControls.getButton("BTN_DB_CLEAR").setEnabled(false);
    GuiControls.getTextField("TXT_ARGLIST").setEnabled(false);
    GuiControls.getTextField("TXT_INPUT").setEnabled(false);
    GuiControls.getButton("BTN_SEND").setEnabled(false);
    GuiControls.getTextField("TXT_PORT").setEnabled(false);
    GuiControls.getLabel("TXT_PORT").setEnabled(false);

    // save reference to tabbed panel
    tabPanel = GuiControls.getTabbedPanel("PNL_TABBED");

    // setup the control actions
    GuiControls.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        enableUpdateTimers(false);
        networkListener.exit();
        dbtable.exit();
        GuiControls.close();
        System.exit(0);
      }
    });
    LauncherMain.tabPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        // if we switched to the graph display tab, update the graph
        if (isTabSelection_GRAPH()) {
          if (CallGraph.updateCallGraph(graphMode, false)) {
//            GuiControls.repack();
          }
        }
      }
    });
    classCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JComboBox cbSelect = (JComboBox) evt.getSource();
        String classSelect = (String) cbSelect.getSelectedItem();
        setMethodSelections(classSelect);
//        GuiControls.getFrame().pack(); // need to update frame in case width requirements change
      }
    });
    (GuiControls.getButton("BTN_LOADFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // load the selected file
        loadJarFile();
        
        // enable the class and method selections
        mainClassCombo.setEnabled(true);
        classCombo.setEnabled(true);
        methodCombo.setEnabled(true);
        GuiControls.getCheckbox("CBOX_POST").setEnabled(true);
        GuiControls.getLabel("COMBO_MAINCLS").setEnabled(true);
        GuiControls.getLabel("COMBO_CLASS").setEnabled(true);
        GuiControls.getLabel("COMBO_METHOD").setEnabled(true);
        GuiControls.getButton("BTN_BYTECODE").setEnabled(true);
        GuiControls.getButton("BTN_RUNTEST").setEnabled(true);
        GuiControls.getButton("BTN_SOLVER").setEnabled(true);
        GuiControls.getButton("BTN_LOG_CLEAR").setEnabled(true);
        GuiControls.getButton("BTN_DB_CLEAR").setEnabled(true);
        GuiControls.getTextField("TXT_ARGLIST").setEnabled(true);
        GuiControls.getTextField("TXT_INPUT").setEnabled(true);

        if (GuiControls.getCheckbox("CBOX_POST").isSelected()) {
          GuiControls.getButton("BTN_SEND").setEnabled(true);
          GuiControls.getTextField("TXT_PORT").setEnabled(true);
          GuiControls.getLabel("TXT_PORT").setEnabled(true);
        }
      }
    });
    (GuiControls.getCheckbox("CBOX_POST")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // enable/disable "send to port" controls accordingly
        boolean isServerType = GuiControls.getCheckbox("CBOX_POST").isSelected();
        GuiControls.getButton("BTN_SEND").setEnabled(isServerType);
        GuiControls.getTextField("TXT_PORT").setEnabled(isServerType);
        GuiControls.getLabel("TXT_PORT").setEnabled(isServerType);

        updateProjectProperty("CBOX_POST");
      }
    });
    mainClassCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        //if (evt.getActionCommand().equals("comboBoxChanged")) {
        if (!mainClassInitializing) {
          updateProjectProperty("COMBO_MAINCLS");
        }
      }
    });
    (GuiControls.getTextField("TXT_ARGLIST")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateProjectProperty("TXT_ARGLIST");
      }
    });
    (GuiControls.getTextField("TXT_ARGLIST")).addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
      }
      @Override
      public void focusLost(FocusEvent e) {
        updateProjectProperty("TXT_ARGLIST");
      }
    });
    (GuiControls.getTextField("TXT_PORT")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateProjectProperty("TXT_PORT");
      }
    });
    (GuiControls.getTextField("TXT_PORT")).addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
      }
      @Override
      public void focusLost(FocusEvent e) {
        updateProjectProperty("TXT_PORT");
      }
    });
    (GuiControls.getButton("BTN_BYTECODE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String classSelect  = (String) classCombo.getSelectedItem();
        String methodSelect = (String) methodCombo.getSelectedItem();
        generateBytecode(classSelect, methodSelect);
      }
    });
    (GuiControls.getButton("BTN_RUNTEST")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String arglist = GuiControls.getTextField("TXT_ARGLIST").getText();
        runTest(arglist);
      }
    });
    (GuiControls.getButton("BTN_SEND")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String targetURL = "http://localhost:" + GuiControls.getTextField("TXT_PORT").getText();
        String input = GuiControls.getTextField("TXT_INPUT").getText();
        executePost(targetURL, input);
      }
    });
    (GuiControls.getButton("BTN_SOLVER")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dansolver.Dansolver.main(null);
      }
    });
    (GuiControls.getButton("BTN_DB_CLEAR")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dbtable.clearDB();
      }
    });
    (GuiControls.getButton("BTN_LOG_CLEAR")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        debugLogger.clear();
      }
    });
    (GuiControls.getButton("BTN_STOPTEST")).addActionListener(new ActionListener() {
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
    });
    
    // display the frame
    GuiControls.display();

    // create the special text loggers
    debugLogger = new DebugLogger(PanelTabs.LOG.toString());
    bytecodeLogger = new BytecodeViewer(PanelTabs.BYTECODE.toString());

    // add the tabbed message panels for bytecode output, command output, and debug output
    addPanelToTab(PanelTabs.COMMAND , new JTextArea());
    addPanelToTab(PanelTabs.DATABASE, new JTable());
    addPanelToTab(PanelTabs.BYTECODE, bytecodeLogger.getTextPane());
    addPanelToTab(PanelTabs.PARAMS  , new JTextArea());
    addPanelToTab(PanelTabs.LOG     , debugLogger.getTextPane());
    addPanelToTab(PanelTabs.GRAPH   , new JPanel());

    // create the message logging for the text panels
    commandLogger  = createTextLogger(PanelTabs.COMMAND , null);
    paramLogger    = createTextLogger(PanelTabs.PARAMS  , null);

    // init the CallGraph panel
    CallGraph.initCallGraph((JPanel) getTabPanel(PanelTabs.GRAPH));

    // init the database table panel
    dbtable = new DatabaseTable((JTable) getTabPanel(LauncherMain.PanelTabs.DATABASE));
  }

  private static Component getTabPanel(PanelTabs tabname) {
    if (!tabbedPanels.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }
    return tabbedPanels.get(tabname);
  }
  
  private static void setTabSelect(PanelTabs tabname) {
    Integer index = tabSelect.get(tabname);
    if (index == null) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }

    tabPanel.setSelectedIndex(index);
  }
  
  private void addPanelToTab(PanelTabs tabname, Component panel) {
    // make sure we don't already have the entry
    if (tabbedPanels.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel already defined in tabs");
      System.exit(1);
    }
    
    // add the textPane to a scrollPane
    JScrollPane scrollPanel;
    scrollPanel = new JScrollPane(panel);
    scrollPanel.setBorder(BorderFactory.createTitledBorder(""));
    
    // now add the scroll pane to the tabbed pane
    tabPanel.addTab(tabname.toString(), scrollPanel);
    tabSelect.put(tabname, tabIndex++);
    
    // save access to panel by name
    tabbedPanels.put(tabname, panel);
  }
  
  private Logger createTextLogger(PanelTabs tabname, HashMap<String, FontInfo> fontmap) {
    return new Logger(getTabPanel(tabname), tabname.toString(), fontmap);
  }
  
  public static boolean isElapsedModeReset() {
    return LauncherMain.elapsedMode == LauncherMain.ElapsedMode.RESET;
  }
  
  public static void setThreadEnabled(boolean enabled) {
//    JRadioButton button = GuiControls.getRadiobutton("RB_THREAD");
//    button.setEnabled(enabled);
//    if (enabled) {
//      enabled = button.isSelected();
//    }
//
//    JLabel label = GuiControls.getLabel ("TXT_TH_SEL");
//    label.setText("0");
//
//    label.setEnabled(enabled);
//    GuiControls.getButton("BTN_TH_UP").setEnabled(enabled);
//    GuiControls.getButton("BTN_TH_DN").setEnabled(enabled);
  }
  
  public static boolean isTabSelection_GRAPH() {
    PanelTabs select = PanelTabs.GRAPH;
    if (LauncherMain.tabPanel == null || tabSelect.isEmpty()) {
      return false;
    }
    int curTab = LauncherMain.tabPanel.getSelectedIndex();
    if (!tabSelect.containsKey(select)) {
      System.err.println("Tab selection '" + select + "' not found!");
      return false;
    }
    return LauncherMain.tabPanel.getSelectedIndex() == tabSelect.get(select);
  }

  public static void highlightBranch(int start, boolean branch) {
    bytecodeLogger.highlightBranch(start, branch);
  }
  
  public static void resetLoggedTime() {
    // this adds log entry to file for demarcation
    udpThread.resetInput();
    
    // this resets and starts the timer
    LauncherMain.startElapsedTime();
  }
  
  public static void resetCapturedInput() {
    // clear the packet buffer and statistics
    udpThread.clear();

    // clear the graphics panel
    CallGraph.clearGraphAndMethodList();
    if (isTabSelection_GRAPH()) {
      CallGraph.updateCallGraph(LauncherMain.GraphHighlight.NONE, false);
    }
          
    // reset the elapsed time
//    GuiPanel.resetElapsedTime();
  }

  /**
   * finds the classes in a jar file & sets the Class ComboBox to these values.
   */
  private static void setupClassList (String pathname) {
    // init the class list
    LauncherMain.clsMethMap = new HashMap<>();
    LauncherMain.classList = new ArrayList<>();
    ArrayList<String> fullMethList = new ArrayList<>();

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
          LauncherMain.classList.add(curClass);
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
      LauncherMain.classList.add(curClass);
      clsMethMap.put(curClass, methList);
    }

    // setup the class and method selections
    setClassSelections();
    System.out.println(LauncherMain.classList.size() + " classes and " +
        fullMethList.size() + " methods found");
  }

  private static void setClassSelections() {
    // temp disable update of properties from main class selection, since all we are doing here
    // is filling the entries in. The user has not made a selection yet.
    mainClassInitializing = true;
    
    classCombo.removeAllItems();
    mainClassCombo.removeAllItems();
    for (int ix = 0; ix < LauncherMain.classList.size(); ix++) {
      String cls = LauncherMain.classList.get(ix);
      classCombo.addItem(cls);

      // now get the methods for the class and check if it has a "main"
      ArrayList<String> methodSelection = LauncherMain.clsMethMap.get(cls);
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
    // TODO: skip this, since it causes the frrame size to get extremely large for some reason
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
    ArrayList<String> methodSelection = LauncherMain.clsMethMap.get(clsname);
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
    LauncherMain.elapsedStart = System.currentTimeMillis();
    LauncherMain.elapsedMode = ElapsedMode.RUN;
//    GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void resetElapsedTime() {
    LauncherMain.elapsedStart = 0;
    LauncherMain.elapsedMode = ElapsedMode.RESET;
//    GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void updateElapsedTime() {
    if (LauncherMain.elapsedMode == ElapsedMode.RUN) {
      long elapsed = System.currentTimeMillis() - LauncherMain.elapsedStart;
      if (elapsed > 0) {
        Integer msec = (int)(elapsed % 1000);
        elapsed = elapsed / 1000;
        Integer secs = (int)(elapsed % 60);
        Integer mins = (int)(elapsed / 60);
        String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
                           ((secs < 10) ? "0" : "") + secs.toString(); // + "." +
                           //((msec < 10) ? "00" : (msec < 100) ? "0" : "") + msec.toString();
//        GuiPanel.GuiControls.getTextField("FIELD_ELAPSED").setText(timestamp);
      }
    }
  }
  
  private static void enableUpdateTimers(boolean enable) {
    if (enable) {
      if (pktTimer != null) {
        pktTimer.start();
      }
      if (graphTimer != null) {
        graphTimer.start();
      }
    } else {
      if (pktTimer != null) {
        pktTimer.stop();
      }
      if (graphTimer != null) {
        graphTimer.stop();
      }
    }
  }
  
  private static void printStatusClear() {
    GuiControls.getTextField("TXT_MESSAGES").setText("                   ");
  }
  
  private static void printStatusMessage(String message) {
    GuiControls.getTextField("TXT_MESSAGES").setText(message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  private static void printStatusError(String message) {
    GuiControls.getTextField("TXT_MESSAGES").setText(message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  private static void printParameter(String message) {
    paramLogger.printLine(message);
  }
  
  private static void printCommandMessage(String message) {
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
      String setting = GuiControls.getInputControl(ctlName, ctlType);
      String val = projectProps.getPropertiesItem(tag.toString(), setting);
      if (!setting.equals(val)) {
        // if property value was found & differs from gui setting, update the gui
        GuiControls.setInputControl(ctlName, ctlType, val);
      }
    }
  }
  
  private static void updateProjectProperty(String ctlName) {
    for (PropertiesTable propEntry : PROJ_PROP_TBL) {
      if (ctlName.equals(propEntry.controlName)) {
        String val = GuiControls.getInputControl(ctlName, propEntry.controlType);
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
  
  private static void loadJarFile() {
    printStatusClear();

    FileNameExtensionFilter filter = new FileNameExtensionFilter("Jar Files", "jar");
    LauncherMain.fileSelector.setFileFilter(filter);
    //GuiPanel.fileSelector.setSelectedFile(new File("TestMain.jar"));
    LauncherMain.fileSelector.setMultiSelectionEnabled(false);
    LauncherMain.fileSelector.setApproveButtonText("Load");
    int retVal = LauncherMain.fileSelector.showOpenDialog(GuiControls.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      // read the file
      File file = LauncherMain.fileSelector.getSelectedFile();
      projectName = file.getName();
      projectPathName = file.getParentFile().getAbsolutePath() + "/";
      
      // init project config file to current settings
      initProjectProperties(projectPathName);
        
      // verify all the required files exist
      if (!fileCheck(projectPathName + projectName) ||
          !fileCheck(DSEPATH + "danalyzer/dist/danalyzer.jar") ||
          !fileCheck(DSEPATH + "danalyzer/lib/commons-io-2.5.jar") ||
          !fileCheck(DSEPATH + "danalyzer/lib/asm-all-5.2.jar")) {
        return;
      }
    
      // read the symbolic parameter definitions
      readSymbolicList();
  
      String mainclass = "danalyzer.instrumenter.Instrumenter";
      String classpath = DSEPATH + "danalyzer/dist/danalyzer.jar";
      classpath += ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar";
      classpath += ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar";
      classpath += ":" + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar";
      classpath += ":/*:/lib/*";

      // remove any existing class files in the location of the jar file
      File applPath = new File(projectPathName);  // + CLASSFILE_STORAGE
      if (applPath.isDirectory()) {
        for(String fname: applPath.list()){
          if (fname.endsWith(".class")) {
            File currentFile = new File(applPath.getPath(), fname);
            currentFile.delete();
          }
        }
      }
      
      // instrument the jar file
      int retcode = 0;
      if (projectName.endsWith("-dan-ed.jar")) {
        // file is already instrumented - use it as-is
        printCommandMessage("Input file already instrumented - using as is.");
        projectName = projectName.substring(0, projectName.indexOf("-dan-ed.jar")) + ".jar";
      } else {
        String[] command = { "java", "-cp", classpath, mainclass, projectName };
        CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
        retcode = commandLauncher.start(command, projectPathName);
        if (retcode == 0) {
          printStatusMessage("Instrumentation successful");
          printCommandMessage(commandLauncher.getResponse());
        } else {
          printStatusError("ERROR: instrumenting file: " + projectName);
          return;
        }
      }
      
      GuiControls.getLabel("LBL_JARFILE").setText(projectPathName + projectName);
      
      // update the class and method selections
      setupClassList(projectPathName);
        
      // save location of project selection
      systemProps.setPropertiesItem(SystemProperties.PROJECT_PATH.toString(), projectPathName);
        
      // set the location of the debug log file to this directory
      udpThread.setBufferFile(projectPathName + "debug.log");
        
      // reset the soultion list
      solutionMap.clear();
      solutionList.clear();
    }
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
      printStatusError("ERROR: no main class found!");
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
//              + ":" + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar"
              + ":" + mongolib
              + ":" + localpath;

    if (!GuiControls.getCheckbox("CBOX_POST").isSelected()) {
      inputAttempt = arglist;
      elapsedStart = System.currentTimeMillis();
    }

    printStatusMessage("Run command started...");
    
    // run the instrumented jar file
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass, arglist };

    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(command, projectPathName, "run_" + projectName, null);

    // allow user to terminate the test
    GuiControls.getButton("BTN_STOPTEST").setEnabled(true);
  }

  public static void generateBytecode(String classSelect, String methodSelect) {
    printStatusClear();

    // check if bytecode for this method already displayed
    if (bytecodeLogger.isMethodDisplayed(classSelect, methodSelect)) {
      printStatusMessage("Bytecode already loaded");

      // clear any highlighting
      bytecodeLogger.highlightClear();
      
      // swich tab to show bytecode
      setTabSelect(PanelTabs.BYTECODE);
      return;
    }
    
    // first we have to pull off the class files from the jar file
    File jarfile = new File(projectPathName + projectName);
    if (!jarfile.isFile()) {
      printStatusError("ERROR: Jar file not found: " + jarfile);
      return;
    }
    try {
      // extract the selected class file
      extractClassFile(jarfile, classSelect);
    } catch (IOException ex) {
      printStatusError("ERROR: " + ex);
      return;
    }

    // decompile the selected class file
    String[] command = { "javap", "-p", "-c", "-s", "-l", classSelect + ".class" };
    CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
    int retcode = commandLauncher.start(command, projectPathName);
    if (retcode == 0) {
      String content = commandLauncher.getResponse();
      bytecodeLogger.parseJavap(classSelect, methodSelect, content);
      printStatusMessage("Successfully generated bytecode");

      // swich tab to show bytecode
      setTabSelect(PanelTabs.BYTECODE);
    } else {
      printStatusError("ERROR: running javap on file: " + classSelect + ".class");
    }
  }

  private static void executePost(String targetURL, String urlParameters) {
    printStatusMessage("Posting message to: " + targetURL);
    printCommandMessage("Message contents: " + urlParameters);

    HttpURLConnection connection = null;
    
    // get starting time
    if (GuiControls.getCheckbox("CBOX_POST").isSelected()) {
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
        String fullpath = jarpathname + relpathname;
//        String fullpath = jarpathname + CLASSFILE_STORAGE + relpathname;
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
    
    printStatusError("ERROR: '" + className + "' not found in " + jarfile.getAbsoluteFile());
  }
  
  private static void readSymbolicList() {
    // initialize replacement config info
    String content = "#! DANALYZER SYMBOLIC EXPRESSION LIST" + NEWLINE;
    content += "# DO_NOT_CHANGE" + NEWLINE;
    content += "IPAddress: localhost" + NEWLINE;
    content += "DebugPort: 5000" + NEWLINE;
    content += "DebugMode: TCPPORT" + NEWLINE;
    content += "DebugFlags: WARN SOLVE PATH CALLS" + NEWLINE;
    content += "TriggerOnCall: 0" + NEWLINE;
    content += "TriggerOnReturn: 0" + NEWLINE;
    content += "TriggerOnInstr: 0" + NEWLINE;
    content += "TriggerOnError: 0" + NEWLINE;
    content += "TriggerOnException: 0" + NEWLINE;
    content += NEWLINE;

    boolean needChange = false;
    boolean noChange = false;
    
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
        int count = 0;
        dbtable.initSymbolic();
        printCommandMessage("Symbolic parameters:");
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("# DO_NOT_CHANGE")) {
            printCommandMessage("Will not update danfig file");
            noChange = true;
          } else if (line.startsWith("Symbolic:")) {
//            line = line.substring("Symbolic:".length()).trim();
            String word[] = line.substring("Symbolic:".length()).trim().split(",");
            if (word.length < 2) {
              printCommandError("ERROR: invalid symbolic definition - " + line);
              return;
            }
            String symname = word[1].trim() + "_" + word[0].trim().replace(".","/");
            printParameter("P" + count++ + ": " + symname);
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
      
      // rename file so we can modify it
      file.renameTo(new File(projectPathName + "danfig.save"));
      
      // create the new file
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
      printCommandMessage("jobstart - job " + threadInfo.jobid + ": " + threadInfo.jobname);
    }
        
    @Override
    public void jobfinished(ThreadLauncher.ThreadInfo threadInfo) {
      printCommandMessage("jobfinished - job " + threadInfo.jobid + ": status = " + threadInfo.exitcode);
      switch (threadInfo.exitcode) {
        case 0:
          if (!GuiControls.getCheckbox("CBOX_POST").isSelected()) {
            // TODO: need to get the actual cost here
            addSolution(inputAttempt, System.currentTimeMillis() - elapsedStart);
          }
          break;
        case 137:
          printCommandMessage("User SIGKILL complete.");
          break;
        case 143:
          printCommandMessage("User SIGTERM complete.");
          break;
        default:
          printCommandMessage("Failure executing command.");
          break;
      }
      
      // disable stop key
      GuiControls.getButton("BTN_STOPTEST").setEnabled(false);
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (isTabSelection_GRAPH()) {
        if (CallGraph.updateCallGraph(graphMode, false)) {
//          GuiControls.repack();
        }
      }
    }
  }

  private class MsgListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // read & process next message
      String message = LauncherMain.udpThread.getNextMessage();
      if (message != null) {
        DebugLogger.processMessage(message);
      }
    }
  }

}
