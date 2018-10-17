/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import dansolver.Dansolver;
import debug.FontInfo.FontType;
import debug.FontInfo.TextColor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
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

  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

  private enum ElapsedMode { OFF, RUN, RESET }

  // types of debug messages
  public enum DebugType { TSTAMP, NORMAL, INFO, WARN, ERROR, DUMP, START, ENTRY, AGENT, THREAD,
        CALL, RETURN, UNINST, STATS, STACK, STACKS, STACKI, LOCAL,  LOCALS, SOLVE }

  // types of bytecode messages
  public enum BytecodeType { ERROR,METHOD, TEXT, PARAM, COMMENT, BRANCH, INVOKE, LOAD, STORE, OTHER }
  
  // tab panel selections
  public enum PanelTabs { BYTECODE, COMMAND, DATABASE, DEBUG, GRAPH }
  
  // location of danalyzer program
  private static final String DSEPATH = "/home/dan/Projects/isstac/dse/";
  
  private static final String CLASSFILE_STORAGE = ""; // application/";

  private static GuiControls    mainFrame = new GuiControls();
  private static PropertiesFile props;
  private static JTabbedPane    tabPanel;
  private static JPanel         graphPanel;
  private static JFileChooser   fileSelector;
  private static JComboBox      mainClassCombo;
  private static JComboBox      classCombo;
  private static JComboBox      methodCombo;
  private static Logger         bytecodeLogger;
  private static Logger         commandLogger;
  private static Logger         debugLogger;
  private static Timer          pktTimer;
  private static Timer          graphTimer;
  private static int            threadCount;
  private static long           elapsedStart;
  private static ElapsedMode    elapsedMode;
  private static String         projectPathName;
  private static String         projectName;
  private static int            tabIndex = 0;
  private static ThreadLauncher threadLauncher;
  private static PrintStream    standardOut = System.out;
  private static PrintStream    standardErr = System.err;         
  private static DatabaseTable  dbtable;
  
  private static HashMap<PanelTabs, Integer> tabSelect = new HashMap<>();
  private static HashMap<String, ArrayList<String>> clsMethMap; // maps the list of methods to each class
  private static ArrayList<String>  classList;
  private static final HashMap<String, FontInfo> bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo> commandFontTbl = null;
  private static final HashMap<String, FontInfo> debugFontTbl = new HashMap<>();
  private static HashMap<PanelTabs, Component> tabbedPanels = new HashMap<>();
  

  public GuiPanel(int port, boolean tcp) {
    // create the main panel and controls
    createDebugPanel();

    // this creates a command launcher on a separate thread
    threadLauncher = new ThreadLauncher((JTextArea) getTabPanel(PanelTabs.COMMAND));

    // check for a properties file
    props = new PropertiesFile();
    String logfileName = props.getPropertiesItem("LogFile", "");
    if (logfileName.isEmpty()) {
      logfileName = System.getProperty("user.dir") + "/debug.log";
    }
    GuiPanel.fileSelector.setCurrentDirectory(new File(logfileName));

    // start the TCP or UDP listener thread
//    try {
//      GuiPanel.udpThread = new ServerThread(port, tcp, logfileName, makeConnection);
//      GuiPanel.udpThread.start();
//      GuiPanel.listener = GuiPanel.udpThread;
//      GuiPanel.udpThread.setBufferFile(logfileName);
//      GuiPanel.mainFrame.getLabel("LBL_MESSAGES").setText("Logfile: " + udpThread.getOutputFile());
//    } catch (IOException ex) {
//      System.out.println(ex.getMessage());
//      System.exit(1);
//    }

//    // create a timer for reading and displaying the messages received (from either network or file)
//    GuiPanel.inputListener = new MsgListener();
//    pktTimer = new Timer(1, GuiPanel.inputListener);
//    pktTimer.start();
//
//    // create a slow timer for updating the call graph
//    graphTimer = new Timer(1000, new GraphUpdateListener());
//    graphTimer.start();

    // start timer when 1st line is received from port
//    GuiPanel.elapsedMode = ElapsedMode.RESET;
}
  
  public void createDebugPanel() {
    // if a panel already exists, close the old one
    if (GuiPanel.mainFrame.isValidFrame()) {
      GuiPanel.mainFrame.close();
    }

    GuiPanel.classList = new ArrayList<>();
    GuiPanel.clsMethMap = new HashMap<>();
    GuiPanel.threadCount = 0;
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.OFF;
    
    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // create the frame
    mainFrame.newFrame("danviewer", 1200, 800, false);

    // create the entries in the main frame
    panel = null;
    mainFrame.makeTextField(panel, "TXT_MESSAGES" , "Status"      , LEFT, true, "         ", false);
    mainFrame.makePanel    (panel, "PNL_CONTAINER", ""            , LEFT, true);
    tabPanel = mainFrame.makeTabbedPanel(panel, "PNL_TABBED", ""  , LEFT, true);

    panel = "PNL_CONTAINER";
    mainFrame.makePanel    (panel, "PNL_CONTROL"  , "Controls"    , LEFT, false);
    mainFrame.makePanel    (panel, "PNL_BYTECODE" , "Bytecode"    , LEFT, false);
    mainFrame.makePanel    (panel, "PNL_STATS"    , "Solutions"   , LEFT, true);

    panel = "PNL_STATS";
    mainFrame.makeTextField(panel, "TXT_1"        , "Dummy1"      , LEFT,  true, "------", false);

    panel = "PNL_BYTECODE";
    mainFrame.makeCombobox (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    mainFrame.makeCombobox (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    mainFrame.makeButton   (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, true);

    panel = "PNL_CONTROL";
    mainFrame.makeButton   (panel, "BTN_LOADFILE" , "Select Jar"  , LEFT, false);
    mainFrame.makeLabel    (panel, "LBL_JARFILE"  , "           " , LEFT, true);
    mainFrame.makeCombobox (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    mainFrame.makeGap      (panel);
    mainFrame.makeButton   (panel, "BTN_RUNTEST"  , "Run code"    , LEFT, false);
    mainFrame.makeTextField(panel, "TXT_ARGLIST"  , ""            , LEFT, true, "", true);
    mainFrame.makeButton   (panel, "BTN_SEND"     , "Post Data"   , LEFT, false);
    mainFrame.makeTextField(panel, "TXT_INPUT"    , ""            , LEFT, true, "", true);
    mainFrame.makeButton   (panel, "BTN_SOL_STRT" , "Start Solver", LEFT, true);

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
    mainFrame.getButton("BTN_SOL_STRT").setEnabled(false);
    mainFrame.getTextField("TXT_ARGLIST").setEnabled(false);
    mainFrame.getTextField("TXT_INPUT").setEnabled(false);
    
    // we need a filechooser for the Save buttons
    GuiPanel.fileSelector = new JFileChooser();

    // setup the control actions
    GuiPanel.mainFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        enableUpdateTimers(false);
        dbtable.exit();
        mainFrame.close();
        System.exit(0);
      }
    });
    GuiPanel.tabPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        // if we switched to the graph display tab, update the graph
//        if (isTabSelection(PanelTabs.GRAPH)) {
//          GuiPanel.mainFrame.repack();
//        }
      }
    });
    classCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JComboBox cbSelect = (JComboBox) evt.getSource();
        String classSelect = (String) cbSelect.getSelectedItem();
        setMethodSelections(classSelect);
        mainFrame.getFrame().pack(); // need to update frame in case width requirements change
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOADFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // load the selected file
        loadFileButtonActionPerformed();
        
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
        mainFrame.getButton("BTN_SOL_STRT").setEnabled(true);
        mainFrame.getTextField("TXT_ARGLIST").setEnabled(true);
        mainFrame.getTextField("TXT_INPUT").setEnabled(true);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_BYTECODE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        String classSelect  = (String) classCombo.getSelectedItem();
        String methodSelect = (String) methodCombo.getSelectedItem();
        runBytecode(classSelect, methodSelect, -1);
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
        String input = mainFrame.getTextField("TXT_INPUT").getText();
        executePost(input);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_SOL_STRT")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dansolver.Dansolver.main(null);
      }
    });
    
    // display the frame
    GuiPanel.mainFrame.display();

    // setup the font selections for the bytecode display
    String fonttype = "Courier";
    HashMap<String, FontInfo> map = debugFontTbl;
    setTypeColor (map, DebugType.TSTAMP.toString(), TextColor.Black,  FontType.Normal, 14, fonttype);
    setTypeColor (map, DebugType.ERROR.toString(),  TextColor.Red,    FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.WARN.toString(),   TextColor.Orange, FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.INFO.toString(),   TextColor.Black,  FontType.Italic, 14, fonttype);
    setTypeColor (map, DebugType.NORMAL.toString(), TextColor.Black,  FontType.Normal, 14, fonttype);
    setTypeColor (map, DebugType.DUMP.toString(),   TextColor.Orange, FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.START.toString(),  TextColor.Black,  FontType.BoldItalic, 14, fonttype);
    setTypeColor (map, DebugType.ENTRY.toString(),  TextColor.Brown,  FontType.Normal, 14, fonttype);
    setTypeColor (map, DebugType.AGENT.toString(),  TextColor.Violet, FontType.Italic, 14, fonttype);
    setTypeColor (map, DebugType.THREAD.toString(), TextColor.DkVio,  FontType.Italic, 14, fonttype);
    setTypeColor (map, DebugType.CALL.toString(),   TextColor.Gold,   FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.RETURN.toString(), TextColor.Gold,   FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.UNINST.toString(), TextColor.Gold,   FontType.BoldItalic, 14, fonttype);
    setTypeColor (map, DebugType.STATS.toString(),  TextColor.Gold,   FontType.BoldItalic, 14, fonttype);
    setTypeColor (map, DebugType.STACK.toString(),  TextColor.Blue,   FontType.Normal, 14, fonttype);
    setTypeColor (map, DebugType.STACKS.toString(), TextColor.Blue,   FontType.Italic, 14, fonttype);
    setTypeColor (map, DebugType.STACKI.toString(), TextColor.Blue,   FontType.Bold,   14, fonttype);
    setTypeColor (map, DebugType.LOCAL.toString(),  TextColor.Green,  FontType.Normal, 14, fonttype);
    setTypeColor (map, DebugType.LOCALS.toString(), TextColor.Green,  FontType.Italic, 14, fonttype);
    setTypeColor (map, DebugType.SOLVE.toString(),  TextColor.DkVio,  FontType.Bold,   14, fonttype);

    // setup the font selections for the bytecode display
    map = bytecodeFontTbl;
    setTypeColor (map, BytecodeType.ERROR.toString(),   TextColor.Red,    FontType.Bold  , 14, fonttype);
    setTypeColor (map, BytecodeType.METHOD.toString(),  TextColor.Violet, FontType.Italic, 16, fonttype);
    setTypeColor (map, BytecodeType.TEXT.toString(),    TextColor.Black,  FontType.Italic, 14, fonttype);
    setTypeColor (map, BytecodeType.PARAM.toString(),   TextColor.Brown,  FontType.Normal, 14, fonttype);
    setTypeColor (map, BytecodeType.COMMENT.toString(), TextColor.Green,  FontType.Italic, 14, fonttype);
    setTypeColor (map, BytecodeType.BRANCH.toString(),  TextColor.DkVio,  FontType.Bold  , 14, fonttype);
    setTypeColor (map, BytecodeType.INVOKE.toString(),  TextColor.Gold,   FontType.Bold  , 14, fonttype);
    setTypeColor (map, BytecodeType.LOAD.toString(),    TextColor.Green,  FontType.Normal, 14, fonttype);
    setTypeColor (map, BytecodeType.STORE.toString(),   TextColor.Blue,   FontType.Normal, 14, fonttype);
    setTypeColor (map, BytecodeType.OTHER.toString(),   TextColor.Black,  FontType.Normal, 14, fonttype);
    
    // add the tabbed message panels for bytecode output, command output, and debug output
    addPanelToTab(PanelTabs.COMMAND , new JTextArea());
    addPanelToTab(PanelTabs.BYTECODE, new JTextPane());
    addPanelToTab(PanelTabs.DATABASE, new JTable());
    addPanelToTab(PanelTabs.DEBUG   , new JTextPane());
    addPanelToTab(PanelTabs.GRAPH   , new JPanel());

    // create the message logging for the text panels
    commandLogger  = createTextLogger(PanelTabs.COMMAND , commandFontTbl);
    bytecodeLogger = createTextLogger(PanelTabs.BYTECODE, bytecodeFontTbl);
    debugLogger    = createTextLogger(PanelTabs.DEBUG   , debugFontTbl);

    // init the CallGraph panel
//    CallGraph.initCallGraph((JPanel) getTabPanel(PanelTabs.GRAPH));

    // init the database table panel
    dbtable = new DatabaseTable((JTable) getTabPanel(GuiPanel.PanelTabs.DATABASE));
  }

  private Component getTabPanel(PanelTabs tabname) {
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
  
  /**
   * sets the association between a type of message and the characteristics
   * in which to print the message.
   * 
   * @param map   - the hasmap to assign the entry to
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   * @param size  - the size of the font
   * @param font  - the font family (e.g. Courier, Ariel, etc.)
   */
  private void setTypeColor (HashMap<String, FontInfo> map, String type,
      TextColor color, FontType ftype, int size, String font) {
    FontInfo fontinfo = new FontInfo(color, ftype, size, font);
    if (map.containsKey(type)) {
      map.replace(type, fontinfo);
    }
    else {
      map.put(type, fontinfo);
    }
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
    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void resetElapsedTime() {
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.RESET;
    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
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
        GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText(timestamp);
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
  
  private static void printStatus(String message) {
    if (message == null) {
      mainFrame.getTextField("TXT_MESSAGES").setText("                   ");
    } else {
      mainFrame.getTextField("TXT_MESSAGES").setText(message);
      
      // echo status to command output window
      printCommandError(message);
    }
  }
  
  private static void printCommandMessage(String message) {
    commandLogger.printLine(message);
  }
  
  private static void printCommandError(String message) {
    commandLogger.printLine(message);
  }
  
  private static void printBytecodeMethod(String methodName) {
    bytecodeLogger.printField("TEXT", "Method: ");
    bytecodeLogger.printField("METHOD", methodName + NEWLINE + NEWLINE);
  }
  
  private static void printBytecodeOpcode(int line, String opcode, String param, String comment) {
    String type = "OTHER";
    if (opcode.startsWith("invoke")) {
      type = "INVOKE";
    } else if (opcode.startsWith("if_") ||
        opcode.startsWith("ifeq") ||
        opcode.startsWith("ifne") ||
        opcode.startsWith("ifgt") ||
        opcode.startsWith("ifge") ||
        opcode.startsWith("iflt") ||
        opcode.startsWith("ifle")) {
      type = "BRANCH";
    }

    bytecodeLogger.printFieldAlignRight("TEXT", "" + line, 5);
    bytecodeLogger.printFieldAlignLeft(type, "  " + opcode, 16);
    bytecodeLogger.printFieldAlignLeft("PARAM", param, 10);
    bytecodeLogger.printField("COMMENT", comment + NEWLINE);
  }
  
  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param linenum  - the line number
   * @param elapsed  - the elapsed time
   * @param threadid - the thread id
   * @param typestr  - the type of message to display (all caps)
   * @param content  - the message content
   */
  private void printDebug(int linenum, String elapsed, String threadid, String typestr, String content) {
    if (linenum >= 0 && elapsed != null && typestr != null && content != null && !content.isEmpty()) {
      // make sure the linenum is 8-digits in length and the type is 6-chars in length
      String linestr = "00000000" + linenum;
      linestr = linestr.substring(linestr.length() - 8);
      typestr = (typestr + "      ").substring(0, 6);
      if (!threadid.isEmpty()) {
        threadid = "<" + threadid + ">";
      }
      
      // print message (seperate into multiple lines if ASCII newlines are contained in it)
      if (!content.contains(NEWLINE)) {
        debugLogger.printField("INFO", linestr + "  ");
        debugLogger.printField("INFO", elapsed + " ");
        debugLogger.printField("INFO", threadid + " ");
        debugLogger.printField(typestr, typestr + ": " + content + NEWLINE);
      }
      else {
        // seperate into lines and print each independantly
        String[] msgarray = content.split(NEWLINE);
        for (String msg : msgarray) {
          debugLogger.printField("INFO", linestr + "  ");
          debugLogger.printField("INFO", elapsed + " ");
          debugLogger.printField("INFO", threadid + " ");
          debugLogger.printField(typestr, typestr + ": " + msg + NEWLINE);
        }
      }
    }
  }

  private static boolean fileCheck(String fname) {
    if (new File(fname).isFile()) {
      return true;
    }

    printStatus("Missing file: " + fname);
    return false;
  }
  
  private static void loadFileButtonActionPerformed() {
    printStatus(null);
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
//      classpath += ":" + danpath + "lib/com.microsoft.z3.jar";
      classpath += ":/*:/lib/*";

      // remove any existing class files in the "application" folder in the location of the jar file
      File applPath = new File(projectPathName + CLASSFILE_STORAGE);
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
        printStatus("Instrumentation successful");
        printCommandMessage(commandLauncher.getResponse());
      
        // update the class and method selections
        setupClassList(projectPathName);
      } else {
        printStatus("ERROR: instrumenting file: " + projectName);
      }
    }
  }
  
  public static void runBytecode(String classSelect, String methodSelect, int markLine) {
    printStatus(null);

    // first we have to pull off the class files from the jar file
    File jarfile = new File(projectPathName + projectName);
    if (!jarfile.isFile()) {
      printStatus("ERROR: Jar file not found: " + jarfile);
      return;
    }
    try {
      // extract the selected class file
      extractClassFile(jarfile, classSelect);
    } catch (IOException ex) {
      printStatus("ERROR: " + ex);
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
      parseJavap(classSelect, methodSelect, content, markLine);
      printStatus("Successfully generated bytecode");
      
      // swich tab to show bytecode
      setTabSelect(PanelTabs.BYTECODE);
    } else {
      printStatus("ERROR: running javap on file: " + classSelect + ".class");
    }
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
      printStatus("ERROR: no main class found!");
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
              + ":" + mongolib
              + ":" + localpath;

    // run the instrumented jar file
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass, arglist };

    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(command, projectPathName, "run_" + projectName, null);
  }

  private static void executePost(String urlParameters) {
    HttpURLConnection connection = null;
    String targetURL = "http://localhost:8080"; // TODO: need to allow user to specify this

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
    if (!CLASSFILE_STORAGE.isEmpty()) {
      offset = className.indexOf(CLASSFILE_STORAGE);
      if (offset >= 0) {
        className = className.substring(offset + CLASSFILE_STORAGE.length());
      }
    }
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
        String fullpath = jarpathname + CLASSFILE_STORAGE + relpathname;
        File fout = new File(fullpath + className);
        // skip if file already exists
        if (fout.isFile()) {
          System.err.println("File '" + className + "' already created");
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
    
    printStatus("ERROR: '" + className + "' not found in " + jarfile.getAbsoluteFile());
  }
  
  /**
   * outputs the bytecode message to the status display.
   * 
   * @param line
   * @param entry  - the line read from javap
   * @param className
   */
  private static void formatBytecode(int line, String entry, String className) {
    if (entry != null && !entry.isEmpty()) {
      String opcode = entry;
      String param = "";
      String comment = "";
      // check for parameter
      int offset = opcode.indexOf(" ");
      if (offset > 0 ) {
        param = opcode.substring(offset).trim();
        opcode = opcode.substring(0, offset);

        // check for comment
        offset = param.indexOf("//");
        if (offset > 0) {
          comment = param.substring(offset);
          param = param.substring(0, offset).trim();
        }
      }

      printBytecodeOpcode(line, opcode, param, comment);
    }
  }

  private static void parseJavap(String classSelect, String methodSelect, String content, int markLine) {
    printCommandMessage("searching for: " + classSelect + "." + methodSelect);

    // javap uses the dot format for the class name, so convert it
    classSelect = classSelect.replace("/", ".");
    int lastline = 0;

    String[] lines = content.split(System.getProperty("line.separator"));
    boolean found = false;
    for (String entry : lines) {
      entry = entry.replace("\t", " ").trim();
        
      // ignore these entries for now
      if (entry.startsWith("public class ") ||
          entry.startsWith("private class ") ||
          entry.startsWith("class ") ||
          entry.startsWith("descriptor:") ||
          entry.startsWith("Code:")) {
        continue;
      }

      // check for line number, indicating this is bytecode
      boolean bytecode = false;
      int offset = entry.indexOf(":");
      if (offset > 0) {
        try {
          int line = Integer.parseUnsignedInt(entry.substring(0, offset));
          entry = entry.substring(offset+1).trim();
          bytecode = true;
//printCommandMessage("entry is bytecode: " + entry);
          if (found) {
            if (line < lastline) {
//printCommandMessage("line count indicates new method: " + line);
              return;
            }
            lastline = line;
            formatBytecode(line, entry, classSelect);
            continue;
          }
        } catch (NumberFormatException ex) { }
      }
        
      // check for start of selected method (method must contain the parameter list)
      if (!bytecode && entry.contains("(") && entry.contains(")")) {
//printCommandMessage("method found in: " + entry);
        // extract the word that contains the param list
        String methName = "";
        String array[] = entry.split("\\s+");
        for (String word : array) {
          if (word.contains("(") && word.contains(")")) {
            methName = word;
          }
        }
        if (methName.isEmpty()) {
          continue;
        }
        // extract the method name from the entry (the <init> method will also include the class)
        if (methName.startsWith(classSelect)) {
          methName = methName.substring(classSelect.length());
        }
        // ignore the return value - it's not part of the signature
        methName = methName.substring(0, methName.indexOf(")"));
        // TODO: for now, we just look for method name, not signature because javap uses
        // nomenclature like 'int' and 'long' instead of 'I' and 'J', making comparisons
        // a little tougher. This will be a problem for classes that hane multiple signatures
        // of the same class name.
        methName = methName.substring(0, methName.indexOf("("));
        if (methName.isEmpty()) { // if no name - it must be the <init> contructor method
          methName = "<init>";
        }
        // method entry found, let's see if it's the one we want
        printCommandMessage("method: " + methName);
        if (methodSelect.startsWith(methName)) {
          found = true;
          printCommandMessage("method: FOUND!");
          printBytecodeMethod(classSelect + "." + methodSelect);
        } else if (found) {
          // athe next method has been found in the file - stop parsing
          break;
        }
      }
    }
  }
  
  public static void readSymbolicList() {
    File file = new File(projectPathName + "danfig");
    if (!file.isFile()) {
      printCommandError("danfig file not found at path: " + projectPathName);
      printCommandMessage("No symbolic parameters known.");
      return;
    }
    try {
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      printCommandMessage("Symbolic parameters:");
      while ((line = bufferedReader.readLine()) != null) {
        if (line.startsWith("Symbolic:")) {
          line = line.substring("Symbolic:".length()).trim();
          String word[] = line.split(",");
          if (word.length < 2) {
            printCommandError("ERROR: invalid symbolic definition - " + line);
            return;
          }
          String symname = word[1].trim() + "_" + word[0].trim().replace(".","/");
          printCommandMessage(" - " + symname);

          // add entry to list 
          dbtable.addSymbolic(symname);
        }
      }
      fileReader.close();
    } catch (IOException ex) {
      printCommandError(ex.getMessage());
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
      System.setOut(standardOut);
      System.setErr(standardErr);
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
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
//      if (isTabSelection(PanelTabs.GRAPH)) {
//        GuiPanel.mainFrame.repack();
//      }
    }
  }

}
