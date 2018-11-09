/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import callgraph.BaseGraph;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.swing.mxGraphComponent;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JPanel;
import main.LauncherMain;

/**
 *
 * @author dan
 */
public class BytecodeGraph {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static JPanel graphPanel = null;
  private static mxGraphComponent graphComponent = null;
  private static BaseGraph<FlowInfo> flowGraph = new BaseGraph<>();
  private static BytecodeViewer bcViewer;
  
  public BytecodeGraph(JPanel panel, BytecodeViewer viewer) {
    graphPanel = panel;
    bcViewer = viewer;
  }
  
  public void drawGraph() {
    System.out.println("drawGraph: Bytecode entries = " + BytecodeViewer.bytecode.size());
    
    // clear the current graph
    clearGraph();
    
    // create an array of FlowInfo entities that correspond to blocks to display
    ArrayList<FlowInfo> flowBlocks = new ArrayList<>();
    HashMap<Integer, FlowInfo> branchMap = new HashMap<>();

    // start with an initial ENTRY block and add EXCEPTION entry points (if any)
    FlowInfo lastBlock = new FlowInfo();
    flowBlocks.add(lastBlock);
    for (HashMap.Entry pair : BytecodeViewer.exceptions.entrySet()) {
      flowBlocks.add(new FlowInfo((String) pair.getValue(), (Integer) pair.getKey()));
    }

    // now add the opcode blocks
    int line = 0;
    for(BytecodeViewer.BytecodeInfo bc : BytecodeViewer.bytecode) {
      FlowInfo entry = new FlowInfo(line, bc);
      flowBlocks.add(entry);
      ++line;
    }
    
    // find all branch points that we need to keep
    ArrayList<Integer> branchpoints = new ArrayList<>();
    for(FlowInfo flow : flowBlocks) {
      int branchloc;
      switch (flow.type) {
        case Branch:
        case SymBranch:
          branchloc = flow.nextloc.get(0);
          if (!branchpoints.contains(branchloc)) {
            branchpoints.add(branchloc);
          }
          break;
        case Switch:
          for (int ix = 0; ix < flow.nextloc.size(); ix++) {
            branchloc = flow.nextloc.get(ix);
            if (!branchpoints.contains(branchloc)) {
              branchpoints.add(branchloc);
            }
          }
          break;
        default:
          break;
      }
    }

    // make a copy of the list so we can do some node trimming
    // Here we eliminate all BLOCKs whose predecessor was also a BLOCK if it is not a branchpoint.
    ArrayList<FlowInfo> essentialList = new ArrayList<>();
    FlowInfo last = null;
    for(FlowInfo flow : flowBlocks) {
      if (last == null || last.type != FlowType.Block || flow.type != FlowType.Block ||
                          branchpoints.contains(flow.offset)) {
        essentialList.add(flow);
        branchMap.put(flow.offset, flow); // map the line number to the flow entry
        last = flow;
      }
    }

    // now create graph nodes for all blocks
    for(FlowInfo flow : essentialList) {
      if (!flow.opcode.equals("GOTO")) {
        flowGraph.addVertex(flow, flow.title);
        if (!flow.color.isEmpty()) {
          flowGraph.colorVertex(flow, flow.color);
        }
      }
    }
    
    // now connect the blocks
    int nextloc = 0;
    for (int index = 0; index < essentialList.size(); index++) {
      FlowInfo flow = essentialList.get(index);
      FlowInfo nextflow;
      
      if (flow.type != FlowType.Return) { // skip any RETURN type block
        // this section handles connecting a block to the next opcode following it.
        // This is the normal process for opcodes other than GOTO and SWITCH (and the last
        // opcode in the method, which should be either a RETURN or GOTO).
        // The conditional BRANCH instructions also need to connect to the next block for the
        // case in which the branch is not taken. We must also add in the branch case for these
        // instructions, as well as the case for the SWITCH and GOTOs, which are handled in
        // the next section.
        if (index < essentialList.size() - 1) {
          // first, let's find the next valid opcode block
          nextflow = essentialList.get(index + 1);
          while (nextflow != null && nextflow.opcode.equals("GOTO")) {
            nextloc = nextflow.nextloc.get(0);
            nextflow = branchMap.get(nextloc);
          }
          if (nextflow == null) {
            System.out.println("Failed Connection: " + flow.offset + "_" + flow.opcode + " to offset " + nextloc);
            continue;
          }
          
          // all other opcodes except for SWITCH and GOTO will always have a connection to
          // the next opcode (this will be for one case of the branch conditions).
          if (!flow.opcode.isEmpty() && !flow.opcode.equals("GOTO") && flow.type != FlowType.Switch) {
            flowGraph.addEdge(flow, nextflow, null);
          }
        }

        // go through all connections
        ArrayList<Integer> connected = new ArrayList<>();
        for (int ix = 0; ix < flow.nextloc.size(); ix++) {
          nextloc = flow.nextloc.get(ix);
          nextflow = branchMap.get(nextloc);
          while (nextflow != null && nextflow.opcode.equals("GOTO")) {
            nextflow = branchMap.get(nextflow.nextloc.get(0));
          }
          if (nextflow == null) {
            System.out.println("Failed Connection: " + flow.offset + "_" + flow.opcode + " to offset " + nextloc);
            continue;
          }
          // skip if this block has already been connected to the branch location
          // (this can happen on switch statement that has multiple conditions that branch to same location)
          if (!connected.contains(nextloc)) {
            flowGraph.addEdge(flow, nextflow, null);
            connected.add(nextloc);
          }
        }
      }
    }
    
    updateClass();
  }
  
  private class ByteFlowMouseListener extends MouseAdapter {

    @Override
    public void mouseReleased(MouseEvent e) {
      int x = e.getX();
      int y = e.getY();
      mxGraphHandler handler = graphComponent.getGraphHandler();
      mxCell cell = (mxCell) handler.getGraphComponent().getCellAt(x, y);
      if (cell != null && cell.isVertex()) {
        FlowInfo selected = flowGraph.getSelectedNode();
        if (selected.type == FlowType.Call) {
          String meth = selected.bcode.callMeth;
          int offset = meth.lastIndexOf(".");
          String cls = meth.substring(0, offset);
          meth = meth.substring(offset + 1);
                
          // skip if the method is the one that's currently loaded
          if (!bcViewer.isMethodDisplayed(cls, meth)) {
            LauncherMain.runBytecodeViewer(cls, meth);
            LauncherMain.setBytecodeSelections(cls, meth);
          }
        }
      }
    }
  }

  private void clearGraph() {
    // clear the current graph
    graphPanel.removeAll();
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
    graphComponent = null;
    flowGraph = new BaseGraph<>();
  }

  private void updateClass() {
    graphComponent = new mxGraphComponent(flowGraph.getGraph());
    graphComponent.setConnectable(false);
    graphComponent.getGraphControl().addMouseListener(new ByteFlowMouseListener());
    graphPanel.add(graphComponent);

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    // update the graph layout
    flowGraph.layoutGraph();
  }

  private static enum FlowType { Entry, Exception, Call, SymBranch, Switch, Branch, Return, Block }
  
  private class FlowInfo {
    public FlowType type;       // type of block
    public int      line;       // bytecode line number corresponding to entry
    public int      offset;     // byte offset of opcode (-1 if not a bytecode entry)
    public String   opcode;     // opcode
    public String   color;      // color to display
    public String   title;      // title for the block
    public BytecodeViewer.BytecodeInfo bcode;  // the Bytecode info
    ArrayList<Integer> nextloc = new ArrayList<>(); // list of offsets to branch to
    
    public FlowInfo(int count, BytecodeViewer.BytecodeInfo bc) {
      bcode  = bc;
      opcode = bc.opcode.toUpperCase();
      offset = bc.offset;
      line = count;

      int branchix;
      switch (bc.optype) {
        case INVOKE:
          // extract just the class and method names from the full method name
          String methname = bc.callMeth.substring(0, bc.callMeth.indexOf("("));
          int stroff = methname.lastIndexOf("/");
          if (stroff > 0) {
            methname = methname.substring(stroff + 1);
          }
          stroff = methname.indexOf(".");
          String clsname = methname.substring(0, stroff);
          methname = methname.substring(stroff + 1);

          type = FlowType.Call;
          color = "DDAA00"; // gold
          title = clsname + NEWLINE + methname;
          break;

        case SYMBRA:
          branchix = Integer.parseUnsignedInt(bc.param); // let's assume it is a numeric

          type = FlowType.SymBranch;
          color = "CC00EE"; // violet
          title = bc.offset + NEWLINE + bc.opcode.toUpperCase();
          nextloc.add(branchix);
          break;

        case BRANCH:
        case GOTO:
          branchix = Integer.parseUnsignedInt(bc.param); // let's assume it is a numeric

          type = FlowType.Branch;
          color = "";
          title = bc.offset + NEWLINE + bc.opcode.toUpperCase();
          nextloc.add(branchix);
          break;

        case SWITCH:
          type = FlowType.Switch;
          color = "0077FF"; // blue
          title = bc.offset + NEWLINE + bc.opcode.toUpperCase();
          for (HashMap.Entry pair : bc.switchinfo.entrySet()) {
            nextloc.add((Integer) pair.getValue());
          }
          break;
          
        case RETURN:
          type = FlowType.Return;
          color = "";
          title = bc.offset + NEWLINE + bc.opcode.toUpperCase();
          // there is no branch from here
          break;

        case OTHER:
          type = FlowType.Block;
          color = "";
          title = bc.offset + NEWLINE + "BLOCK";
          break;

        default:
          // indicate error
          System.err.println("Unhandled OpcodeType: " + bc.optype.toString());
          break;
      }
    }

    // this one for defining entry point
    public FlowInfo() {
      type = FlowType.Entry;
      opcode = "";
      offset = -1; // this indicates it is not an opcode
      title = "ENTRY";
      color = "FFCCCC"; // pink
      nextloc.add(0);
    }

    // this one for defining exceptions
    public FlowInfo(String extype, int branchoff) {
      type = FlowType.Exception;
      opcode = "";
      offset = -1; // this indicates it is not an opcode
      title = "EXCEPTION" + NEWLINE + extype;
      color = "FFCCCC"; // pink
      nextloc.add(branchoff);
    }
    
  }
  
}
