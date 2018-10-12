/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import debug.FontInfo.FontType;
import debug.FontInfo.TextColor;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author dmcd2356
 */
public class GuiPanel {

  public enum GraphHighlight { NONE, STATUS, TIME, INSTRUCTION, ITERATION, THREAD }

  private enum ElapsedMode { OFF, RUN, RESET }

  // tab panel selections
  private enum PanelTabs { BYTECODE, COMMAND, DATABASE, DEBUG, GRAPH }
  
  // location of danalyzer program
  private static final String DSEPATH = "/home/dan/Projects/isstac/dse/";
  
  private static final String CLASSFILE_STORAGE = ""; // application/";

  private final static GuiControls  mainFrame = new GuiControls();
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
  private static Timer          statsTimer;
  private static int            threadCount;
  private static long           elapsedStart;
  private static ElapsedMode    elapsedMode;
  private static String         projectPathName;
  private static String         projectName;
  private static int            tabIndex = 0;
  private static HashMap<PanelTabs, Integer> tabSelect = new HashMap<>();
  private static HashMap<String, ArrayList<String>> clsMethMap; // maps the list of methods to each class
  private static ArrayList<String>  classList;
  private static final HashMap<String, FontInfo> bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo> commandFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo> debugFontTbl = new HashMap<>();
  
/**
 * creates a debug panel to display the Logger messages in.
   * @param port  - the port to use for reading messages
   * @param tcp     - true if use TCP, false to use UDP
 */  
  public void createDebugPanel(int port, boolean tcp) {
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
    mainFrame.makePanel (panel, "PNL_CONTAINER", ""              , LEFT, true);
    tabPanel = mainFrame.makeTabbedPanel(panel, "PNL_TABBED", "" , LEFT, true);

    panel = "PNL_CONTAINER";
    mainFrame.makePanel (panel, "PNL_CONTROL"  , "Controls"      , LEFT, true);
    mainFrame.makeTextField(panel, "TXT_MESSAGES", "Status"      , RIGHT, true, "              ", false);

//    panel = "PNL_CONTAINER1";
//    mainFrame.makePanel (panel, "PNL_STATS"    , "Statistics"        , LEFT, false);
//    mainFrame.makePanel (panel, "PNL_CONTROL"  , "Controls"          , LEFT, true);
//    mainFrame.makePanel (panel, "PNL_MESSAGES" , "Status"            , LEFT, true);
    
//    panel = "PNL_STATS";
//    mainFrame.makeLabel      (panel, "LBL_1" , " "      , RIGHT, true);
//    mainFrame.makeLabel      (panel, "LBL_2" , " "      , LEFT,  true); // dummy seperator
//    mainFrame.makeTextField  (panel, "TXT_1" , "Dummy1" , LEFT,  false, "------", false);
//    mainFrame.makeTextField  (panel, "TXT_2" , "Dummy2" , LEFT,  true , "------", false);

    panel = "PNL_CONTROL";
    mainFrame.makeLabel    (panel, "LBL_1"        , "Jar file:"   , LEFT, false);
    mainFrame.makeLabel    (panel, "LBL_JARFILE"  , "           " , LEFT, true);
    mainFrame.makeCombobox (panel, "COMBO_MAINCLS", "Main Class"  , LEFT, true);
    mainFrame.makeLabel    (panel, "LBL_2"        , " "           , LEFT, true); // dummy separator
    mainFrame.makeCombobox (panel, "COMBO_CLASS"  , "Class"       , LEFT, true);
    mainFrame.makeCombobox (panel, "COMBO_METHOD" , "Method"      , LEFT, true);
    mainFrame.makeTextField(panel, "TXT_INPUT"    , "Input"       , LEFT, true, "", true);
    mainFrame.makeButton   (panel, "BTN_LOADFILE" , "Select Jar"  , LEFT, false);
    mainFrame.makeButton   (panel, "BTN_BYTECODE" , "Get Bytecode", LEFT, false);
    mainFrame.makeButton   (panel, "BTN_RUNTEST"  , "Run code"    , LEFT, true);

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
    mainFrame.getTextField("TXT_INPUT").setEnabled(false);
    
    // we need a filechooser for the Save buttons
    GuiPanel.fileSelector = new JFileChooser();

    // setup the control actions
    GuiPanel.mainFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        formWindowClosing(evt);
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
        loadFileButtonActionPerformed(evt);
        
        // enable the class and method selections
        mainClassCombo.setEnabled(false);
        classCombo.setEnabled(true);
        methodCombo.setEnabled(true);
        mainFrame.getLabel("COMBO_MAINCLS").setEnabled(true);
        mainFrame.getLabel("COMBO_CLASS").setEnabled(true);
        mainFrame.getLabel("COMBO_METHOD").setEnabled(true);
        mainFrame.getButton("BTN_BYTECODE").setEnabled(true);
        mainFrame.getButton("BTN_RUNTEST").setEnabled(true);
        mainFrame.getTextField("TXT_INPUT").setEnabled(true);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_BYTECODE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        runBytecode(evt);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_RUNTEST")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        runTest(evt);
      }
    });
    // setMethodSelections(String clsname)

    // display the frame
    GuiPanel.mainFrame.display();

    // setup the font selections for the bytecode display
    String fonttype = "Courier";
    HashMap<String, FontInfo> map = bytecodeFontTbl;
    // generic types
    setTypeColor (map, "ERROR",   FontInfo.TextColor.Red,    FontInfo.FontType.Bold  , 14, fonttype);
    setTypeColor (map, "WARN",    FontInfo.TextColor.Orange, FontInfo.FontType.Bold  , 14, fonttype);
    // the method name banner
    setTypeColor (map, "METHOD",  FontInfo.TextColor.Violet, FontInfo.FontType.Italic, 16, fonttype);
    // parts of the bytecode info
    setTypeColor (map, "TEXT",    FontInfo.TextColor.Black,  FontInfo.FontType.Italic, 14, fonttype);
    setTypeColor (map, "PARAM",   FontInfo.TextColor.Brown,  FontInfo.FontType.Normal, 14, fonttype);
    setTypeColor (map, "COMMENT", FontInfo.TextColor.Green,  FontInfo.FontType.Italic, 14, fonttype);
    // the bytecode opcode formats
    setTypeColor (map, "BRANCH",  FontInfo.TextColor.DkVio,  FontInfo.FontType.Bold  , 14, fonttype);
    setTypeColor (map, "INVOKE",  FontInfo.TextColor.Gold,   FontInfo.FontType.Bold  , 14, fonttype);
    setTypeColor (map, "LOAD",    FontInfo.TextColor.Green,  FontInfo.FontType.Normal, 14, fonttype);
    setTypeColor (map, "STORE",   FontInfo.TextColor.Blue,   FontInfo.FontType.Normal, 14, fonttype);
    setTypeColor (map, "OTHER",   FontInfo.TextColor.Black,  FontInfo.FontType.Normal, 14, fonttype);
    
    // setup the font selections for the command display
    map = commandFontTbl;
    setTypeColor (map, "ERROR",   FontInfo.TextColor.Red,    FontInfo.FontType.Bold  , 14, fonttype);
    setTypeColor (map, "NORMAL",  FontInfo.TextColor.DkGrey, FontInfo.FontType.Normal, 14, fonttype);
    setTypeColor (map, "COMMAND", FontInfo.TextColor.Black,  FontInfo.FontType.Bold  , 14, fonttype);

    // add the tabbed message panels for bytecode output, command output, and debug output
    bytecodeLogger = addTextPanelToTab(PanelTabs.BYTECODE, bytecodeFontTbl);
    commandLogger  = addTextPanelToTab(PanelTabs.COMMAND, commandFontTbl);
    debugLogger    = addTextPanelToTab(PanelTabs.DEBUG, null); // TODO: setup fontmap

    // add the tabbed CallGraph panel
//    GuiPanel.graphPanel = new JPanel();
//    JScrollPane graphScrollPanel = new JScrollPane(GuiPanel.graphPanel);
//    tabPanel.addTab("Call Graph", graphScrollPanel);
//    tabSelect.put(PanelTabs.GRAPH, tabIndex++);
//    CallGraph.initCallGraph(GuiPanel.graphPanel);

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
//
//    // create a timer for updating the statistics
//    statsTimer = new Timer(100, new StatsUpdateListener());
//    statsTimer.start();

    // start timer when 1st line is received from port
//    GuiPanel.elapsedMode = ElapsedMode.RESET;
  }

  private Logger addTextPanelToTab(PanelTabs tabname, HashMap<String, FontInfo> fontmap) {
    // create the Logger
    Logger logger = new Logger(tabname.toString(), fontmap);
    
    // add the textPane to a scrollPane
    JTextPane textPane = logger.getTextPane();

    // this was supposed to enable word wrap on JTextPane, but doesn't work
//    JPanel noWrapPanel = new JPanel(new BorderLayout());
//    noWrapPanel.add( textPane );
//    JScrollPane scrollPanel = new JScrollPane(noWrapPanel);
//    scrollPanel.setViewportView(textPane);
    
    JScrollPane scrollPanel = new JScrollPane(textPane);
    scrollPanel.setBorder(BorderFactory.createTitledBorder(""));
    
    // now add the scroll pane to the tabbed pane
    tabPanel.addTab(tabname.toString(), scrollPanel);
    tabSelect.put(tabname, tabIndex++);
    return logger;
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

  private static boolean isTabSelection(PanelTabs select) {
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
  
  private class StatsUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // update statistics
      int methods = CallGraph.getMethodCount();
      
      (GuiPanel.mainFrame.getTextField("TXT_1")).setText("stuff");
      (GuiPanel.mainFrame.getTextField("TXT_2")).setText("more stuff");
          
      // update elapsed time if enabled
      GuiPanel.updateElapsedTime();
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

  private static void printStatus(String message) {
    if (message == null) {
      mainFrame.getTextField("TXT_MESSAGES").setText("                   ");
    } else {
      mainFrame.getTextField("TXT_MESSAGES").setText(message);
      commandLogger.printMaxLength("ERROR", message, 120);
    }
  }
  
  private static boolean fileCheck(String fname) {
    if (new File(fname).isFile()) {
      return true;
    }

    printStatus("Missing file: " + fname);
    return false;
  }
  
  private static void loadFileButtonActionPerformed(java.awt.event.ActionEvent evt) {
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
        commandLogger.printMaxLength("NORMAL", commandLauncher.getResponse(), 120);
      
        // update the class and method selections
        setupClassList(projectPathName);
      } else {
        printStatus("ERROR: instrumenting file: " + projectName);
      }
    }
  }
  
  private static void runBytecode(java.awt.event.ActionEvent evt) {
    String classSelect  = (String) classCombo.getSelectedItem();
    String methodSelect = (String) methodCombo.getSelectedItem();
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
      parseJavap(classSelect, methodSelect, content);
      printStatus("Successfully generated bytecode");
    } else {
      printStatus("ERROR: running javap on file: " + classSelect + ".class");
    }
  }

  private static void runTest(java.awt.event.ActionEvent evt) {
    // clear the output display
    commandLogger.clear();

    String instrJarFile = projectPathName + projectName.substring(0, projectName.indexOf(".")) + "-dan-ed.jar";
    
    // verify all the required files exist
    if (!fileCheck(instrJarFile) ||
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
    String arglist = mainFrame.getTextField("TXT_INPUT").getText();

    String JAVA_HOME = "/usr/lib/jvm/java-8-openjdk-amd64";
    String options = "-Xverify:none -Dsun.boot.library.path=" + JAVA_HOME + "/bin:/usr/lib";
    String bootcpath ="-Xbootclasspath/a:"
                      + DSEPATH + "danalyzer/dist/danalyzer.jar:"
                      + DSEPATH + "danalyzer/lib/com.microsoft.z3.jar";
    String agentpath ="-agentpath:" + DSEPATH + "danhelper/libdanhelper.so";
    String classpath = instrJarFile;
    classpath += ":" + DSEPATH + "danalyzer/lib/commons-io-2.5.jar";
    classpath += ":" + DSEPATH + "danalyzer/lib/asm-all-5.2.jar";
    classpath += ":/*:/lib/*:/libs/*";

    // run the instrumented jar file
    String[] command = { "java", options, bootcpath, agentpath, "-cp", classpath, mainclass, arglist };
    CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
    int retcode = commandLauncher.start(command, projectPathName);
    if (retcode == 0) {
      mainFrame.getLabel("LBL_JARFILE").setText("Running: " + projectName);
      
      // TODO: start timer to wait for response to show cost and elapsed time
    } else {
      printStatus("ERROR: running instrumented jar file: " + projectName);
    }
  }
  
  private static void formWindowClosing(java.awt.event.WindowEvent evt) {
    if (graphTimer != null) {
      graphTimer.stop();
    }
    if (pktTimer != null) {
      pktTimer.stop();
    }
    if (statsTimer != null) {
      statsTimer.stop();
    }
    mainFrame.close();
    System.exit(0);
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

      bytecodeLogger.printBytecode(line, opcode, param, comment);
    }
  }

  private static void parseJavap(String classSelect, String methodSelect, String content) {
commandLogger.printMaxLength("NORMAL", "searching for: " + classSelect + "." + methodSelect, 120);

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
commandLogger.printMaxLength("NORMAL", "entry is bytecode: " + entry, 120);
          if (found) {
            if (line < lastline) {
commandLogger.printMaxLength("NORMAL", "line count indicates new method: " + line, 120);
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
commandLogger.printMaxLength("NORMAL", "method found in: " + entry, 120);
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
commandLogger.printMaxLength("NORMAL", "method: " + methName, 120);
        // method entry found, let's see if it's the one we want
        if (methodSelect.startsWith(methName)) {
          found = true;
commandLogger.printMaxLength("NORMAL", "method: FOUND!", 120);
          bytecodeLogger.printMethod(classSelect + "." + methodSelect);
        } else if (found) {
          // athe next method has been found in the file - stop parsing
          break;
        }
      }
    }
  }
  
}
