/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

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
//import java.util.HashMap;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
//import javax.swing.SpinnerDateModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 *
 * @author dan
 */
public class DatabaseTable {
  
  private static final ArrayList<String> SYMBOLIC_PARAMS = new ArrayList<>();
  
  private static JTable   dbTable;
  private static DatabaseUpdateListener dbListener;
  private static Timer    databaseTimer;
//  private static HashMap<String, DatabaseInfo> dbList = new HashMap<>();
  private static ArrayList<DatabaseInfo> dbList = new ArrayList<>();
  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;

  private static MongoClient                 mongoClient;
  private static MongoDatabase               database;
  private static MongoCollection<Document>   collection;

  
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
    
    dbTable.setModel(new DefaultTableModel(
      new Object [][] { },
      new String [] { "ID", "Param", "Method", "Offset", "Solvable", "Solution", "Constraint" }
    ) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false, false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });

    dbTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
    dbTable.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        dbTableMouseClicked(evt);
      }
    });
    dbTable.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        dbTableKeyPressed(evt);
      }
    });
        
    // align columns in database table to center
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment( SwingConstants.CENTER );
    dbTable.setDefaultRenderer(String.class, centerRenderer);
    TableCellRenderer headerRenderer = dbTable.getTableHeader().getDefaultRenderer();
    JLabel headerLabel = (JLabel) headerRenderer;
    headerLabel.setHorizontalAlignment( SwingConstants.CENTER );
        
    // create a timer for updating the cloud job information
    dbListener = new DatabaseUpdateListener();
    databaseTimer = new Timer(2000, dbListener);
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
    cloudTableHeader.addMouseListener(new CloudJobsHeaderMouseListener());
  }
    
  public void exit() {
    databaseTimer.stop();
  }
  
  public static void addSymbolic(String symname) {
    // convert dot format of method name to slash format for Mongo
    symname = symname.replace(".","/");
    SYMBOLIC_PARAMS.add(symname);
  }
  
  private static class DatabaseInfo {
    String  id;
    String  method;
    String  offset;
    String  constraint;
    String  solvable;
    String  param;
    String  solution;
    boolean updated;
    
    public DatabaseInfo(String idn, String parm, String meth, String off, String con, String solve, String sol) {
      id         = idn   == null ? "" : idn;
      method     = meth  == null ? "" : meth;
      offset     = off   == null ? "" : off;
      constraint = con   == null ? "" : con;
      solvable   = solve == null ? "" : solve;
      param      = parm  == null ? "" : parm;
      solution   = sol   == null ? "" : sol;
      updated = false;
    }
    
    public void setUpdated() {
      updated = true;
    }
    
    public boolean isUpdated() {
      return updated;
    }
  } 
  
    public static void readDatabase() {
    // read data base for solutions to specified parameter that are solvable
    FindIterable<Document> iterdocs = collection.find() //(Bson) new BasicDBObject("solvable", true))
        .sort((Bson) new BasicDBObject("_id", -1)); // sort in descending order (most recent first)

    dbList.clear();
    for (Document doc : iterdocs) {
      // get the id value for the entry
      String id = doc.getObjectId("_id").toHexString(); // .getString("_id");
      
      Integer offset = doc.getInteger("offset");
      Boolean solvable = doc.getBoolean("solvable");
      Document solutions = (Document) doc.get("solution"); //doc.getString("solution");
      String solution = "unknown solution";
      DatabaseInfo entry = new DatabaseInfo(id, "unknown",
                                            doc.getString("method"),
                                            (offset == null ? null : offset.toString()),
                                            doc.getString("constraint"),
                                            (solvable == null ? null : solvable.toString()),
                                            solution);
//      if (!dbList.containsKey(id)) {
//        dbList.put(id, entry);
//      }
      dbList.add(entry);
    }
  }
  
  private static int getColumnIndex(String colname) {
    for (int col = 0; col < dbTable.getColumnCount(); col++) {
      String entry = dbTable.getColumnName(col);
      if(entry != null && entry.equals(colname)) {
        return col;
      }
    }
    return 0;
  }
  
  private void dbTableMouseClicked(java.awt.event.MouseEvent evt) {                                            
//    bPauseCloudUpdate = true;
    int row = dbTable.rowAtPoint(evt.getPoint());
    int col = dbTable.columnAtPoint(evt.getPoint());
    String colname = dbTable.getColumnName(col);

    switch(colname) {
      case "Method":
      case "Offset":
        String meth = (String)dbTable.getValueAt(row, getColumnIndex("Method"));
        String line = (String)dbTable.getValueAt(row, getColumnIndex("Offset"));
        int offset = meth.lastIndexOf("/");
        String cls = meth.substring(0, offset);
        meth = meth.substring(offset + 1);
        GuiPanel.runBytecode(cls, meth, Integer.parseInt(line));
        break;
      case "Solution":
        String solution = (String)dbTable.getValueAt(row, col);
        // copy the solution value to the input field
        break;
      case "Constraint":
        String constraint = (String)dbTable.getValueAt(row, col);
        // pop up a panel that contains the full context string
//        JOptionPane.showMessageDialog (null, constraint, "Constraint value", JOptionPane.INFORMATION_MESSAGE);
        GuiControls.makeFrameWithText("Constraint value", constraint);
        break;

      case "ID":
      case "Param":
      case "Solvable":
      default:
        break;
    }
  }                                           

  private void dbTableKeyPressed(java.awt.event.KeyEvent evt) {                                          
    switch (evt.getKeyCode()) {
      case KeyEvent.VK_ENTER: // ENTER key
//        if (rowSelection >= 0) {
//          selectFileToDownload (rowSelection);
//        }
        // the ENTER key will by default advance the row selection.
        // let's restore it, since we are using it to download files instead.
        // TODO: disabled for now. the row selection is reset correctly,
        //       but the display still highlights the next line.
        //       If we enable this line, it causes the up/down arrows
        //       to behave funny because the hilighted row does not match
        //       the value of cloudRowSelection anymore.
//        dbTable.setRowSelectionInterval(cloudRowSelection, cloudRowSelection);
          break;
/*
      // These didn't work correctly - they caused the cursor selection to
      // move rather randomly. These are now handled using KeyBindings for the table.
      case KeyEvent.VK_UP:    // UP ARROW key
      case KeyEvent.VK_DOWN:  // DOWN ARROW key
        break;
*/
                
      default:
        break;
    }
  }                                         

  /**
   * This performs a sort on the cloudJobList and updates the table display.
   * The sort based on the current criteria of:
   * 'colSortSelection' which specifies the column on which to sort and
   * 'bSortOrder' which specifies either ascending (false) or descending (true) order.
   */
  private void cloudJobsSortAndDisplay () {
    // determine the sort criteria
    String colname = dbTable.getColumnName(colSortSelection);
    String dir = bSortOrder ? "1" : "0";

    // sort the table entries
    Collections.sort(dbList, new Comparator<DatabaseInfo>() {
      @Override
      public int compare(DatabaseInfo job1, DatabaseInfo job2) {
        String object1, object2;
        switch(colSortSelection) {
          default: // fall through...
          case 0: object1 = job1.id;         object2 = job2.id;         break;
          case 1: object1 = job1.param;      object2 = job2.param;      break;
          case 2: object1 = job1.method;     object2 = job2.method;     break;
          case 3: object1 = job1.offset;     object2 = job2.offset;     break;
          case 4: object1 = job1.solvable;   object2 = job2.solvable;   break;
          case 5: object1 = job1.solution;   object2 = job2.solution;   break;
          case 6: object1 = job1.constraint; object2 = job2.constraint; break;
        }
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
    String[] columnNames = new String [] {
        "ID", "Param", "Method", "Offset", "Solvable", "Solution", "Constraint"
    };
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
      model.addRow(new Object[]{
          tableEntry.id,
          tableEntry.param,
          tableEntry.method,
          tableEntry.offset,
          tableEntry.solvable,
          tableEntry.solution,
          tableEntry.constraint
      });
    }

    // auto-resize column width
    resizeColumnWidth(dbTable);
  }
    
  private class UpArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
      String itemName = "UpArrowAction";
//      debugDisplayEvent (DebugEvent.ActionPerformed, itemName, "old row = " + rowSelection);
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
    
  private class DnArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
      String itemName = "DnArrowAction";
//      debugDisplayEvent (DebugEvent.ActionPerformed, itemName, "old row = " + rowSelectionrowSelection);
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
   * handles the updating of the table from the database.
   */
  private class DatabaseUpdateListener implements ActionListener{

  @Override
    public void actionPerformed(ActionEvent e) {
      // exit if database panel is not selected
      if (!GuiPanel.isTabSelection(GuiPanel.PanelTabs.DATABASE)) {
        return;
      }

      // request the database list and save as list entries
      readDatabase();
                
      // sort the table entries based on current selections
      cloudJobsSortAndDisplay();
                
      // re-mark the selected row (if any)
      if (rowSelection >= 0) {
        dbTable.setRowSelectionInterval(rowSelection, rowSelection);
      }
    }
  }    

  private class CloudJobsHeaderMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked (MouseEvent evt) {
      String itemName = "cloudJobsHeader";

      // get the selected header column
      int newSelection = dbTable.columnAtPoint(evt.getPoint());
      if (newSelection >= 0) {
        int oldSelection = colSortSelection;
        colSortSelection = newSelection;
        String colname = dbTable.getColumnName(newSelection);
//        debugDisplayEvent (DebugEvent.MouseClicked, itemName, "(" + evt.getX() + "," + evt.getY() + ") -> col " + newSelection + " = " + colname);

        // invert the order selection if the same column is specified
        if (oldSelection == newSelection)
          bSortOrder = !bSortOrder;
                    
        // sort the table entries based on current selections
        cloudJobsSortAndDisplay();
      } else {
//        debugDisplayEvent (DebugEvent.MouseClicked, itemName, "(" + evt.getX() + "," + evt.getY() + ") -> col " + newSelection);
      }
    }
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

}
