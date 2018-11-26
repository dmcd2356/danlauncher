/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import gui.GuiControls;
import static gui.GuiControls.Orient.CENTER;
import static gui.GuiControls.Orient.LEFT;
import main.LauncherMain;
import util.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;

/**
 *
 * @author dmcd2356
 */
public class CallGraph {
  
  private static final int ALL_THREADS = -1;

  private static String  pnlname;
  private static JPanel  graphPanel;
  private static mxGraphComponent graphComponent;
  private static BaseGraph<MethodInfo> callGraph = new BaseGraph<>();
  private static List<MethodInfo> graphMethList = new ArrayList<>(); // list of all methods found
  private static List<MethodInfo> threadMethList = new ArrayList<>(); // list of all methods for selected thread
  private static final HashMap<Integer, Stack<Integer>> callStack = new HashMap<>(); // stack for each thread
  private static long rangeStepsize;  // the stepsize to use for highlighting
  private static int numNodes;
  private static int numEdges;
  private static int threadSel;
  private static LauncherMain.GraphHighlight curGraphMode;
  private static boolean graphShowAllThreads;
  private static final GuiControls methInfoPanel = new GuiControls();
  private static MethodInfo selectedMethod;
  
  public CallGraph(String name) {
    pnlname = name;
    graphPanel = new JPanel();

    clearGraph();
    graphMethList = new ArrayList<>();
    curGraphMode = LauncherMain.GraphHighlight.NONE;
    graphShowAllThreads = true;
    rangeStepsize = 20;
  }
  
  /**
   * this resets the graphics so a new call graph can be drawn.
   * the current list of methods is not changed.
   */
  private void clearGraph() {
    callGraph = new BaseGraph<>();
    graphComponent = null;
    numNodes = 0;
    numEdges = 0;
    threadSel = 0;

    if (graphPanel != null) {
      graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }

  private Stack<Integer> getStack(int tid) {
    Stack<Integer> stack;
    if (!callStack.isEmpty() && callStack.containsKey(tid)) {
      // get last method placed in stack of specified thread
      stack = callStack.get(tid);
    } else {
      // no stack for the specified thread, create one
      stack = new Stack<>();
      callStack.put(tid, stack);
    }
    
    return stack;
  }
  
  private static MethodInfo findMethodEntry(int tid, String method, List<MethodInfo> methlist) {
    for (int ix = 0; ix < methlist.size(); ix++) {
      MethodInfo entry = methlist.get(ix);
      if (entry.getFullName().equals(method)) {
        if (tid == ALL_THREADS || entry.getThread().contains(tid)) {
          return entry;
        }
      }
    }
    return null;
  }

  private static double calcStepRatio(long value, long minValue, long range) {
    // set step size (20 will highlight in steps of 20% from 100 to 0, 1 will do steps of 1% from 100 to 95)
    double ratio = 100.0 * (double)(value - minValue) / (double) range;
    long stepratio = 100 - ((100 - (long) ratio) / rangeStepsize) * rangeStepsize;

    long stepmin = 100 - (rangeStepsize * 5); // 5 is the number of steps we will display
    if (stepratio < stepmin || stepratio < 0) {
      return 0.0;
    }
    
    ratio = stepratio > 100 ? 1.0 : (double) stepratio / 100.0;
    return ratio;
  }
  
  private static long getMethodValue(LauncherMain.GraphHighlight gmode, int tid, MethodInfo mthNode) {
    switch (gmode) {
      case TIME :
        return mthNode.getDuration(tid);
      case INSTRUCTION :
        return mthNode.getInstructionCount(tid);
      case ITERATION :
        return mthNode.getCount(tid);
      default:
        break;
    }
    return -1;
  }

  private void setGraphBlockColors(LauncherMain.GraphHighlight gmode, int tid) {
      // find the min and max limits for those cases where we color based on relative value
      long value;
      long maxValue = 0;
      long minValue = Long.MAX_VALUE;
      for(MethodInfo mthNode : graphMethList) {
        value = getMethodValue(gmode, tid, mthNode);
        if (value >= 0) {
          maxValue = (maxValue < value) ? value : maxValue;
          minValue = (minValue > value) ? value : minValue;
        }
      }
      long range = maxValue - minValue;

      // update colors based on time usage or number of calls
      for (int ix = 0; ix < graphMethList.size(); ix++) {
        MethodInfo mthNode = graphMethList.get(ix);
        int colorR, colorG, colorB;
        String color = "D2E9FF";  // default color is greay
        double ratio = 1.0;
        switch (gmode) {
          default :
            break;
          case STATUS :
            // mark methods that have not exited
            if (mthNode.getInstructionCount(tid) < 0 || mthNode.getDuration(tid) < 0) {
              color = "CCFFFF"; // cyan
            }
            if (mthNode.getExecption(tid) >= 0) {
              color = "FF6666"; // orange
            }
            if (mthNode.getError(tid) >= 0) {
              color = "FFCCCC"; // pink
            }
            break;
          case TIME :
            value = getMethodValue(gmode, tid, mthNode);
            if (range > 0 && value > minValue) {
              ratio = calcStepRatio(value, minValue, range);
              if (ratio > 0.0) {
                // this runs from FF6666 (red) to FFCCCC (light red)
                colorR = 255;
                colorG = 204 - (int) (102.0 * ratio);
                colorB = 204 - (int) (102.0 * ratio);
                color = String.format ("%06x", (colorR << 16) + (colorG << 8) + colorB);
              }
            }
            break;
          case INSTRUCTION :
            value = getMethodValue(gmode, tid, mthNode);
            if (range > 0 && value > minValue) {
              ratio = calcStepRatio(value, minValue, range);
              if (ratio > 0.0) {
                // this runs from 66FF66 (green) to CCFFCC (light green)
                colorR = 204 - (int) (102.0 * ratio);
                colorG = 255;
                colorB = 204 - (int) (102.0 * ratio);
                color = String.format ("%06x", (colorR << 16) + (colorG << 8) + colorB);
              }
            }
            break;
          case ITERATION :
            value = getMethodValue(gmode, tid, mthNode);
            if (range > 0 && value > minValue && value >= 10) {
              ratio = calcStepRatio(value, minValue, range);
              if (ratio > 0.0) {
                // this runs from 6666FF (blue) to CCCCFF (light blue)
                colorR = 204 - (int) (102.0 * ratio);
                colorG = 204 - (int) (102.0 * ratio);
                colorB = 255;
                color = String.format ("%06x", (colorR << 16) + (colorG << 8) + colorB);
              }
            }
            break;
          case THREAD :
            // color all methods that are in the specified thread
            ArrayList<Integer> threadlist = mthNode.getThread();
            if (threadlist.contains(threadSel)) {
              if (threadlist.size() > 1) {
                color = "FFCCCC"; // pink if shared with other threads
              } else {
                color = "6666FF"; // medium blue
              }
            }
            break;
        }

        // set minimum threshhold
        if (ratio < 0.2 ) {
          color = "D2E9FF";
        }

        callGraph.colorVertex(mthNode, color);
        //LauncherMain.printCommandMessage(color + " for: " + mthNode.getFullName());
      }
  }
  
  /**
   * draws the graph as defined by graphMethList.
   * 
   * @return the number of threads found
   */  
  private void drawCG(List<MethodInfo> methList) {
    callGraph = new BaseGraph<>();

    LauncherMain.printCommandMessage("drawCG: Methods = " + methList.size());
    // add vertexes to graph
    for(MethodInfo mthNode : methList) {
      callGraph.addVertex(mthNode, mthNode.getCGName());
    }
    
    // now connect the methods to their parents
    for (MethodInfo mthNode : methList) {
      // for each parent entry for a method...
      for (String parent : mthNode.getParents(ALL_THREADS)) {
        // find MethodInfo for the parent
        MethodInfo parNode = findMethodEntry(ALL_THREADS, parent, methList);
        // only add connection if parent was found and there isn't already a connection
        if (parNode != null && callGraph.getEdge(parNode, mthNode) == null) {
          // now add the connection from the method to the parent
          callGraph.addEdge(parNode, mthNode, null);
        }
      }
    }
  }
  
  public void clearGraphAndMethodList() {
    clearGraph();
    curGraphMode = LauncherMain.GraphHighlight.NONE;
    graphShowAllThreads = true;
    graphMethList = new ArrayList<>();
  }
  
  public JPanel getPanel() {
    return graphPanel;
  }
  
  public int getMethodCount() {
    return graphMethList.size();
  }

  public MethodInfo getLastMethod(int tid) {
    Stack<Integer> stack = getStack(tid);
    if (graphMethList == null || graphMethList.size() < 1 || stack == null || stack.empty()) {
      return null;
    }

    // this should not happen, but let's be safe
    if (stack.peek() >= graphMethList.size()) {
      return null;
    }
    
    return graphMethList.get(stack.peek());
  }
  
  public void setThreadSelection(int select) {
    threadSel = select;
  }
  
  public void setRangeStepSize(long step) {
    rangeStepsize = step;
  }
  
//  /**
//   * generates the call graph for the selected thread.
//   * 
//   * @param tid
//   * @return true if graph was updated
//   */  
//  public boolean generateCallGraph(int tid) {
//    clearGraph();
//
//    // create a new method list if we are only doiung a single thread
//    if (tid >= 0) {
//      threadMethList = new ArrayList<>();
//      for(MethodInfo mthNode : graphMethList) {
//        ArrayList<Integer> threadList = mthNode.getThread();
//        if (threadList.contains(tid)) {
//          threadMethList.add(mthNode);
//        }
//      }
//      drawCG(threadMethList);
//    } else {
//      drawCG(graphMethList);
//    }
//
//    updateCallGraph(curGraphMode, true);
//
//    graphShowAllThreads = tid < 0;
//    return true;
//  }
  
  /**
   * updates the call graph display
   * 
   * @param gmode
   * @param force
   * @return true if graph was updated
   */  
  public boolean updateCallGraph(LauncherMain.GraphHighlight gmode, boolean force) {
    boolean updated = false;

    // exit if the graphics panel has not been established
    if (graphPanel == null) {
      return false;
    }
    
    // only run if a node or edge has been added to the graph or a color mode has changed
    if (callGraph.getEdgeCount() != numEdges ||
        callGraph.getVertexCount() != numNodes ||
        curGraphMode != gmode || force) {

      // update the state
      numEdges = callGraph.getEdgeCount();
      numNodes = callGraph.getVertexCount();
      curGraphMode = gmode;

      // if no graph has been composed yet, set it up now
      mxGraph graph = callGraph.getGraph();
      if (graphComponent == null) {
        graphComponent = new mxGraphComponent(graph);
        graphComponent.setConnectable(false);
        
        // add listener to show details of selected element
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
          @Override
          public void mouseReleased(MouseEvent e) {
            mxGraphHandler handler = graphComponent.getGraphHandler();
            mxCell cell = (mxCell) handler.getGraphComponent().getCellAt(e.getX(), e.getY());
            if (cell != null && cell.isVertex()) {
              MethodInfo selected = callGraph.getSelectedNode();
              displayMethodInfoPanel(selected);
            }
          }
        });
        graphPanel.add(graphComponent);
      }

      int tid = ALL_THREADS;
      if (graphShowAllThreads == false) {
        tid = threadSel;
        if (gmode == LauncherMain.GraphHighlight.THREAD) {
          gmode = LauncherMain.GraphHighlight.NONE;
        }
      }

      // set the colors of the method blocks based on the mode selection
      setGraphBlockColors(gmode, tid);
      
      // update the contents of the graph component
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
      
      // update the graph layout
      callGraph.layoutGraph();
      updated = true;
    }

    return updated;
  }

  private String getSelectedMethodInfo(MethodInfo selected, int tid) {
    // setup the message contents to display
    String message = "";
    message += "Class: " + selected.getClassName()+ Utils.NEWLINE;
    message += "Method: " + selected.getMethodName() + selected.getMethodSignature() + Utils.NEWLINE;
    if (tid >= 0) {
      message += "Thread: " + tid + Utils.NEWLINE;
    } else if (!selected.getThread().isEmpty()) {
      message += "Thread: " + selected.getThread().toString() + Utils.NEWLINE;
    }
    message += Utils.NEWLINE;
    message += "Iterations: " + selected.getCount(tid) + Utils.NEWLINE;
    message += "Execution Time: " + (selected.getDuration(tid) < 0 ?
               "(never returned)" : selected.getDuration(tid) + " msec") + Utils.NEWLINE;
    message += "Instruction Count: " + (selected.getInstructionCount(tid) < 0 ?
               "(no info)" : selected.getInstructionCount(tid)) + Utils.NEWLINE;
    message += Utils.NEWLINE;
    message += "1st called @ line: " + selected.getFirstLine(tid) + Utils.NEWLINE;
    if (selected.getExecption(tid) > 1) {
      message += "exception @ line: " + selected.getExecption(tid) + Utils.NEWLINE;
    }
    if (selected.getError(tid) > 1) {
      message += "error @ line: " + selected.getError(tid) + Utils.NEWLINE;
    }
      
    if (!selected.getParents(tid).isEmpty()) {
      if (selected.getParents(tid).size() == 1) {
        message += Utils.NEWLINE + "Caller: " + selected.getParents(tid).get(0) + Utils.NEWLINE;
      } else {
        message += Utils.NEWLINE + "Calling Methods: " + Utils.NEWLINE;
        for(String name : selected.getParents(tid)) {
          if (name != null && !name.isEmpty() && !name.equals("null")) {
            message += "   " + name + Utils.NEWLINE;
          }
        }
      }
    }

    return message;
  }
  
  public void displayMethodInfoPanel(MethodInfo selected) {
    JFrame frame = methInfoPanel.newFrame("Method Info", 520, 350, GuiControls.FrameSize.NOLIMIT);
    frame.addWindowListener(new Window_ExitListener());

    selectedMethod = selected;
    int threadCount = selected.getThread().size();
    int threadInit = selected.getThread().get(0);

    String panel = null;
    methInfoPanel.makePanel (panel, "PNL_INFO"  , "", LEFT, true, 500, 290);
    
    panel = "PNL_INFO";
    if (threadCount > 1) {
      JCheckBox cboxAll = 
          methInfoPanel.makeCheckbox(panel, "CBOX_ALLTHREADS", "All threads", LEFT, false, 1);
      JLabel threadVal =
          methInfoPanel.makeLabel   (panel, "TXT_TH_SEL", "" + threadInit, LEFT, false);
      JButton upButton =
          methInfoPanel.makeButton  (panel, "BTN_TH_UP" , "UP" , LEFT, false);
      JButton dnButton =
          methInfoPanel.makeButton  (panel, "BTN_TH_DN" , "DN" , LEFT, true);

      cboxAll.addActionListener(new Action_AllThreadSelect());
      upButton.addActionListener(new Action_ThreadUp());
      dnButton.addActionListener(new Action_ThreadDown());
      
      threadVal.setEnabled(false);
      upButton.setEnabled(false);
      dnButton.setEnabled(false);
    }
    JTextPane textPanel =
        methInfoPanel.makeScrollText(panel, "TXT_METHINFO", "");
    JButton bcodeButton =
        methInfoPanel.makeButton(panel, "BTN_BYTECODE", "Show bytecode", CENTER, true);
    
    bcodeButton.addActionListener(new Action_RunBytecode());
    
    // setup initial method info text
    String message = getSelectedMethodInfo(selected, ALL_THREADS);
    textPanel.setText(message);

    methInfoPanel.display();
  }
  
  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      methInfoPanel.close();
    }
  }

  private class Action_RunBytecode implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      MethodInfo selected = callGraph.getSelectedNode();
      String cls = selected.getFullName();
      int offset = cls.lastIndexOf(".");
      String meth = cls.substring(offset + 1);
      cls = cls.substring(0, offset);
      LauncherMain.runBytecodeViewer(cls, meth);
    }
  }
  
  private class Action_AllThreadSelect implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String message;
      boolean threadEnable;
      if (((JCheckBox)evt.getSource()).isSelected()) {
        message = getSelectedMethodInfo(selectedMethod, ALL_THREADS);
        threadEnable = false;
      } else {
        JLabel label = methInfoPanel.getLabel("TXT_TH_SEL");
        int value = Integer.parseInt(label.getText().trim());
        message = getSelectedMethodInfo(selectedMethod, value);
        threadEnable = true;
      }

      methInfoPanel.getLabel("TXT_TH_SEL").setEnabled(threadEnable);
      methInfoPanel.getButton("BTN_TH_UP").setEnabled(threadEnable);
      methInfoPanel.getButton("BTN_TH_DN").setEnabled(threadEnable);
      
      methInfoPanel.getTextPane("TXT_METHINFO").setText(message);
    }
  }
  
  private class Action_ThreadUp implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = methInfoPanel.getLabel("TXT_TH_SEL");
      int value = Integer.parseInt(label.getText().trim());

      // search for current value in thread list
      int threadCount = selectedMethod.getThread().size();
      for (int ix = 0; ix < threadCount - 1; ix++) {
        if (selectedMethod.getThread().get(ix) == value) {
          value = selectedMethod.getThread().get(ix + 1);
          label.setText("" + value);
          
          String message = getSelectedMethodInfo(selectedMethod, value);
          methInfoPanel.getTextPane("TXT_METHINFO").setText(message);
        }
      }
    }
  }
  
  private class Action_ThreadDown implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = methInfoPanel.getLabel("TXT_TH_SEL");
      int value = Integer.parseInt(label.getText().trim());

      // search for current value in thread list
      int threadCount = selectedMethod.getThread().size();
      for (int ix = 1; ix < threadCount; ix++) {
        if (selectedMethod.getThread().get(ix) == value) {
          value = selectedMethod.getThread().get(ix - 1);
          label.setText("" + value);
          
          String message = getSelectedMethodInfo(selectedMethod, value);
          methInfoPanel.getTextPane("TXT_METHINFO").setText(message);
        }
      }
    }
  }
  
  public void saveAsImageFile(File file) {
    BufferedImage bi = new BufferedImage(graphPanel.getSize().width,
      graphPanel.getSize().height, BufferedImage.TYPE_INT_ARGB); 
    Graphics graphics = bi.createGraphics();
    graphPanel.paint(graphics);
    graphics.dispose();
    try {
      ImageIO.write(bi,"png",file);
    } catch (IOException ex) {
      LauncherMain.printCommandError("ERROR: " + ex.getMessage());
    }
  }

  /**
   * finds the specified method entry in the list of saved methods
   * @param tid
   * @param method
   * @return 
   */
  /**
   * reads the CallGraph.graphMethList entries and saves to file
   * 
   * @param file - name of file to save content to
   * @param allThreads
   */  
  public void saveAsJSONFile(File file, boolean allThreads) {
    // open the file to write to
    BufferedWriter bw;
    try {
      bw = new BufferedWriter(new FileWriter(file));
    } catch (IOException ex) {
      LauncherMain.printCommandError("ERROR: " + ex.getMessage());
      return;
    }

    // select whether we are saving the complete list or just one thread
    List<MethodInfo> methlist = graphMethList;
    if (!allThreads && !threadMethList.isEmpty()) {
      methlist = threadMethList;
    }

    // convert to json and save to file
		GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting().serializeNulls();
    //builder.excludeFieldsWithoutExposeAnnotation().create();
		Gson gson = builder.create();
    gson.toJson(methlist, bw);

    try {
      bw.close();
    } catch (IOException ex) {
      LauncherMain.printCommandError("ERROR: " + ex.getMessage());
    }
  }
  
  /**
   * reads method info from specified file and saves in CallGraph.graphMethList
   * 
   * @param file - name of file to load data from
   * @return 
   */  
  public int loadFromJSONFile(File file) {
    // open the file to read from
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
    } catch (FileNotFoundException ex) {
      LauncherMain.printCommandError("ERROR: " + ex.getMessage());
      return 0;
    }
    
    // load the method list info from json file
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
    Type methodListType = new TypeToken<List<MethodInfo>>() {}.getType();
    graphMethList = gson.fromJson(br, methodListType);
    LauncherMain.printCommandMessage("loaded: " + graphMethList.size() + " methods");

    // draw the new graph (always do the full list)
    clearGraph();
    drawCG(graphMethList);
    
    // count the number of threads
    ArrayList<Integer> list = new ArrayList<>();
    for(MethodInfo mthNode : graphMethList) {
      ArrayList<Integer> threadList = mthNode.getThread();
      if (!threadList.isEmpty()) {
        for (Integer threadid : threadList) {
          if (!list.contains(threadid)) {
            list.add(threadid);
          }
        }
      }
    }
    return list.size();
  }
  
  /**
   * adds a method to the call graph if it is not already there
   * 
   * @param tid      - the thread id
   * @param tstamp   - timestamp in msec from msg
   * @param icount   - instruction count (empty if not known)
   * @param method   - the full name of the method to add
   * @param line     - the line number corresponding to the call event
   */  
  public void methodEnter(int tid, long tstamp, String icount, String method, int line) {
    if (method == null || method.isEmpty() || icount.isEmpty() || graphMethList == null) {
      return;
    }

    // since we're going to add the method to the stack, we first check if there is a stack
    // created for the current thread. If not, we need to create an empty one now.
    Stack<Integer> stack = getStack(tid);

    // get the parent from the stack
    String parent = ""; // if there was no caller on this thread, leave as parentless
    if (!stack.empty()) {
      int ix = stack.peek();
      if (ix >= 0 && ix < graphMethList.size()) {
        MethodInfo mthNode = graphMethList.get(ix);
        if (mthNode != null) {
          parent = mthNode.getFullName();
        }
      }
    }

    int insCount = -1;
    if (!icount.isEmpty()) {
      try {
        insCount = Integer.parseUnsignedInt(icount);
      } catch (NumberFormatException ex) { }
    }
    
    // find parent entry in list
    MethodInfo parNode = null;
    if (!parent.isEmpty()) {
      parNode = findMethodEntry(ALL_THREADS, parent, graphMethList);
//      if (parNode == null && method.contains("<clinit>")) {
//        parNode = new MethodInfo(tid, method, parent, tstamp, insCount, line);
//        graphMethList.add(parNode);
//        callGraph.addVertex(parNode, parNode.getCGName());
//      }
    }

    // find called method in list and create (or update) info
    MethodInfo mthNode = null;
    boolean newnode = true;
    int count = graphMethList.size();
    for (int ix = 0; ix < count; ix++) {
      mthNode = graphMethList.get(ix);
      if (mthNode.getFullName().equals(method)) {
        // update stats for new call to method
        mthNode.repeatedCall(tid, parent, tstamp, insCount, line);
        newnode = false;

        // update saved stack for this thread
        stack.push(ix);
      }
    }
    // if not found, create new one and add it to list
    if (newnode) {
      mthNode = new MethodInfo(tid, method, parent, tstamp, insCount, line);
      graphMethList.add(mthNode);

      // if thread id matches the current selection, add this entry to the current single thread method list
      if (tid == threadSel) {
        threadMethList.add(mthNode);
      }
      
      // update saved stack for this thread
      stack.push(count);
    }

    // add node (if not previously defined) and/or edge (if parent defined) to graph
    if (graphShowAllThreads == true || threadSel == tid) {
      if (newnode) {
        callGraph.addVertex(mthNode, mthNode.getCGName());
      }
      if (parNode != null && callGraph.getEdge(parNode, mthNode) == null) {
        callGraph.addEdge(parNode, mthNode, null);
      }
    }
  }

  /**
   * adds exit condition info to a method in the call graph
   * 
   * @param tid      - the thread id
   * @param tstamp   - timestamp in msec from msg
   * @param icount   - instruction count (empty if not known)
   */  
  public void methodExit(int tid, long tstamp, String icount) {
    if (graphMethList == null) {
      //LauncherMain.printCommandMessage("Return: " + method + " - NOT FOUND!");
      return;
    }

    int insCount = -1;
    if (!icount.isEmpty()) {
      try {
        insCount = Integer.parseUnsignedInt(icount);
      } catch (NumberFormatException ex) { }
    }
    
    // get method we are returning from (last entry in stack for this thread)
    Stack<Integer> stack = getStack(tid);
    if (stack != null && !stack.empty()) {
      int ix = stack.pop();
      if (ix >= 0 && ix < graphMethList.size()) {
        MethodInfo mthNode = graphMethList.get(ix);
        mthNode.exit(tid, tstamp, insCount);
        //LauncherMain.printCommandMessage("Return: (" + mthNode.getDuration() + ") " + mthNode.getClassAndMethod());
      }
    }
  }
  
}
