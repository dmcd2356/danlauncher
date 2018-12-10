/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import main.LauncherMain;
import gui.GuiControls;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/**
 *
 * @author dan
 */
public class ImportGraph {
    
  private static String  tabName;
  private static JPanel  graphPanel;
  private static JScrollPane scrollPanel;
  private static boolean     tabSelected;
  private static GuiControls gui;
  private static mxGraphComponent graphComponent;
  private static BaseGraph<ImportMethod> callGraph = new BaseGraph<>();
  private static ArrayList<ImportMethod> graphMethList = new ArrayList<>();

  public ImportGraph(String name) {
    tabName = name;
    graphPanel = new JPanel();
    gui = new GuiControls();
    scrollPanel = gui.makeRawScrollPanel(name, graphPanel);
    
    callGraph = null;
    graphComponent = null;
    graphMethList = new ArrayList<>();
    tabSelected = false;
  }
  
  public class ImportMethod {
    public String  fullName;       // full name of method (package, class, method + signature)
    public ArrayList<String> parent; // list of caller methods (full names)
    
    public ImportMethod(String fullname) {
      this.fullName = fullname;
      this.parent = new ArrayList<>();
    }
  
  }
    
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public void clearGraph() {
    callGraph = null;
    graphComponent = null;

    if (graphPanel != null) {
      graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }

  public void setZoomFactor(double scaleFactor) {                                             
    if (callGraph != null) {
      callGraph.scaleVerticies(scaleFactor);
//      graphPanel.update(graphPanel.getGraphics());   
    }
  }
  
  public void clearPath() {
    // remove the color tracing of the traveled path from the graph
    if (callGraph != null && !graphMethList.isEmpty()) {
      for (int ix = 0; ix < graphMethList.size(); ix++) {
        ImportMethod node = graphMethList.get(ix);
        callGraph.colorVertex(node, "D2E9FF"); // set color to default
      }
    }
  }
  
  public boolean addPathEntry(String methcall) {
    // add color to specified method block
    if (callGraph != null && !graphMethList.isEmpty()) {
      // Janalyzer keeps the "L" entry in the method name, but the received method name does not
      ImportMethod node = findMethodEntry("L" + methcall);
      if (node != null) {
        callGraph.colorVertex(node, "6666FF"); // set color to blue
        return true;
      }

      // ignore the <clinit>methods - they are not included in the methods provided by Janalyzer.
      // Also some <init> methods are excluded if they are only initializing parameters.
      if (!methcall.contains("<clinit>") && !methcall.contains("<init>")) {
        System.err.println("Method not found: " + methcall);
      }
    }
    return false;
  }
  
  public void updateCallGraph() {
    // exit if the graphics panel has not been established
    if (graphPanel == null || callGraph == null) {
      return;
    }
    
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
            // show selected method in bytecode viewer
            // Janalyzer keeps the "L" entry in the method name, but the Bytecode viewer name does not
            ImportMethod selected = callGraph.getSelectedNode();
            String methname = selected.fullName;
            if (methname.startsWith("L")) {
              methname = methname.substring(1);
            }
            MethodInfo methSelect = CallGraph.getMethodInfo(methname);
            if (methSelect != null) {
              displayMethodInfoPanel(methSelect);
            } else {
              String cls = methname;
              int offset = cls.lastIndexOf(".");
              String meth = cls.substring(offset + 1);
              cls = cls.substring(0, offset);
              LauncherMain.runBytecodeViewer(cls, meth);
            }
          }
        }
      });
      graphPanel.add(graphComponent);
    }

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    // update the graph layout
    callGraph.layoutGraph();
  }

  private static GuiControls methInfoPanel;
  
  private void displayMethodInfoPanel(MethodInfo selected) {
    // if panel is currently displayed for another method, close that one before opening the new one
    if (methInfoPanel != null) {
      methInfoPanel.close();
    }
    
    // create the panel to display the selected method info on
    methInfoPanel = new GuiControls();
    JFrame frame = methInfoPanel.newFrame("Executed Method Information", 600, 400, GuiControls.FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_ExitListener());

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    
    String panel = null;
    methInfoPanel.makePanel (panel, "PNL_INFO", LEFT, true , "", 600, 400);
    
    panel = "PNL_INFO";
    JTextPane textPanel = methInfoPanel.makeScrollTextPane(panel, "TXT_METHINFO");
    JButton bcodeButton = methInfoPanel.makeButton(panel, "BTN_BYTECODE", CENTER, true, "Show bytecode");
    
    bcodeButton.addActionListener(new Action_RunBytecode());
    
    // setup initial method info text
    String message = CallGraph.getSelectedMethodInfo(selected, -1);
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
      // show selected method in bytecode viewer
      // Janalyzer keeps the "L" entry in the method name, but the Bytecode viewer name does not
      ImportMethod selected = callGraph.getSelectedNode();
      String methname = selected.fullName;
      if (methname.startsWith("L")) {
        methname = methname.substring(1);
      }

      String cls = methname;
      int offset = cls.lastIndexOf(".");
      String meth = cls.substring(offset + 1);
      cls = cls.substring(0, offset);

      LauncherMain.runBytecodeViewer(cls, meth);
      
      // close this menu
      methInfoPanel.close();
    }
  }
  
  private static String getCGName(String fullname) {
    String  className = "";
    String  methName = fullname.replace("/", ".");
    if (methName.contains("(")) {
      methName = methName.substring(0, methName.lastIndexOf("("));
    }
    int offset = methName.lastIndexOf(".");
    if (offset > 0) {
      className = methName.substring(0, offset);
      methName = methName.substring(offset + 1);
      offset = className.lastIndexOf(".");
      if (offset > 0) {
        className = className.substring(offset + 1);
      }
    }
    return className.isEmpty() ? methName : className + Utils.NEWLINE + methName;
  }

  /**
   * reads method info from specified file and saves in graphMethList
   * 
   * @param file - name of file to load data from
   * @return number of methods found
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
    Type methodListType = new TypeToken<List<ImportMethod>>() {}.getType();
    graphMethList = gson.fromJson(br, methodListType);
    LauncherMain.printCommandMessage("loaded: " + graphMethList.size() + " methods");

    // draw the new graph on seperate pane
    clearGraph();
    drawCallGraph(graphMethList);
      
    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    return graphMethList.size();
  }
  
  private static ImportMethod findMethodEntry(String method) {
    for (int ix = 0; ix < graphMethList.size(); ix++) {
      ImportMethod entry = graphMethList.get(ix);
      if (entry.fullName.equals(method)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * draws the graph as defined by the list passed.
   * 
   * @return the number of threads found
   */  
  private void drawCallGraph(List<ImportMethod> methList) {
    callGraph = new BaseGraph<>();

    LauncherMain.printCommandMessage("drawCallGraph: Methods = " + methList.size());

    // add vertexes to graph
    for(ImportMethod mthNode : methList) {
      if (mthNode != null) {
        callGraph.addVertex(mthNode, getCGName(mthNode.fullName));
      }
    }
    
    // now connect the methods to their parents
    for (ImportMethod mthNode : methList) {
      if (mthNode == null) {
        break;
      }
      // for each parent entry for a method...
      for (String parent : mthNode.parent) {
        if (parent != null && !parent.isEmpty()) {
          ImportMethod parNode = findMethodEntry(parent);
          // only add connection if parent was found and there isn't already a connection
          if (parNode != null && callGraph.getEdge(parNode, mthNode) == null) {
            // now add the connection from the method to the parent
            callGraph.addEdge(parNode, mthNode, null);
          }
        }
      }
    }
  }
  
}
