/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import main.LauncherMain;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 *
 * @author dan
 */
public class DatabaseTable {
  
  private static final ArrayList<String> SYMBOLIC_PARAMS = new ArrayList<>();
  
  private static final String[] TABLE_COLUMNS = new String [] {
    "ID", "Method", "Offset", "Path", "Cost", "Solvable", "Solution" //, "Constraint"
  };

  private static JTable   dbTable;
  private static Timer    databaseTimer;
  private static ArrayList<DatabaseInfo> dbList = new ArrayList<>();
  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;

  private static MongoClient                 mongoClient;
  private static MongoDatabase               database;
  private static MongoCollection<Document>   collection;

  
  private static class DatabaseInfo {
    String  id;
    String  method;
    String  offset;
    String  lastpath;
    String  cost;
    String  solvable;
    String  solution;
    String  constraint;
    boolean updated;
    
    public DatabaseInfo(String idn, String meth, Integer off,
                        Boolean path, Integer cst, Boolean solve, String sol, String con) {
      id         = idn   == null ? "" : idn;
      method     = meth  == null ? "" : meth;
      offset     = off   == null ? "" : off.toString();
      lastpath   = path  == null ? "" : path.toString();
      cost       = cst   == null ? "" : cst.toString();
      solvable   = solve == null ? "" : solve.toString();
      solution   = sol   == null ? "" : sol;
      constraint = con   == null ? "" : con;
      updated = false;
    }
    
    public void setUpdated() {
      updated = true;
    }
    
    public boolean isUpdated() {
      return updated;
    }
  } 
  
  public DatabaseTable (JTable dbaseTable) {
    dbTable = dbaseTable;
    
    // init the access to mongo
    mongoClient = MongoClients.create();
    database = mongoClient.getDatabase("mydb");
    collection = database.getCollection("dsedata");
    
    // init the params
    bSortOrder = false;
    rowSelection = -1;
    colSortSelection = 0;
    
    dbTable.setModel(new DefaultTableModel(new Object [][]{ }, TABLE_COLUMNS) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false, false, false
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

    dbTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
    dbTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        dbTableMouseClicked(evt);
      }
    });
//    dbTable.addKeyListener(new java.awt.event.KeyAdapter() {
//      @Override
//      public void keyPressed(java.awt.event.KeyEvent evt) {
//        dbTableKeyPressed(evt);
//      }
//    });
        
    // align columns in database table to center
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment( SwingConstants.CENTER );
    dbTable.setDefaultRenderer(String.class, centerRenderer);
    TableCellRenderer headerRenderer = dbTable.getTableHeader().getDefaultRenderer();
    JLabel headerLabel = (JLabel) headerRenderer;
    headerLabel.setHorizontalAlignment( SwingConstants.CENTER );
        
    // create a timer for updating the cloud job information
    databaseTimer = new Timer(2000, new DatabaseUpdateListener());
    databaseTimer.start();

    // create up & down key handlers for cloud row selection
    AbstractAction tableUpArrowAction = new UpArrowAction();
    AbstractAction tableDnArrowAction = new DnArrowAction();
    dbTable.getInputMap().put(KeyStroke.getKeyStroke("UP"), "upAction");
    dbTable.getActionMap().put( "upAction", tableUpArrowAction );
    dbTable.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "dnAction");
    dbTable.getActionMap().put( "dnAction", tableDnArrowAction );
        
    // add a mouse listener for the cloud table header selection
    JTableHeader cloudTableHeader = dbTable.getTableHeader();
    cloudTableHeader.addMouseListener(new HeaderMouseListener());
  }
    
  public void clearDB() {
    collection.deleteMany(new Document());
  }
  
  public void exit() {
    databaseTimer.stop();
  }
  
  public void initSymbolic() {
    SYMBOLIC_PARAMS.clear();
  }

  public void addSymbolic(String symname) {
    // convert dot format of method name to slash format for Mongo
    symname = symname.replace(".","/");
    SYMBOLIC_PARAMS.add(symname);
  }
  
  public static void readDatabase() {
    // read data base for solutions to specified parameter that are solvable
    FindIterable<Document> iterdocs = collection.find() //(Bson) new BasicDBObject("solvable", true))
        .sort((Bson) new BasicDBObject("_id", -1)); // sort in descending order (most recent first)

    dbList.clear();
    for (Document doc : iterdocs) {
      // determine if we have any solutions
      String solution = "---";
      Document solutions = (Document) doc.get("solution"); //doc.getString("solution");
      if (solutions != null) {
        // String type = (String) solutions.get("type");
        String paramName = (String) solutions.get("name");
        String value = (String) solutions.get("value");
        if (SYMBOLIC_PARAMS.contains(paramName)) {
          paramName = "P" + SYMBOLIC_PARAMS.lastIndexOf(paramName);
          solution = paramName + " = " + value;
        }
      }

      DatabaseInfo entry = new DatabaseInfo(doc.getObjectId("_id").toHexString(),
                                            doc.getString("method"),
                                            doc.getInteger("offset"),
                                            doc.getBoolean("lastpath"),
                                            doc.getInteger("cost"),
                                            doc.getBoolean("solvable"),
                                            solution,
                                            doc.getString("constraint") );
      dbList.add(entry);
    }
  }
  
  private String getColumnName(int col) {
    if (col < 0 || col >= TABLE_COLUMNS.length) {
      col = 0;
    }
    return TABLE_COLUMNS[col];
  }
  
  private String getColumnParam(int col, DatabaseInfo dbinfo) {
    switch(getColumnName(col)) {
      default: // fall through...
      case "ID":
        return dbinfo.id;
      case "Method":
        return dbinfo.method;
      case "Offset":
        return dbinfo.offset;
      case "Path":
        return dbinfo.lastpath;
      case "Cost":
        return dbinfo.cost;
      case "Solvable":
        return dbinfo.solvable;
//      case "Param":
//        return dbinfo.param;
      case "Solution":
        return dbinfo.solution;
      case "Constraint":
        return dbinfo.constraint;
    }
  }
  
  // NOTE: the order of the entries should match the order of the columns
  private Object[] makeRow(DatabaseInfo tableEntry) {
    return new Object[]{
        tableEntry.id,
        tableEntry.method,
        tableEntry.offset,
        tableEntry.lastpath,
        tableEntry.cost,
        tableEntry.solvable,
        tableEntry.solution,
//        tableEntry.constraint
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
    Collections.sort(dbList, new Comparator<DatabaseInfo>() {
      @Override
      public int compare(DatabaseInfo job1, DatabaseInfo job2) {
        String object1 = getColumnParam(colSortSelection, job1);
        String object2 = getColumnParam(colSortSelection, job2);
        if (!bSortOrder)
          return  object1.compareTo(object2);
        else
          return  object2.compareTo(object1);
      }
    });

    // clear out the table entries
    DefaultTableModel model = (DefaultTableModel) dbTable.getModel();
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
    for (int ix = 0; ix < dbList.size(); ix++) {
      DatabaseInfo tableEntry = dbList.get(ix);
      model.addRow(makeRow(tableEntry));
    }

    // auto-resize column width
    resizeColumnWidth(dbTable);
  }
    
  /**
   * action event when the mouse is clicked in the table.
   */
  private void dbTableMouseClicked(java.awt.event.MouseEvent evt) {                                            
//    bPauseCloudUpdate = true;
    int row = dbTable.rowAtPoint(evt.getPoint());
    int col = dbTable.columnAtPoint(evt.getPoint());
    String colname = getColumnName(col);

    switch(colname) {
      case "Method":
      case "Offset":
        String meth   = (String)dbTable.getValueAt(row, getColumnIndex("Method"));
        String line   = (String)dbTable.getValueAt(row, getColumnIndex("Offset"));
        String branch = (String)dbTable.getValueAt(row, getColumnIndex("Path"));
        int offset = meth.lastIndexOf("/");
        String cls = meth.substring(0, offset);
        meth = meth.substring(offset + 1);
        LauncherMain.generateBytecode(cls, meth);
        LauncherMain.highlightBranch(Integer.parseInt(line), branch.equals("true"));
        break;
      case "Solution":
        String solution = (String)dbTable.getValueAt(row, col);
        // TODO: copy the solution value to the input field
        break;
      case "ID":
        //String constraint = (String)dbTable.getValueAt(row, getColumnIndex("Constraint"));
        String constraint = dbList.get(row).constraint;
        // pop up a panel that contains the full context string
        GuiControls.makeFrameWithText("Constraint value", constraint, 500, 300);
        break;
      default:
        break;
    }
  }                                           

  /**
   * action event when a key is pressed in the table.
   */
  private void dbTableKeyPressed(java.awt.event.KeyEvent evt) {                                          
    switch (evt.getKeyCode()) {
      case KeyEvent.VK_ENTER: // ENTER key
        break;
      default:
        break;
    }
  }                                         

  /**
   * handles the updating of the table from the database.
   * This is run from a timer to keep the table updated.
   */
  private class DatabaseUpdateListener implements ActionListener{

  @Override
    public void actionPerformed(ActionEvent e) {
      // exit if database panel is not selected
      if (!LauncherMain.isTabSelection_DATABASE()) {
        return;
      }

      // request the database list and save as list entries
      readDatabase();
                
      // sort the table entries based on current selections
      tableSortAndDisplay();
                
      // re-mark the selected row (if any)
      if (rowSelection >= 0) {
        dbTable.setRowSelectionInterval(rowSelection, rowSelection);
      }
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
      int newSelection = dbTable.columnAtPoint(evt.getPoint());
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
