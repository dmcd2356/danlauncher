/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import bytecode.BytecodeLogger;
import gui.GuiControls;
import logging.Logger;
import logging.FontInfo;
import callgraph.CallGraph;
import command.ThreadLauncher;
import command.CommandLauncher;
import debug.DebugLogger;
import gui.GuiControls.FrameSize;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
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
public final class GuiPanel {

  private static final String NEWLINE = System.getProperty("line.separator");
  private static final PrintStream STANDARD_OUT = System.out;
  private static final PrintStream STANDARD_ERR = System.err;         
  //private static final String CLASSFILE_STORAGE = ""; // application/";
  
  // location of danalyzer program
  private static final String DSEPATH = "/home/dan/Projects/isstac/dse/";
  
  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

  private enum ElapsedMode { OFF, RUN, RESET }

  // tab panel selections
  public enum PanelTabs { COMMAND, DATABASE, BYTECODE, PARAMS, LOG, GRAPH }
  
  private static final GuiControls     mainFrame = new GuiControls();
  private static PropertiesFile  props;
  private static JTabbedPane     tabPanel;
  private static JFileChooser    fileSelector;
  private static JComboBox       mainClassCombo;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static Logger          commandLogger;
  private static Logger          paramLogger;
  private static BytecodeLogger  bytecodeLogger;
  private static DebugLogger     debugLogger;
  private static Timer           pktTimer;
  private static Timer           graphTimer;
  private static long            elapsedStart;
  private static ElapsedMode     elapsedMode;
  private static GraphHighlight  graphMode;
  //private static boolean         graphShowAllThreads;
  private static String          projectPathName;
  private static String          projectName;
  private static int             tabIndex = 0;
  private static ThreadLauncher  threadLauncher;
  private static DatabaseTable   dbtable;
  private static DefaultListModel solutionList;
  
  private static Visitor         makeConnection;
  private static String          serverPort = "";
  private static String          clientPort = "";
  private static NetworkServer   udpThread;
  private static NetworkListener networkListener;
  private static MsgListener     inputListener;
  
  private static ArrayList<String>  classList;
  private static HashMap<String, ArrayList<String>>  clsMethMap; // maps the list of methods to each class
  private static final HashMap<PanelTabs, Integer>   tabSelect = new HashMap<>();
  private static final HashMap<String, FontInfo>     bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo>     debugFontTbl = new HashMap<>();
  private static final HashMap<PanelTabs, Component> tabbedPanels = new HashMap<>();
  

  // allow ServerThread to indicate on panel when a connection has been made for TCP
  public interface Visitor {
    void showConnection(String connection);
    void resetConnection();
  }

  public GuiPanel(int port, boolean tcp, NetworkServer portThread) {
    makeConnection = new Visitor() {
      @Override
      public void showConnection(String connection) {
        clientPort = connection;
        printStatusMessage("connected to  " + GuiPanel.clientPort);
        printCommandMessage("connected to  " + GuiPanel.clientPort);
      }

      @Override
      public void resetConnection() {
        printStatusMessage("connected to  " + GuiPanel.clientPort + "  (CONNECTION CLOSED)");
        printCommandMessage("connected to  " + GuiPanel.clientPort + "  (CONNECTION CLOSED)");
      }
    };

    GuiPanel.classList = new ArrayList<>();
    GuiPanel.clsMethMap = new HashMap<>();
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.OFF;
    GuiPanel.graphMode = GraphHighlight.NONE;
    //GuiPanel.graphShowAllThreads = true;

    String ipaddr = "<unknown>";
    // get this server's ip address
    try(final DatagramSocket socket = new DatagramSocket()){
      socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
      ipaddr = socket.getLocalAddress().getHostAddress();
    } catch (SocketException | UnknownHostException ex) {  }
    GuiPanel.serverPort = "Server port (" + (tcp ? "TCP" : "UDP") + ")  -  " + ipaddr + ":" + port;
    
    // we need a filechooser
    GuiPanel.fileSelector = new JFileChooser();

    // check for a properties file
    props = new PropertiesFile("danlauncher");
    String logfileName = System.getProperty("user.dir") + "/debug.log"; // the default value
    logfileName = props.getPropertiesItem("LogFile", logfileName);
    GuiPanel.fileSelector.setCurrentDirectory(new File(logfileName));

    // create the main panel and controls
    createDebugPanel();

    // this creates a command launcher on a separate thread
    threadLauncher = new ThreadLauncher((JTextArea) getTabPanel(PanelTabs.COMMAND));

    // setup access to the network listener thread
    GuiPanel.udpThread = portThread;
    GuiPanel.udpThread.setLoggingCallback(makeConnection);
    GuiPanel.udpThread.setBufferFile(logfileName);

    GuiPanel.networkListener = GuiPanel.udpThread; // this allows us to signal the network listener
    
    // create a timer for reading and displaying the messages received (from either network or file)
    GuiPanel.inputListener = new MsgListener();
    pktTimer = new Timer(1, GuiPanel.inputListener);
    pktTimer.start();

    // create a slow timer for updating the call graph
    graphTimer = new Timer(1000, new GraphUpdateListener());
    graphTimer.start();

    // start timer when 1st line is received from port
    GuiPanel.elapsedMode = ElapsedMode.RESET;
}

  public void createDebugPanel() {
    // if a panel already exists, close the old one
    if (GuiPanel.mainFrame.isValidFrame()) {
      GuiPanel.mainFrame.close();
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
    mainFrame.makePanel      (panel, "PNL_CONTROLS" , ""          , LEFT, false);
    mainFrame.makePanel      (panel, "PNL_SOLUTIONS", "Solutions" , LEFT, true, 300, 250);
    mainFrame.makeTabbedPanel(panel, "PNL_TABBED"   , ""          , LEFT, true);

    panel = "PNL_MESSAGES";
    mainFrame.makeTextField (panel, "TXT_MESSAGES"  , ""          , LEFT, true, "", 150, false);

    panel = "PNL_CONTROLS";
    mainFrame.makePanel     (panel, "PNL_ACTION"    , "Controls"  , LEFT, false);
    mainFrame.makePanel     (panel, "PNL_BYTECODE"  , "Bytecode"  , LEFT, true);

    panel = "PNL_SOLUTIONS";
    mainFrame.makeScrollList(panel, "LIST_SOLUTIONS", "" , solutionList);

    panel = "PNL_BYTECODE";
    mainFrame.makeCombobox  (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    mainFrame.makeCombobox  (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    mainFrame.makeButton    (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, true);

    panel = "PNL_ACTION";
    mainFrame.makeButton    (panel, "BTN_LOADFILE" , "Select Jar"  , LEFT, false);
    mainFrame.makeLabel     (panel, "LBL_JARFILE"  , "           " , LEFT, true);
    mainFrame.makeCombobox  (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    mainFrame.makeGap       (panel);
    mainFrame.makeButton    (panel, "BTN_RUNTEST"  , "Run code"    , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_ARGLIST"  , ""            , LEFT, true, "", 20, true);
    mainFrame.makeButton    (panel, "BTN_SEND"     , "Post Data"   , LEFT, false);
    mainFrame.makeTextField (panel, "TXT_INPUT"    , ""            , LEFT, true, "", 20, true);
    mainFrame.makeTextField (panel, "TXT_PORT"     , "Server Port" , LEFT, true, "8080", 10, true);
    mainFrame.makeButton    (panel, "BTN_SOL_STRT" , "Run Solver"  , LEFT, false);
    mainFrame.makeButton    (panel, "BTN_STOP"     , "STOP"        , RIGHT, true);
    mainFrame.makeButton    (panel, "BTN_LOG_CLEAR", "Clear Log"   , RIGHT, true);
    mainFrame.makeButton    (panel, "BTN_DB_CLEAR" , "Clear DB"    , RIGHT, true);

    // initially disable the class/method select and generating bytecode
    mainClassCombo = mainFrame.getCombobox ("COMBO_MAINCLS");
    classCombo  = mainFrame.getCombobox ("COMBO_CLASS");
    methodCombo = mainFrame.getCombobox ("COMBO_METHOD");
    mainClassCombo.setEnabled(false);
    classCombo.setEnabled(false);
    methodCombo.setEnabled(false);
    mainFrame.getLabel("COMBO_MAINCLS").setEnabled(false);
    mainFrame.getLabel("COMBO_CLASS").setEnabled(false);
    mainFrame.getLabel("COMBO_METHOD").setEnabled(false);
    mainFrame.getButton("BTN_BYTECODE").setEnabled(false);
    mainFrame.getButton("BTN_RUNTEST").setEnabled(false);
    mainFrame.getButton("BTN_SEND").setEnabled(false);
    mainFrame.getTextField("TXT_PORT").setEnabled(false);
    mainFrame.getLabel("TXT_PORT").setEnabled(false);
    mainFrame.getButton("BTN_SOL_STRT").setEnabled(false);
    mainFrame.getButton("BTN_STOP").setEnabled(false);
    mainFrame.getButton("BTN_LOG_CLEAR").setEnabled(false);
    mainFrame.getButton("BTN_DB_CLEAR").setEnabled(false);
    mainFrame.getTextField("TXT_ARGLIST").setEnabled(false);
    mainFrame.getTextField("TXT_INPUT").setEnabled(false);

    // save reference to tabbed panel
    tabPanel = mainFrame.getTabbedPanel("PNL_TABBED");

    // setup the control actions
    GuiPanel.mainFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        enableUpdateTimers(false);
        networkListener.exit();
        dbtable.exit();
        mainFrame.close();
        System.exit(0);
      }
    });
    GuiPanel.tabPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        // if we switched to the graph display tab, update the graph
        if (isTabSelection(PanelTabs.GRAPH)) {
          if (CallGraph.updateCallGraph(graphMode, false)) {
//            GuiPanel.mainFrame.repack();
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
//        mainFrame.getFrame().pack(); // need to update frame in case width requirements change
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOADFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // load the selected file
        loadJarFile();
        
        // enable the class and method selections
        mainClassCombo.setEnabled(false);
        classCombo.setEnabled(true);
        methodCombo.setEnabled(true);
        mainFrame.getLabel("COMBO_MAINCLS").setEnabled(true);
        mainFrame.getLabel("COMBO_CLASS").setEnabled(true);
        mainFrame.getLabel("COMBO_METHOD").setEnabled(true);
        mainFrame.getButton("BTN_BYTECODE").setEnabled(true);
        mainFrame.getButton("BTN_RUNTEST").setEnabled(true);
        mainFrame.getButton("BTN_SEND").setEnabled(true);
        mainFrame.getTextField("TXT_PORT").setEnabled(true);
        mainFrame.getLabel("TXT_PORT").setEnabled(true);
        mainFrame.getButton("BTN_SOL_STRT").setEnabled(true);
        mainFrame.getButton("BTN_LOG_CLEAR").setEnabled(true);
        mainFrame.getButton("BTN_DB_CLEAR").setEnabled(true);
        mainFrame.getTextField("TXT_ARGLIST").setEnabled(true);
        mainFrame.getTextField("TXT_INPUT").setEnabled(true);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_BYTECODE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String classSelect  = (String) classCombo.getSelectedItem();
        String methodSelect = (String) methodCombo.getSelectedItem();
        generateBytecode(classSelect, methodSelect);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_RUNTEST")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String arglist = mainFrame.getTextField("TXT_ARGLIST").getText();
        runTest(arglist);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_SEND")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String targetURL = "http://localhost:" + mainFrame.getTextField("TXT_PORT").getText();
        String input = mainFrame.getTextField("TXT_INPUT").getText();
        executePost(targetURL, input);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_SOL_STRT")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dansolver.Dansolver.main(null);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_DB_CLEAR")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dbtable.clearDB();
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOG_CLEAR")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        debugLogger.clear();
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_STOP")).addActionListener(new ActionListener() {
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
    GuiPanel.mainFrame.display();

    // create the special text loggers
    debugLogger = new DebugLogger(PanelTabs.LOG.toString());
    bytecodeLogger = new BytecodeLogger(PanelTabs.BYTECODE.toString());

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
    dbtable = new DatabaseTable((JTable) getTabPanel(GuiPanel.PanelTabs.DATABASE));
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
    return GuiPanel.elapsedMode == GuiPanel.ElapsedMode.RESET;
  }
  
  public static void setThreadEnabled(boolean enabled) {
//    JRadioButton button = mainFrame.getRadiobutton("RB_THREAD");
//    button.setEnabled(enabled);
//    if (enabled) {
//      enabled = button.isSelected();
//    }
//
//    JLabel label = mainFrame.getLabel ("TXT_TH_SEL");
//    label.setText("0");
//
//    label.setEnabled(enabled);
//    mainFrame.getButton("BTN_TH_UP").setEnabled(enabled);
//    mainFrame.getButton("BTN_TH_DN").setEnabled(enabled);
  }
  
  public static boolean isTabSelection(PanelTabs select) {
    if (GuiPanel.tabPanel == null || tabSelect.isEmpty()) {
      return false;
    }
    int curTab = GuiPanel.tabPanel.getSelectedIndex();
    if (!tabSelect.containsKey(select)) {
      System.err.println("Tab selection '" + select + "' not found!");
      return false;
    }
    return GuiPanel.tabPanel.getSelectedIndex() == tabSelect.get(select);
  }

  /**
   * finds the classes in a jar file & sets the Class ComboBox to these values.
   */
  private static void setupClassList (String pathname) {
    // init the class list
    GuiPanel.clsMethMap = new HashMap<>();
    GuiPanel.classList = new ArrayList<>();
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
          GuiPanel.classList.add(curClass);
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
      GuiPanel.classList.add(curClass);
      clsMethMap.put(curClass, methList);
    }

    // setup the class and method selections
    setClassSelections();
    System.out.println(GuiPanel.classList.size() + " classes and " +
        fullMethList.size() + " methods found");
  }

  private static void setClassSelections() {
    classCombo.removeAllItems();
    mainClassCombo.removeAllItems();
    for (int ix = 0; ix < GuiPanel.classList.size(); ix++) {
      String cls = GuiPanel.classList.get(ix);
      classCombo.addItem(cls);

      // now get the methods for the class and check if it has a "main"
      ArrayList<String> methodSelection = GuiPanel.clsMethMap.get(cls);
      if (methodSelection != null && methodSelection.contains("main([Ljava/lang/String;)V")) {
        mainClassCombo.addItem(cls);
      }
    }

    // init class selection to 1st item
    classCombo.setSelectedIndex(0);
    
    // now update the method selections
    setMethodSelections((String) classCombo.getSelectedItem());

    // in case selections require expansion, adjust frame packing
    // TODO: skip this, since it causes the frrame size to get extremely large for some reason
//    mainFrame.getFrame().pack();
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
    ArrayList<String> methodSelection = GuiPanel.clsMethMap.get(clsname);
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
    GuiPanel.elapsedStart = System.currentTimeMillis();
    GuiPanel.elapsedMode = ElapsedMode.RUN;
//    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void resetElapsedTime() {
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.RESET;
//    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void updateElapsedTime() {
    if (GuiPanel.elapsedMode == ElapsedMode.RUN) {
      long elapsed = System.currentTimeMillis() - GuiPanel.elapsedStart;
      if (elapsed > 0) {
        Integer msec = (int)(elapsed % 1000);
        elapsed = elapsed / 1000;
        Integer secs = (int)(elapsed % 60);
        Integer mins = (int)(elapsed / 60);
        String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
                           ((secs < 10) ? "0" : "") + secs.toString(); // + "." +
                           //((msec < 10) ? "00" : (msec < 100) ? "0" : "") + msec.toString();
//        GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText(timestamp);
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
  
//  private static void setHighlightMode(GraphHighlight mode) {
//    JRadioButton threadSelBtn = GuiPanel.mainFrame.getRadiobutton("RB_THREAD");
//    JRadioButton timeSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_ELAPSED");
//    JRadioButton instrSelBtn  = GuiPanel.mainFrame.getRadiobutton("RB_INSTRUCT");
//    JRadioButton iterSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_ITER");
//    JRadioButton statSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_STATUS");
//    JRadioButton noneSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_NONE");
//
//    // turn on the selected mode and turn off all others
//    switch(mode) {
//      case THREAD:
//        threadSelBtn.setSelected(true);
//        timeSelBtn.setSelected(false);
//        instrSelBtn.setSelected(false);
//        iterSelBtn.setSelected(false);
//        statSelBtn.setSelected(false);
//        noneSelBtn.setSelected(false);
//
//        // send the thread selection to the CallGraph
//        JLabel label = mainFrame.getLabel("TXT_TH_SEL");
//        int select = Integer.parseInt(label.getText().trim());
//        CallGraph.setThreadSelection(select);
//        break;
//      case TIME:
//        threadSelBtn.setSelected(false);
//        timeSelBtn.setSelected(true);
//        instrSelBtn.setSelected(false);
//        iterSelBtn.setSelected(false);
//        statSelBtn.setSelected(false);
//        noneSelBtn.setSelected(false);
//        break;
//      case INSTRUCTION:
//        threadSelBtn.setSelected(false);
//        timeSelBtn.setSelected(false);
//        instrSelBtn.setSelected(true);
//        iterSelBtn.setSelected(false);
//        statSelBtn.setSelected(false);
//        noneSelBtn.setSelected(false);
//        break;
//      case ITERATION:
//        threadSelBtn.setSelected(false);
//        timeSelBtn.setSelected(false);
//        instrSelBtn.setSelected(false);
//        iterSelBtn.setSelected(true);
//        statSelBtn.setSelected(false);
//        noneSelBtn.setSelected(false);
//        break;
//      case STATUS:
//        threadSelBtn.setSelected(false);
//        timeSelBtn.setSelected(false);
//        instrSelBtn.setSelected(false);
//        iterSelBtn.setSelected(false);
//        statSelBtn.setSelected(true);
//        noneSelBtn.setSelected(false);
//        break;
//      case NONE:
//        threadSelBtn.setSelected(false);
//        timeSelBtn.setSelected(false);
//        instrSelBtn.setSelected(false);
//        iterSelBtn.setSelected(false);
//        statSelBtn.setSelected(false);
//        noneSelBtn.setSelected(true);
//      default:
//        break;
//    }
//
//    // set the mode flag & update graph
//    graphMode = mode;
//    if (isTabSelection(PanelTabs.GRAPH)) {
//      CallGraph.updateCallGraph(graphMode, false);
//    }
//  }
  
  private static void printStatusClear() {
    mainFrame.getTextField("TXT_MESSAGES").setText("                   ");
  }
  
  private static void printStatusMessage(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText(message);
  }
  
  private static void printStatusError(String message) {
    mainFrame.getTextField("TXT_MESSAGES").setText(message);
      
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
  
  private static boolean fileCheck(String fname) {
    if (new File(fname).isFile()) {
      return true;
    }

    printStatusError("Missing file: " + fname);
    return false;
  }
  
  private static void loadJarFile() {
    printStatusClear();
    FileNameExtensionFilter filter = new FileNameExtensionFilter("Jar Files", "jar");
    GuiPanel.fileSelector.setFileFilter(filter);
    //GuiPanel.fileSelector.setSelectedFile(new File("TestMain.jar"));
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setApproveButtonText("Load");
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      // read the file
      File file = GuiPanel.fileSelector.getSelectedFile();
      projectName = file.getName();
      projectPathName = file.getParentFile().getAbsolutePath() + "/";
      
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
      String[] command = { "java", "-cp", classpath, mainclass, projectName };
      CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
      int retcode = commandLauncher.start(command, projectPathName);
      if (retcode == 0) {
        mainFrame.getLabel("LBL_JARFILE").setText(projectPathName + projectName);
        printStatusMessage("Instrumentation successful");
        printCommandMessage(commandLauncher.getResponse());
      
        // update the class and method selections
        setupClassList(projectPathName);
        
        // set the location of the debug log file to this directory
        String logfileName = projectPathName + "/debug.log";
        udpThread.setBufferFile(logfileName);
        props.setPropertiesItem("LogFile", logfileName);
      } else {
        printStatusError("ERROR: instrumenting file: " + projectName);
      }
    }
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

    // clear the output display
    bytecodeLogger.clear();
      
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

  public static void markBytecode(int start, boolean branch) {
    bytecodeLogger.highlightBytecode(start, branch);
  }
  
  private void runTest(String arglist) {
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

    // TODO: let user pick his java home
    String JAVA_HOME = "/usr/lib/jvm/java-8-openjdk-amd64";
    
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
    String bootlpath = "-Dsun.boot.library.path=" + JAVA_HOME + "/bin:/usr/lib";
    String bootcpath ="-Xbootclasspath/a"
              + ":" + DSEPATH + "danalyzer/dist/danalyzer.jar"
              + ":" + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar"
              + ":" + mongolib;
    String agentpath ="-agentpath:" + DSEPATH + "danhelper/libdanhelper.so";
    String classpath = instrJarFile
              + ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar"
              + ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar"
              + ":" + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar"
              + ":" + mongolib
              + ":" + localpath;

    // run the instrumented jar file
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass, arglist };

    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(command, projectPathName, "run_" + projectName, null);

    // allow user to terminate the test
    mainFrame.getButton("BTN_STOP").setEnabled(true);
  }

  private static void executePost(String targetURL, String urlParameters) {
    HttpURLConnection connection = null;
    
    // get starting time
    long elapsedStart = System.currentTimeMillis();

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
      printCommandMessage("RESPONSE: " + response.toString());

      // TODO: this is not really the elapsed time - we need to read from the debug output to
      // determine when the terminating condition has been reached.
      long elapsed = System.currentTimeMillis() - elapsedStart;

      // add input value to solutions tried
      String entry = urlParameters + "   " + elapsed;
      if (!GuiPanel.solutionList.contains(entry)) {
        System.out.println("Added symbolic entry " + solutionList.size() + ": " + entry);
        GuiPanel.solutionList.addElement(entry);
//        GuiPanel.symbList.setSelectedIndex(solutionList.size()-1); // select the new (last) entry
      }
    } catch (IOException ex) {
      // display error
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
  
  public static void readSymbolicList() {
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

  public static void resetLoggedTime() {
    // this adds log entry to file for demarcation
    udpThread.resetInput();
    
    // this resets and starts the timer
    GuiPanel.startElapsedTime();
  }
  
  public static void resetCapturedInput() {
    // clear the packet buffer and statistics
    udpThread.clear();

    // clear the graphics panel
    CallGraph.clearGraphAndMethodList();
    if (isTabSelection(PanelTabs.GRAPH)) {
      CallGraph.updateCallGraph(GuiPanel.GraphHighlight.NONE, false);
    }
          
    // reset the elapsed time
//    GuiPanel.resetElapsedTime();
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
      mainFrame.getButton("BTN_STOP").setEnabled(false);
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (isTabSelection(PanelTabs.GRAPH)) {
        if (CallGraph.updateCallGraph(graphMode, false)) {
//          GuiPanel.mainFrame.repack();
        }
      }
    }
  }

  private class MsgListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // read & process next message
      String message = GuiPanel.udpThread.getNextMessage();
      if (message != null) {
        DebugLogger.processMessage(message);
      }
    }
  }

}
