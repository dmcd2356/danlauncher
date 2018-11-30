/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import static gui.GuiControls.Orient.LEFT;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import main.LauncherMain;
import util.Utils;

/**
 *
 * @author dan
 */
/**
 *
 * @author dan
 */
public class SymbolTable {

  private static final String[] TABLE_COLUMNS = new String [] {
    "Method", "Slot", "Start", "End", "Name", "Type"
  };

  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;
  private static JTable   table;
  private static ArrayList<TableListInfo> paramList = new ArrayList<>();
  private static ArrayList<String> paramNameList = new ArrayList<>();
  private static GuiControls optionsPanel = new GuiControls();

  
  public static class ConstraintInfo {
    public String comptype;  // comparison type { EQ, NE, GT, GE, LT, LE }
    public String compvalue; // the comparison value
    
    public ConstraintInfo(String type, String value) {
      comptype = type;
      compvalue = value;
    }
  }
  
  public static class TableListInfo {
    public String  method;   // the name of the method that the local parameter belongs to
    public String  name;     // the moniker to call the parameter by (unique entry)
    public String  type;     // data type of the parameter
    public String  slot;     // slot within the method for the parameter
    public String  start;    // byte offset in method that specifies the starting range of the parameter
    public String  end;      // byte offset in method that specifies the ending   range of the parameter
    
    // entries that are not placed in the table
    public int     opStart;  // starting opcode entry in method (cause danalyzer can't determine byte offset)
    public int     opEnd;    // ending   opcode entry in method (cause danalyzer can't determine byte offset)
    public ArrayList<ConstraintInfo> constraints; // the user-defined constraint values for the symbolic entry
    
    public TableListInfo(String meth, String id, String typ, String slt, String strt, String last,
                         int opstrt, int oplast) {
      method = meth == null ? "" : meth;
      name   = id   == null ? "" : id;
      type   = typ  == null ? "" : typ;
      slot   = slt  == null ? "" : slt;
      start  = strt == null ? "" : strt;
      end    = last == null ? "" : last;
      
      opStart = opstrt;
      opEnd = oplast;
      constraints = new ArrayList<>();
    }
    
    public TableListInfo(String meth, String id, String typ, String slt, int opstrt, int oplast) {
      method = meth == null ? "" : meth;
      name   = id   == null ? "" : id;
      type   = typ  == null ? "" : typ;
      slot   = slt  == null ? "" : slt;
      start  = opstrt == 0 ? "0" : ""; // can't convert to byte offsets except for a value of 0
      end    = oplast == 0 ? "0" : "";
      
      opStart = opstrt;
      opEnd = oplast;
      constraints = new ArrayList<>();
    }
  } 
  
  public SymbolTable (JTable tbl) {
    table = tbl;
    
    // init the params
    bSortOrder = false;
    rowSelection = -1;
    colSortSelection = 0;
    
    table.setModel(new DefaultTableModel(new Object [][]{ }, TABLE_COLUMNS) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
      };
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false, false
      };

      @Override
      public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });

    table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        tableMouseClicked(evt);
      }
    });
//    table.addKeyListener(new java.awt.event.KeyAdapter() {
//      @Override
//      public void keyPressed(java.awt.event.KeyEvent evt) {
//        tableKeyPressed(evt);
//      }
//    });
        
    // align columns in database table to center
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment( SwingConstants.CENTER );
    table.setDefaultRenderer(String.class, centerRenderer);
    TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
    JLabel headerLabel = (JLabel) headerRenderer;
    headerLabel.setHorizontalAlignment( SwingConstants.CENTER );
        
    // create up & down key handlers for cloud row selection
    AbstractAction tableUpArrowAction = new UpArrowAction();
    AbstractAction tableDnArrowAction = new DnArrowAction();
    table.getInputMap().put(KeyStroke.getKeyStroke("UP"), "upAction");
    table.getActionMap().put( "upAction", tableUpArrowAction );
    table.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "dnAction");
    table.getActionMap().put( "dnAction", tableDnArrowAction );
        
    // add a mouse listener for the cloud table header selection
    JTableHeader cloudTableHeader = table.getTableHeader();
    cloudTableHeader.addMouseListener(new HeaderMouseListener());
  }
    
  public void clear() {
    paramList.clear();
    paramNameList.clear();
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    model.setRowCount(0); // this clears all the entries from the table
  }
  
  public int getSize() {
    return paramList.size();
  }
  
  public void exit() {
  }

  public String addEntry(String meth, String name, String type, String slot, String start, String end,
                       int opstrt, int oplast) {
    // if no name given, pick a default one
    name = getUniqueName(name);
    TableListInfo entry = new TableListInfo(meth, name, type, slot, start, end, opstrt, oplast);
    if (isMatch(entry)) {
      return null;
    }
    paramList.add(entry);
    paramNameList.add(name);
    tableSortAndDisplay();
    return name;
  }
  
  // this adds the entry using only opcode line numbers for the start and end range,
  // because the byte offset values are not known at the time (reading from danfig).
  public String addEntryByLine(String meth, String name, String type, String slot, int start, int end) {
    // if no name given, pick a default one
    name = getUniqueName(name);
    TableListInfo entry = new TableListInfo(meth, name, type, slot, start, end);
    if (isMatch(entry)) {
      return null;
    }
    paramList.add(entry);
    paramNameList.add(name);
    tableSortAndDisplay();
    return name;
  }
  
  public void addConstraint(String id, String type, String value) {
    for (TableListInfo entry : paramList) {
      if (entry.name.equals(id)) {
        entry.constraints.add(new ConstraintInfo(type, value));
        return;
      }
    }
  }
  
  public TableListInfo getSymbolicEntry(int ix) {
    if (ix < paramList.size()) {
      return paramList.get(ix);
    }
    return null;
  }
  
  private boolean isMatch(TableListInfo entry) {
    String entryMethod = entry.method.replaceAll("/", ".");
    for (TableListInfo tblval : paramList) {
      String tblMethod = tblval.method.replaceAll("/", ".");
      if (tblMethod.equals(entryMethod) && tblval.slot.equals(entry.slot) &&
          tblval.opStart == entry.opStart && tblval.opEnd == entry.opEnd) {
        return true;
      }
    }
    return false;
  }
  
  private String getUniqueName(String name) {
    // if no name given, pick a default one
    if (name == null || name.isEmpty()) {
      name = "P_0";
    }
    
    // name must be unique. if not, make it so
    if (paramNameList.contains(name)) {
      if (name.endsWith("_0")) {
        name = name.substring(0, name.lastIndexOf("_0"));
      }
      String newname = name;
      for (int index = 0; paramNameList.contains(newname); index++) {
        newname = name + "_" + index;
      }
      name = newname;
    }

    return name;
  }
  
  private String getColumnName(int col) {
    if (col < 0 || col >= TABLE_COLUMNS.length) {
      col = 0;
    }
    return TABLE_COLUMNS[col];
  }
  
  private String getColumnParam(int col, TableListInfo tblinfo) {
    switch(getColumnName(col)) {
      default: // fall through...
      case "Method":
        return tblinfo.method;
      case "Name":
        return tblinfo.name;
      case "Type":
        return tblinfo.type;
      case "Slot":
        return tblinfo.slot;
      case "Start":
        return tblinfo.start;
      case "End":
        return tblinfo.end;
    }
  }
  
  // NOTE: the order of the entries should match the order of the columns
  private Object[] makeRow(TableListInfo tableEntry) {
    return new Object[]{
        tableEntry.method,
        tableEntry.slot,
        tableEntry.start,
        tableEntry.end,
        tableEntry.name,
        tableEntry.type,
    };
  }
  
  private static int getColumnIndex(String colname) {
    for (int col = 0; col < TABLE_COLUMNS.length; col++) {
      String entry = TABLE_COLUMNS[col];
      if(entry != null && entry.equals(colname)) {
        return col;
      }
    }
    return 0;
  }
  
  /**
   * This is used to resize the colums of a table to accomodate some columns
   * requiring more width than others.
   * 
   * @param table - the table to resize
   */
  private void resizeColumnWidth (JTable table) {
    final TableColumnModel columnModel = table.getColumnModel();
    for (int column = 0; column < table.getColumnCount(); column++) {
      int width = 15; // Min width
      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        width = Math.max(comp.getPreferredSize().width +1 , width);
      }
      if(width > 300) // Max width
        width=300;
      columnModel.getColumn(column).setPreferredWidth(width);
    }
  }

  /**
   * This performs a sort on the selected table column and updates the table display.
   * The sort based on the current criteria of:
   * 'colSortSelection' which specifies the column on which to sort and
   * 'bSortOrder' which specifies either ascending (false) or descending (true) order.
   */
  private void tableSortAndDisplay () {
    // sort the table entries
    Collections.sort(paramList, new Comparator<TableListInfo>() {
      @Override
      public int compare(TableListInfo job1, TableListInfo job2) {
        String object1 = getColumnParam(colSortSelection, job1);
        String object2 = getColumnParam(colSortSelection, job2);
        if (!bSortOrder)
          return  object1.compareTo(object2);
        else
          return  object2.compareTo(object1);
      }
    });

    // clear out the table entries
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    model.setRowCount(0); // this clears all the entries from the table

    // reset the names of the columns and modify the currently selected one
    String[] columnNames = new String[TABLE_COLUMNS.length];
    System.arraycopy(TABLE_COLUMNS, 0, columnNames, 0, TABLE_COLUMNS.length);
    for (int ix = 0; ix < model.getColumnCount(); ix++) {
      if (ix == colSortSelection) {
        if (bSortOrder) {
          columnNames[ix] += " " + "\u2193".toCharArray()[0]; // DOWN arrow
        }
        else {
          columnNames[ix] += " " + "\u2191".toCharArray()[0]; // UP arrow
        }
      }
    }
    model.setColumnIdentifiers(columnNames);
        
    // now copy the entries to the displayed table
    for (int ix = 0; ix < paramList.size(); ix++) {
      TableListInfo tableEntry = paramList.get(ix);
      model.addRow(makeRow(tableEntry));
    }

    // auto-resize column width
    resizeColumnWidth(table);
  }
    
  /**
   * action event when the mouse is clicked in the table.
   */
  private void tableMouseClicked(java.awt.event.MouseEvent evt) {                                            
    int row = table.rowAtPoint(evt.getPoint());
    int col = table.columnAtPoint(evt.getPoint());
    String colname = getColumnName(col);

    rowSelection = row;
    showMenuSelection();
  }                                           

  private void showMenuSelection() {
    JFrame frame = optionsPanel.newFrame("Symbolic Parameter Modification", 370, 420,
        GuiControls.FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_ExitListener());

    TableListInfo pinfo = paramList.get(rowSelection);
    String method = pinfo.method;
    String clz = "";
    String sig = "";
    int offset = method.indexOf("(");
    if (offset > 0) {
      sig = method.substring(offset);
      method = method.substring(0, offset);
    }
    offset = method.lastIndexOf("/");
    if (offset > 0) {
      clz = method.substring(0, offset);
      method = method.substring(offset + 1);
    }
    method = method + sig;
    
    String panel = null;
    optionsPanel.makePanel (panel, "PNL_INFO"       , "", LEFT, true);
    optionsPanel.makePanel (panel, "PNL_EDIT_SYMB"  , "Edit Symbolic"   , LEFT, true);
    optionsPanel.makePanel (panel, "PNL_REMOVE_SYMB", "Remove Symbolic" , LEFT, true);
    optionsPanel.makePanel (panel, "PNL_CONSTRAINTS", "Setup Constraint", LEFT, true);

    panel = "PNL_INFO";
    optionsPanel.makeLabel (panel, ""             , "Class: " + clz        , LEFT, true);
    optionsPanel.makeLabel (panel, ""             , "Method: " + method    , LEFT, true);
    optionsPanel.makeLabel (panel, ""             , "Slot: " + pinfo.slot  , LEFT, true);

    panel = "PNL_EDIT_SYMB";
    optionsPanel.makeButton(panel, "BTN_EDIT_NAME", "Name"      , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , pinfo.name  , LEFT, true);
    optionsPanel.makeButton(panel, "BTN_EDIT_TYPE", "Type"      , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , pinfo.type  , LEFT, true);
    optionsPanel.makeButton(panel, "BTN_EDIT_STRT", "Start"     , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , pinfo.start , LEFT, true);
    optionsPanel.makeButton(panel, "BTN_EDIT_END" , "End"       , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , pinfo.end   , LEFT, true);

    panel = "PNL_REMOVE_SYMB";
    optionsPanel.makeButton(panel, "BTN_REMOVE_ONE" , "Remove"       , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , "Removes this symbolic", LEFT, true);
    optionsPanel.makeButton(panel, "BTN_REMOVE_ALL" , "Remove all"   , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , "Removes all symbolics", LEFT, true);

    panel = "PNL_CONSTRAINTS";
    TableListInfo tbl = paramList.get(rowSelection);
    if (tbl != null && !tbl.constraints.isEmpty()) {
      optionsPanel.makeButton(panel, "BTN_CON_SHOW" , "Show"       , LEFT, false);
      optionsPanel.makeLabel (panel, ""             , "Show added constraints for selection", LEFT, true);
      optionsPanel.makeButton(panel, "BTN_CON_REM"  , "Remove"     , LEFT, false);
      optionsPanel.makeLabel (panel, ""             , "Remove all constraints for this selection", LEFT, true);
    }
    optionsPanel.makeButton(panel, "BTN_CON_ADD"  , "Add"       , LEFT, false);
    optionsPanel.makeLabel (panel, ""             , "Add constraint to selection", LEFT, true);

    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_EDIT_SYM");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_NAME");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_TYPE");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_STRT");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_END");
    optionsPanel.setGroupSameMinSize("GRP_EDIT_SYM", GuiControls.DimType.WIDTH);

    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_REMOVE");
    optionsPanel.addGroupComponent("GRP_REMOVE", "BTN_REMOVE_ONE");
    optionsPanel.addGroupComponent("GRP_REMOVE", "BTN_REMOVE_ALL");
    optionsPanel.setGroupSameMinSize("GRP_REMOVE", GuiControls.DimType.WIDTH);
    
    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_CONSTRAINTS");
    if (tbl != null && !tbl.constraints.isEmpty()) {
      optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_SHOW");
      optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_REM");
    }
    optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_ADD");
    optionsPanel.setGroupSameMinSize("GRP_CONSTRAINTS", GuiControls.DimType.WIDTH);
    
    // add each button to the group and set the listener
    addButtonListener("BTN_EDIT_NAME" , new Action_EditName());
    addButtonListener("BTN_EDIT_TYPE" , new Action_EditType());
    addButtonListener("BTN_EDIT_STRT" , new Action_EditStart());
    addButtonListener("BTN_EDIT_END"  , new Action_EditEnd());
    addButtonListener("BTN_REMOVE_ONE", new Action_Remove());
    addButtonListener("BTN_REMOVE_ALL", new Action_RemoveAll());
    if (tbl != null && !tbl.constraints.isEmpty()) {
      addButtonListener("BTN_CON_SHOW", new Action_ConstraintShow());
      addButtonListener("BTN_CON_REM" , new Action_ConstraintRemove());
    }
    addButtonListener("BTN_CON_ADD"   , new Action_ConstraintAdd());

    optionsPanel.display();
  }

  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      optionsPanel.close();
    }
  }

  private void addButtonListener(String name, ActionListener listener) {
    JButton button = optionsPanel.getButton(name);
    button.addActionListener(listener);
  }
  
  private class Action_EditName implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      TableListInfo entry;
      String result = JOptionPane.showInputDialog(null, "Enter name to identify parameter:");
      if (result != null) {
        entry = paramList.get(row);
        if (paramNameList.contains(result)) {
          LauncherMain.printStatusError("Symbolic name is already used: " + result);
        } else if (!result.equals(entry.name)) {
          paramNameList.remove(entry.name);
          entry.name = result;
          paramNameList.add(entry.name);
          tableSortAndDisplay();
        }
      }
      optionsPanel.close();
    }
  }
  
  private class Action_EditType implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      TableListInfo entry;
      String result = JOptionPane.showInputDialog(null, "Enter parameter type:");
      if (result != null) {
        entry = paramList.get(row);
        entry.type = result;
        tableSortAndDisplay();
      }
      optionsPanel.close();
    }
  }
  
  private class Action_EditStart implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      TableListInfo entry;
      String result = JOptionPane.showInputDialog(null, "Enter start offset range in method:");
      if (result != null) {
        entry = paramList.get(row);
        try {
          int value = Integer.parseUnsignedInt(result);
        } catch (NumberFormatException ex) {
          LauncherMain.printStatusError("Invalid start offset for symbolic: " + result);
          return;
        }
        entry.start = result;
        tableSortAndDisplay();
      }
      optionsPanel.close();
    }
  }
  
  private class Action_EditEnd implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      TableListInfo entry;
      String result = JOptionPane.showInputDialog(null, "Enter end offset range in method:");
      if (result != null) {
        entry = paramList.get(row);
        try {
          int value = Integer.parseUnsignedInt(result);
        } catch (NumberFormatException ex) {
          LauncherMain.printStatusError("Invalid end offset for symbolic: " + result);
          return;
        }
        entry.end = result;
        tableSortAndDisplay();
      }
      optionsPanel.close();
    }
  }

  private class Action_Remove implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove entry from list?",
        "Remove entry", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove selected symbolic parameter
        String name = paramList.get(row).name;
        paramList.remove(row);
        paramNameList.remove(name);
      
        // update table display
        tableSortAndDisplay();
      }
    }
  }
  
  private class Action_RemoveAll implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove all symbolics from list?",
        "Remove all", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove all symbolic parameters
        paramList.clear();
        paramNameList.clear();
      
        // update table display
        tableSortAndDisplay();
      }
    }
  }
  
  private class Action_ConstraintShow implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo tbl = paramList.get(rowSelection);
      String pname = "'" + tbl.name + "'";
      if (tbl.name.isEmpty()) {
        pname = "The selected parameter";
      }

      String message = pname + " has the following uesr-defined constraints:" + Utils.NEWLINE + Utils.NEWLINE;
      for (int ix = 0; ix < tbl.constraints.size(); ix++) {
        ConstraintInfo con = tbl.constraints.get(ix);
        message += con.comptype + " " + con.compvalue + Utils.NEWLINE;
      }

      JOptionPane.showMessageDialog(null, message, "Message Dialog", JOptionPane.INFORMATION_MESSAGE);
    }
  }
  
  private class Action_ConstraintRemove implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo tbl = paramList.get(rowSelection);
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove all constraints from '" + tbl.name + "' ?",
        "Remove constraints", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove all constraints for the selected symbolic
        tbl.constraints.clear();
      
        // update table display
        tableSortAndDisplay();
      }
    }
  }

  private class Action_ConstraintAdd implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;

      String result = JOptionPane.showInputDialog(null, "Enter the constraint (e.g. GE 23):");
      TableListInfo entry = paramList.get(row);

      int offset = result.indexOf(" ");
      if (offset < 0) {
        LauncherMain.printStatusError("missing numeric comparison value");
        return;
      }

      String comptype = result.substring(0, offset).trim().toUpperCase();
      String compval  = result.substring(offset + 1).trim();

      if (!entry.type.equals("D") && !entry.type.equals("F") && compval.contains(".")) {
        LauncherMain.printStatusMessage("comparison value will ignore decimal entries");
        compval = compval.substring(0, compval.indexOf("."));
      }

      // if user enters symbolic entry, convert to standard nomenclature
      switch (comptype) {
        case "==":  comptype = "EQ";  break;
        case "!=":  comptype = "NE";  break;
        case ">":   comptype = "GT";  break;
        case ">=":  comptype = "GE";  break;
        case "<":   comptype = "LT";  break;
        case "<=":  comptype = "LE";  break;
        default:
          break;
      }
      if (!comptype.equals("EQ") && !comptype.equals("GT") && !comptype.equals("LT") &&
          !comptype.equals("NE") && !comptype.equals("GE") && !comptype.equals("LE")) {
        LauncherMain.printStatusError("constraint must begin with { EQ, NE, GT, GE, LT, LE }");
        return;
      }
      try {
        double value = Double.parseDouble(compval);
      } catch (NumberFormatException ex) {
        LauncherMain.printStatusError("invalid comparison value (must be numeric)");
        return;
      }

      // add constraint to symbolic
      entry.constraints.add(new ConstraintInfo(comptype, compval));
    }
  }
  
  /**
   * action event when a key is pressed in the table.
   */
  private void tableKeyPressed(java.awt.event.KeyEvent evt) {                                          
    switch (evt.getKeyCode()) {
      case KeyEvent.VK_ENTER: // ENTER key
        break;
      default:
        break;
    }
  }                                         

  /**
   * handles cursoring with the UP arrow key when a table row is selected..
   */
  private class UpArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
//      debugDisplayEvent ("UpArrowAction: old row = " + rowSelection);
      JTable table = (JTable) evt.getSource();

      // if selection is not at the min, decrement the row selection
      if (rowSelection > 0) {
        --rowSelection;

        // highlight the new selection
        table.setRowSelectionInterval(rowSelection, rowSelection);

        // now scroll if necessary to make selection visible
        Rectangle rect = new Rectangle(table.getCellRect(rowSelection, 0, true));
        table.scrollRectToVisible(rect);
      }
    }
  }
    
  /**
   * handles cursoring with the DOWN arrow key when a table row is selected..
   */
  private class DnArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
//      debugDisplayEvent ("DnArrowAction: old row = " + rowSelectionrowSelection);
      JTable table = (JTable) evt.getSource();
      int maxrow = table.getRowCount();

      // if selection is valid & not at the max, increment the row selection
      if (rowSelection >= 0 && rowSelection < maxrow - 1) {
        ++rowSelection;

        // highlight the new selection
        table.setRowSelectionInterval(rowSelection, rowSelection);

        // now scroll if necessary to make selection visible
        Rectangle rect = new Rectangle(table.getCellRect(rowSelection, 0, true));
        table.scrollRectToVisible(rect);
      }
    }
  }

  /**
   * handles sorting of the table based on the header column clicked.
   * alternate clicks on same column reverse the sort order.
   */
  private class HeaderMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked (MouseEvent evt) {
      // get the selected header column
      int newSelection = table.columnAtPoint(evt.getPoint());
      if (newSelection >= 0) {
        int oldSelection = colSortSelection;
        colSortSelection = newSelection;
//        debugDisplayEvent ("HeaderMouseListener: (" + evt.getX() + "," + evt.getY() + ") -> col "
//                          + newSelection + " = " + getColumnName(newSelection));

        // invert the order selection if the same column is specified
        if (oldSelection == newSelection)
          bSortOrder = !bSortOrder;
                    
        // sort the table entries based on current selections
        tableSortAndDisplay();
      } else {
//        debugDisplayEvent ("HeaderMouseListener: (" + evt.getX() + "," + evt.getY() + ") -> col "
//                          + newSelection);
      }
    }
  }
    
}
