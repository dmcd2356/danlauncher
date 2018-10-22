/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import main.GuiPanel;
import java.awt.Graphics;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 *
 * @author dmcd2356
 */
public class CallGraph {
  
  private static final String NEWLINE = System.getProperty("line.separator");
  private static final int ALL_THREADS = -1;

  private static JPanel           graphPanel = null;
  private static mxGraphComponent graphComponent = null;
  private static BaseGraph<MethodInfo> callGraph = new BaseGraph<>();
  private static List<MethodInfo> graphMethList = new ArrayList<>(); // list of all methods found
  private static List<MethodInfo> threadMethList = new ArrayList<>(); // list of all methods for selected thread
  private static HashMap<Integer, Stack<Integer>> callStack = new HashMap<>(); // stack for each thread
  private static long rangeStepsize;  // the stepsize to use for highlighting
  private static int numNodes;
  private static int numEdges;
  private static int threadSel;
  private static GuiPanel.GraphHighlight curGraphMode;
  private static boolean graphShowAllThreads;
  
  private static Stack<Integer> getStack(int tid) {
    Stack<Integer> stack;
    if (!CallGraph.callStack.isEmpty() && CallGraph.callStack.containsKey(tid)) {
      // get last method placed in stack of specified thread
      stack = CallGraph.callStack.get(tid);
    } else {
      // no stack for the specified thread, create one
      stack = new Stack<>();
      CallGraph.callStack.put(tid, stack);
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
  
  private static long getMethodValue(GuiPanel.GraphHighlight gmode, int tid, MethodInfo mthNode) {
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

  private static void setGraphBlockColors(GuiPanel.GraphHighlight gmode, int tid) {
      // find the min and max limits for those cases where we color based on relative value
      long value;
      long maxValue = 0;
      long minValue = Long.MAX_VALUE;
      for(MethodInfo mthNode : CallGraph.graphMethList) {
        value = getMethodValue(gmode, tid, mthNode);
        if (value >= 0) {
          maxValue = (maxValue < value) ? value : maxValue;
          minValue = (minValue > value) ? value : minValue;
        }
      }
      long range = maxValue - minValue;

      // update colors based on time usage or number of calls
      for (int ix = 0; ix < CallGraph.graphMethList.size(); ix++) {
        MethodInfo mthNode = CallGraph.graphMethList.get(ix);
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
            if (threadlist.contains(CallGraph.threadSel)) {
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

        CallGraph.callGraph.colorVertex(mthNode, color);
        //System.out.println(color + " for: " + mthNode.getFullName());
      }
  }
  
  /**
   * draws the graph as defined by CallGraph.graphMethList.
   * 
   * @return the number of threads found
   */  
  private static void drawCG(List<MethodInfo> methList) {
    CallGraph.callGraph = new BaseGraph<>();

System.out.println("drawCG: Methods = " + methList.size());
    // add vertexes to graph
    for(MethodInfo mthNode : methList) {
      CallGraph.callGraph.addVertex(mthNode, mthNode.getCGName());
    }
    
    // now connect the methods to their parents
    for (MethodInfo mthNode : methList) {
      // for each parent entry for a method...
      for (String parent : mthNode.getParents(ALL_THREADS)) {
        // find MethodInfo for the parent
        MethodInfo parNode = findMethodEntry(ALL_THREADS, parent, methList);
        // only add connection if parent was found and there isn't already a connection
        if (parNode != null && CallGraph.callGraph.getEdge(parNode, mthNode) == null) {
          // now add the connection from the method to the parent
          CallGraph.callGraph.addEdge(parNode, mthNode, null);
        }
      }
    }
  }
  
  /**
   * this initializes CallGraph
   * 
   * @param panel 
   */
  public static void initCallGraph(JPanel panel) {
    clearGraphAndMethodList();
    CallGraph.curGraphMode = GuiPanel.GraphHighlight.NONE;
    CallGraph.graphShowAllThreads = true;
    CallGraph.rangeStepsize = 20;
    CallGraph.graphPanel = panel;
  }

  /**
   * this resets the graphics so a new call graph can be drawn and resets the method list.
   */
  public static void clearGraphAndMethodList() {
    clearGraph();
    CallGraph.curGraphMode = GuiPanel.GraphHighlight.NONE;
    CallGraph.graphShowAllThreads = true;
    CallGraph.graphMethList = new ArrayList<>();
  }
  
  /**
   * this resets the graphics so a new call graph can be drawn.
   * the current list of methods is not changed.
   */
  private static void clearGraph() {
    CallGraph.callGraph = new BaseGraph<>();
    CallGraph.graphComponent = null;
    CallGraph.numNodes = 0;
    CallGraph.numEdges = 0;
    CallGraph.threadSel = 0;

    if (CallGraph.graphPanel != null) {
      CallGraph.graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }

  public static int getMethodCount() {
    return graphMethList.size();
  }

  public static MethodInfo getLastMethod(int tid) {
    Stack<Integer> stack = getStack(tid);
    if (graphMethList == null || graphMethList.size() < 1 || stack == null || stack.empty()) {
      return null;
    }

    // this should not happen, but let's be safe
    if (stack.peek() >= CallGraph.graphMethList.size()) {
      return null;
    }
    
    return CallGraph.graphMethList.get(stack.peek());
  }
  
  public static void setThreadSelection(int select) {
    CallGraph.threadSel = select;
  }
  
  public static void setRangeStepSize(long step) {
    CallGraph.rangeStepsize = step;
  }
  
  /**
   * generates the call graph for the selected thread.
   * 
   * @param tid
   * @return true if graph was updated
   */  
  public static boolean generateCallGraph(int tid) {
    clearGraph();

    // create a new method list if we are only doiung a single thread
    if (tid >= 0) {
      CallGraph.threadMethList = new ArrayList<>();
      for(MethodInfo mthNode : CallGraph.graphMethList) {
        ArrayList<Integer> threadList = mthNode.getThread();
        if (threadList.contains(tid)) {
          CallGraph.threadMethList.add(mthNode);
        }
      }
      drawCG(CallGraph.threadMethList);
    } else {
      drawCG(CallGraph.graphMethList);
    }

    updateCallGraph(CallGraph.curGraphMode, true);

    CallGraph.graphShowAllThreads = tid < 0;
    return true;
  }
  
  /**
   * updates the call graph display
   * 
   * @param gmode
   * @param force
   * @return true if graph was updated
   */  
  public static boolean updateCallGraph(GuiPanel.GraphHighlight gmode, boolean force) {
    boolean updated = false;

    // exit if the graphics panel has not been established
    if (CallGraph.graphPanel == null) {
      return false;
    }
    
    // only run if a node or edge has been added to the graph or a color mode has changed
    if (CallGraph.callGraph.getEdgeCount() != CallGraph.numEdges ||
        CallGraph.callGraph.getVertexCount() != CallGraph.numNodes ||
        CallGraph.curGraphMode != gmode || force) {

      // update the state
      CallGraph.numEdges = CallGraph.callGraph.getEdgeCount();
      CallGraph.numNodes = CallGraph.callGraph.getVertexCount();
      CallGraph.curGraphMode = gmode;

      // if no graph has been composed yet, set it up now
      mxGraph graph = CallGraph.callGraph.getGraph();
      if (graphComponent == null) {
        graphComponent = new mxGraphComponent(graph);
        graphComponent.setConnectable(false);
        
        // add listener to show details of selected element
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
          @Override
          public void mouseReleased(MouseEvent e) {
            displaySelectedMethodInfo(e.getX(), e.getY());
          }
        });
        CallGraph.graphPanel.add(graphComponent);
      }

      int tid = ALL_THREADS;
      if (graphShowAllThreads == false) {
        tid = CallGraph.threadSel;
        if (gmode == GuiPanel.GraphHighlight.THREAD) {
          gmode = GuiPanel.GraphHighlight.NONE;
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
      CallGraph.callGraph.layoutGraph();
      updated = true;
    }

    return updated;
  }

  public static void displaySelectedMethodInfo(int x, int y) {
    int tid = ALL_THREADS;
    if (graphShowAllThreads == false) {
      tid = CallGraph.threadSel;
    }
      
    mxGraphHandler handler = graphComponent.getGraphHandler();
    mxCell cell = (mxCell) handler.getGraphComponent().getCellAt(x, y);
    if (cell != null && cell.isVertex()) {
      MethodInfo selected = CallGraph.callGraph.getSelectedNode();
      String parentList = "";
      if (selected.getParents(tid).isEmpty()) {
        parentList = "<none>";
      } else if(selected.getParents(tid).size() == 1) {
        parentList += selected.getParents(tid).get(0);
        int offset = parentList.indexOf('(');
        if (offset > 0) {
          parentList = parentList.substring(0, offset);
        }
      } else {
        for(String name : selected.getParents(tid)) {
          if (name != null && !name.isEmpty() && !name.equals("null")) {
            int offset = name.indexOf('(');
            if (name.equals(selected.getFullName())) {
              name = "<self>";
            } else if (offset > 0) {
              name = name.substring(0, offset);
            }
            parentList += NEWLINE + "   " + name;
          }
        }
      }

      JOptionPane.showMessageDialog (null,
          "Thread: " + ((selected.getThread().isEmpty()) ?
              "<no info>" : selected.getThread().toString()) + NEWLINE +
          "Method: " + selected.getFullName() + NEWLINE +
          "Calling Methods: " + parentList + NEWLINE +
          "Execution Time: " + (selected.getDuration(tid) < 0 ?
              "(never returned)" : selected.getDuration(tid) + " msec") + NEWLINE +
          "Instruction Count: " + (selected.getInstructionCount(tid) < 0 ?
              "(no info)" : selected.getInstructionCount(tid)) + NEWLINE +
          "Iterations: " + selected.getCount(tid) + NEWLINE +
          "1st called @ line: " + selected.getFirstLine(tid) + NEWLINE +
          (selected.getExecption(tid) <= 1 ?
              "" : "exception @ line: " + selected.getExecption(tid) + NEWLINE) +
          (selected.getError(tid) <= 1 ?
              "" : "error @ line: " + selected.getError(tid) + NEWLINE),
          "Method Info",
          JOptionPane.INFORMATION_MESSAGE);
    }
  }
  
  public static void saveAsImageFile(File file) {
    BufferedImage bi = new BufferedImage(CallGraph.graphPanel.getSize().width,
      CallGraph.graphPanel.getSize().height, BufferedImage.TYPE_INT_ARGB); 
    Graphics graphics = bi.createGraphics();
//    Graphics graphics = graphPanel.getGraphics();
    CallGraph.graphPanel.paint(graphics);
    graphics.dispose();
    try {
      ImageIO.write(bi,"png",file);
    } catch (Exception ex) {
      System.err.println(ex.getMessage());
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
   */  
  public static void saveAsJSONFile(File file, boolean allThreads) {
    // open the file to write to
    BufferedWriter bw;
    try {
      bw = new BufferedWriter(new FileWriter(file));
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
      return;
    }

    // select whether we are saving the complete list or just one thread
    List<MethodInfo> methlist = CallGraph.graphMethList;
    if (!allThreads && !CallGraph.threadMethList.isEmpty()) {
      methlist = CallGraph.threadMethList;
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
      System.err.println(ex.getMessage());
    }
  }
  
  /**
   * reads method info from specified file and saves in CallGraph.graphMethList
   * 
   * @param file - name of file to load data from
   */  
  public static int loadFromJSONFile(File file) {
    // open the file to read from
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
    } catch (FileNotFoundException ex) {
      System.err.println(ex.getMessage());
      return 0;
    }
    
    // load the method list info from json file
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
    Type methodListType = new TypeToken<List<MethodInfo>>() {}.getType();
    CallGraph.graphMethList = gson.fromJson(br, methodListType);
    System.out.println("loaded: " + CallGraph.graphMethList.size() + " methods");

    // draw the new graph (always do the full list)
    clearGraph();
    drawCG(CallGraph.graphMethList);
    
    // count the number of threads
    ArrayList<Integer> list = new ArrayList<>();
    for(MethodInfo mthNode : CallGraph.graphMethList) {
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
  public static void methodEnter(int tid, long tstamp, String icount, String method, int line) {
    if (method == null || method.isEmpty() || icount.isEmpty() || CallGraph.graphMethList == null) {
      return;
    }

    // since we're going to add the method to the stack, we first check if there is a stack
    // created for the current thread. If not, we need to create an empty one now.
    Stack<Integer> stack = getStack(tid);

    // get the parent from the stack
    String parent = ""; // if there was no caller on this thread, leave as parentless
    if (!stack.empty()) {
      int ix = stack.peek();
      if (ix >= 0 && ix < CallGraph.graphMethList.size()) {
        MethodInfo mthNode = CallGraph.graphMethList.get(ix);
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
      parNode = findMethodEntry(ALL_THREADS, parent, CallGraph.graphMethList);
//      if (parNode == null && method.contains("<clinit>")) {
//        parNode = new MethodInfo(tid, method, parent, tstamp, insCount, line);
//        CallGraph.graphMethList.add(parNode);
//        CallGraph.callGraph.addVertex(parNode, parNode.getCGName());
//      }
    }

    // find called method in list and create (or update) info
    MethodInfo mthNode = null;
    boolean newnode = true;
    int count = CallGraph.graphMethList.size();
    for (int ix = 0; ix < count; ix++) {
      mthNode = CallGraph.graphMethList.get(ix);
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
      CallGraph.graphMethList.add(mthNode);

      // if thread id matches the current selection, add this entry to the current single thread method list
      if (tid == CallGraph.threadSel) {
        CallGraph.threadMethList.add(mthNode);
      }
      
      // update saved stack for this thread
      stack.push(count);
    }

    // add node (if not previously defined) and/or edge (if parent defined) to graph
    if (graphShowAllThreads == true || threadSel == tid) {
      if (newnode) {
        CallGraph.callGraph.addVertex(mthNode, mthNode.getCGName());
      }
      if (parNode != null && CallGraph.callGraph.getEdge(parNode, mthNode) == null) {
        CallGraph.callGraph.addEdge(parNode, mthNode, null);
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
  public static void methodExit(int tid, long tstamp, String icount) {
    if (CallGraph.graphMethList == null) {
      //System.out.println("Return: " + method + " - NOT FOUND!");
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
      if (ix >= 0 && ix < CallGraph.graphMethList.size()) {
        MethodInfo mthNode = CallGraph.graphMethList.get(ix);
        mthNode.exit(tid, tstamp, insCount);
        //System.out.println("Return: (" + mthNode.getDuration() + ") " + mthNode.getClassAndMethod());
      }
    }
  }
  
}
