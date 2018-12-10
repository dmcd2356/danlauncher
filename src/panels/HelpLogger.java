/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import logging.FontInfo;
import logging.Logger;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import util.Utils;

/**
 *
 * @author dan
 */
public class HelpLogger {

  // types of messages
  private enum MsgType { TITLE, HEADER1, HEADER2, HEADER3, NORMAL, EMPHASIS, HYPERTEXT }

  private static JTextPane       panel;
  private static JScrollPane     scrollPanel;
  private static JFrame          helpFrame;
  private static GuiControls     gui;
  private static Logger          logger;
  private static String          tabName;
  private static boolean         tabSelected;
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  
  public HelpLogger(String name, GuiControls guicontrols) {
    tabSelected = false;
    tabName = name;
    gui = guicontrols;
    helpFrame = gui.newFrame(name, 600, 800, GuiControls.FrameSize.NOLIMIT);
    helpFrame.pack();
    helpFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    helpFrame.setLocationRelativeTo(null);
    helpFrame.addWindowListener(new Window_MainListener());

    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.TITLE.toString(),     FontInfo.TextColor.DkVio, FontInfo.FontType.BoldItalic, 20, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER1.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 18, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER2.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 16, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER3.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.BoldItalic, 15, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.NORMAL.toString(),    FontInfo.TextColor.Black, FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.EMPHASIS.toString(),  FontInfo.TextColor.DkGrey, FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HYPERTEXT.toString(), FontInfo.TextColor.Blue,  FontInfo.FontType.Bold, 14, fonttype);

    // create the text panel and assign it to the logger
    logger = new Logger(name, Logger.PanelType.TEXTPANE, true, fontmap);
    panel = (JTextPane) logger.getTextPanel();
    scrollPanel = logger.getScrollPanel();
    if (scrollPanel == null) {
      System.err.println("ERROR: HelpLogger failed to create a Scroll Pane");
      System.exit(1);
    }

    // add the scroll panel to the frame and a listener to the scroll bar
    gui.addPanelToPanel(null, scrollPanel);
    gui.setGridBagLayout(null, scrollPanel, GuiControls.Orient.NONE, true, GuiControls.Expand.BOTH);
    scrollPanel.getVerticalScrollBar().addAdjustmentListener(new HelpScrollListener());

    // add key listener for the debug viewer
    panel.addKeyListener(new HelpKeyListener());
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public void clear() {
    logger.clear();
  }

  public void printHelpPanel(String select) {
    // make panel writable, erase current data, then write the selected header
    panel.setEditable(true);
    logger.clear();
    
    // write the context-specific help messages
    switch(select) {
      case "GENERAL":
        printTitle("General Operation");
        printParagraph("This program allows the user to instrument and run a Java application using " +
                       "the ^^danalyzer^^ DSE. It provides an interface for allowing the user to " +
                       "specify <<Symbolic Parameters>> that the DSE keeps track of in the shadow stack " +
                       "it maintains that allows the ^^dansolver^^ application to solve. As the program " +
                       "runs, the DSE builds up a list of constraints for the symbolic parameters selected, " +
                       "and when the user wants to find solutions for those parameters, he can stop the " +
                       "execution and run the solver. The <<SOLUTIONS>> tab at the bottom of the screen " +
                       "will display a list of the constraints that were found for the symbolic parameters " +
                       "and the solutions (if any) that the solver was able to determine.");
        printParagraph("The first step is selecting the application to run. Yu do this by selecting the " +
                       "entry (Load jar file) from the Project menu selection. This will generate the " +
                       "instrumented jar file that can then be run with symbolic parameters enabled. This " +
                       "will then enable the entries in the ^^Controls^^ panel that allow you to run the " +
                       "application and solve any constraints.");
        printParagraph("The <<Controls>> panel allows you to specify the Main class of the application to " +
                       "run, and to ^^RUN^^ and ^^STOP^^ the execution of the application. Prior to running " +
                       "you will need to specify the argument list in the text area to the right of the " +
                       "STOP button. If the application runs as a web server and takes HTTP-formatted POST " +
                       "messages, these may be sent by the lines below the RUN and STOP buttons by specifying " +
                       "the port to send to in the first text window and the message to send in the second, " +
                       "and then pressing the ^^Post^^ button.");
        printParagraph("While the application is running, the user may opt to have messages from the " +
                       "application be displayed in the <<LOG>> tab at the bottom. The user enables these " +
                       "messages in the <<Configuration>> panel. Note that the more messages are displayed, " +
                       "the slower the application will run, so take care to only enable those messages that" +
                       "are essential to be monitored. When logging is enabled, if the CALLS selection " +
                       "is made it will also keep track of the call structure of the application, which will " +
                       "be displayed graphically in the <<CALLGRAPH>> tab. This allows the user to see " +
                       "visually how the methods are called.");
        printParagraph("Additionally, if the user has run another test application, ^^janalyzer^^ prior to " +
                       "this, he can, within that program, save the call graph that it generates for the " +
                       "entire application. It saves it as a JSON-formatted text file, which can then be " +
                       "loaded into this test program under the Project menu selection (Load JSON Graph), " +
                       "which will bring up that call graph in the <<JANAGRAPH>> tab at the bottom of the " +
                       "screen. When the application is run with CALLS logging messages enabled, it will " +
                       "highlight the methods in this graph as they are traversed to see visually the " +
                       "methods that are called and those that are not.");
        printParagraph("To specify a symbolic parameter to define for the application, the user needs to " +
                       "bring up the method he is interested in in the <<BYTECODE>> viewer tab. He may do " +
                       "this by either making the method selection in the Bytecode panel and pressing the " +
                       "^^Get Bytecode^^ button, or by clicking on the method of interest in either the " +
                       "CALLGRAPH or JANAGRAPH tabs and indicating you want to display the bytecode for " +
                       "that method. When in the BYTECODE viewer tab, the bytecode for the selected method " +
                       "is displayed on the left panel and the local parameters found are displayed in the " +
                       "right panel. Symbolic parameters are selected from one or more of the local " +
                       "parameters defined for a method. A parameter may be added to the symbolic list " +
                       "simply by clicking on the entry in the local parameters list, which will present " +
                       "the information about the parameter and ask whether you wish to add this to the " +
                       "list of symbolics. Clicking yes will add it. You may alternatively click on the " +
                       "corresponding LOAD or STORE opcode entry for the desired parameter in the bytecode " +
                       "panel on the left to get the same panel asking whether you wish to add the selected " +
                       "entry to the symbolic list.");
        printParagraph("Entries in the symbolic list may also be edited by clicking on the entry in the " +
                       "Symbolic Parameters panel at the top and selecting whether you wish to edit or " +
                       "delete the symbolic parameter. It also allows you to add your own user-defined " +
                       "constraints for the specified parameter by specifying one or more entries of " +
                       "EQ, NE, GT, GE, LT or LE to a specific numeric value.");
        break;
      case "CONFIG":
        printTitle("Configuration Panel");
        printNormalText("No help for this item yet!");
        break;
      case "CONTROLS":
        printTitle("Control and Bytecode Panels");
        printNormalText("No help for this item yet!");
        break;
      case "SYMBOLICS":
        printTitle("Symbolics Panel");
        printNormalText("No help for this item yet!");
        break;
      case "SOLUTIONS":
        printTitle("SOLUTIONS Tab");
        printNormalText("No help for this item yet!");
        break;
      case "BYTECODE":
        printTitle("BYTECODE Tab");
        printNormalText("No help for this item yet!");
        break;
      case "BYTEFLOW":
        printTitle("BYTEFLOW Tab");
        printNormalText("No help for this item yet!");
        break;
      case "LOG":
        printTitle("LOG Tab");
        printNormalText("No help for this item yet!");
        break;
      case "CALLGRAPH":
        printTitle("CALLGRAPH Tab");
        printNormalText("No help for this item yet!");
        break;
      case "JANAGRAPH":
        printTitle("JANAGRAPH Tab");
        printNormalText("No help for this item yet!");
        break;
    }
    
    // now keep user from editing the text and display the panel
    panel.setCaretPosition(0);
    panel.setEditable(false);
    helpFrame.setVisible(true);
  }
  
  private void printTitle(String message) {
    logger.printField(MsgType.HEADER1.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printMainHeader(String message) {
    logger.printField(MsgType.HEADER1.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printSubHeader(String message) {
    logger.printField(MsgType.HEADER2.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printSubSubHeader(String message) {
    logger.printField(MsgType.HEADER3.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printNormalText(String message) {
    logger.printField(MsgType.NORMAL.toString(), message);
  }
  
  private void printHighlightedText(String message) {
    logger.printField(MsgType.EMPHASIS.toString(), message);
  }
  
  private void printHyperText(String message) {
    logger.printField(MsgType.HYPERTEXT.toString(), message);
  }
  
  private void printLinefeed() {
    logger.printField(MsgType.NORMAL.toString(), Utils.NEWLINE);
  }
  
  private void printParagraph(String message) {
    // setup the metas to find
    List<MetaData> metalist = new ArrayList<>();
    metalist.add(new MetaData(MsgType.EMPHASIS, "^^", "^^"));
    metalist.add(new MetaData(MsgType.HYPERTEXT, "<<", ">>"));

    while (!message.isEmpty()) {
      // search for any next meta, exit if none
      boolean valid = false;
      MetaData selected = null;
      for (MetaData entry : metalist) {
        entry.checkForMeta(message);
        valid = entry.valid ? true : valid;
        // select the entry that we encounter first in the string
        if (entry.valid) {
          if (selected == null || (entry.start < selected.start)) {
            selected = entry;
          }
        }
      }
      if (!valid || selected == null || !selected.valid) {
        break;
      }
      
      // if there is non-meta text first, output it now
      if (selected.start > 0) {
        printNormalText(message.substring(0, selected.start));
        message = message.substring(selected.start + selected.entry.length());
      }

      // convert and output the meta text
      String metastr = message.substring(0, selected.end);
      logger.printField(selected.type.toString(), metastr);
      message = message.substring(selected.end + selected.exit.length());
    }
      
    // output any remaining non-meta text
    if (!message.isEmpty()) {
      printNormalText(message + Utils.NEWLINE + Utils.NEWLINE);
    }
  }
  
  private class MetaData {
    public MsgType type;
    public String  entry;
    public String  exit;
    public boolean valid;
    public int     start;
    public int     end;
    
    public MetaData(MsgType msgtype, String begin, String term) {
      type  = msgtype;
      entry = begin;
      exit  = term;
      valid = true;
      start = 0;
      end   = 0;
    }
    
    public void checkForMeta(String message) {
      if (!valid) {
        return;
      }

      end = -1;
      start = message.indexOf(entry);
      if (start < 0) {
        valid = false;
        return;
      }
      end = message.substring(start + entry.length()).indexOf(exit);
      if (end <= 0) {
        valid = false;
        return;
      }

      valid = true;
    }
  }
  
  private class Window_MainListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      helpFrame.setVisible(false);
    }
  }
  
  private class HelpScrollListener implements AdjustmentListener {

    @Override
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      // ignore if this tab is not active
      if (!tabSelected) {
        return;
      }
      
      if (evt.getValueIsAdjusting()) {
        return;
      }
      
      JScrollBar scrollBar = scrollPanel.getVerticalScrollBar();
    }
    
  }

  private class HelpKeyListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent ke) {
      // when the key is initially pressed
      //System.out.println("DebugKeyListener: keyPressed: " + ke.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent ke) {
      // follows keyPressed and preceeds keyReleased when entered key is character type
      //System.out.println("DebugKeyListener: keyTyped: " + ke.getKeyCode() + " = '" + ke.getKeyChar() + "'");
    }

    @Override
    public void keyReleased(KeyEvent ke) {
      if (tabSelected) {
        // when the key has been released
        //System.out.println("DebugKeyListener: keyReleased: " + ke.getKeyCode());
        //int curpos = panel.getCaretPosition();
        switch (ke.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            helpFrame.setVisible(false);
            break;
          case KeyEvent.VK_UP:
            break;
          case KeyEvent.VK_DOWN:
            break;
          case KeyEvent.VK_PAGE_UP:
            break;
          case KeyEvent.VK_PAGE_DOWN:
            break;
          default:
            break;
        }
      }
    }
  }

}
