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
import java.util.HashMap;
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
  private enum MsgType { HEADER1, HEADER2, HEADER3, NORMAL, EMPHASIS, HYPERTEXT }

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
    FontInfo.setTypeColor (fontmap, MsgType.HEADER1.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 20, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER2.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 18, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER3.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.BoldItalic, 16, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.NORMAL.toString(),    FontInfo.TextColor.Black, FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.EMPHASIS.toString(),  FontInfo.TextColor.Black, FontInfo.FontType.Bold, 14, fonttype);
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
    logger.printField(MsgType.HEADER1.toString(), select + Utils.NEWLINE);
    
    // write the context-specific help messages
    switch(select) {
      case "GENERAL":
        printNormalText("No help for this item yet!");
        break;
      case "CONFIG":
        printNormalText("No help for this item yet!");
        break;
      case "CONTROLS":
        printNormalText("No help for this item yet!");
        break;
      case "SYMBOLICS":
        printNormalText("No help for this item yet!");
        break;
      case "SOLUTIONS":
        printNormalText("No help for this item yet!");
        break;
      case "BYTECODE":
        printNormalText("No help for this item yet!");
        break;
      case "BYTEFLOW":
        printNormalText("No help for this item yet!");
        break;
      case "LOG":
        printNormalText("No help for this item yet!");
        break;
      case "CALLGRAPH":
        printNormalText("No help for this item yet!");
        break;
      case "JANAGRAPH":
        printNormalText("No help for this item yet!");
        break;
    }
    
    // now keep user from editing the text and display the panel
    panel.setEditable(false);
    helpFrame.setVisible(true);
  }
  
  private void printNormalText(String message) {
    logger.printField(MsgType.NORMAL.toString(), message + Utils.NEWLINE);
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
