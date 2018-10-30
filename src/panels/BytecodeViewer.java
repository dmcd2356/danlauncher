/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import logging.FontInfo;
import logging.FontInfo.FontType;
import logging.FontInfo.TextColor;
import logging.Logger;
import main.LauncherMain;

/**
 *
 * @author dan
 */
public class BytecodeViewer {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  // these are the bitmask values for highlighting types
  private static final int HIGHLIGHT_CURSOR = 0x0001;
  private static final int HIGHLIGHT_BRANCH = 0x0002;
  private static final int HIGHLIGHT_ALL    = 0xFFFF;
  
  // types of messages
  private static enum MsgType { ERROR, METHOD, TEXT, PARAM, COMMENT, BRANCH, INVOKE, LOAD, STORE, OTHER }
  
  private static enum ParseMode { NONE, CLASS, METHOD, DESCRIPTION, OPCODE, LINENUM_TBL, LOCALVAR_TBL, EXCEPTION_TBL }
  
  private static JTextPane       panel;
  private static Logger          logger;
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  private static ArrayList<BytecodeInfo> bytecode = new ArrayList<>();
  private static String          methodLoaded = "";
  private static ParseMode       parseMode;
  private static boolean         valid;

  public BytecodeViewer(String name) {
    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.ERROR.toString(),   TextColor.Red,    FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.METHOD.toString(),  TextColor.Violet, FontType.Italic, 16, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.TEXT.toString(),    TextColor.Black,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.PARAM.toString(),   TextColor.Brown,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.COMMENT.toString(), TextColor.Green,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.BRANCH.toString(),  TextColor.DkVio,  FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.INVOKE.toString(),  TextColor.Gold,   FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.LOAD.toString(),    TextColor.Green,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STORE.toString(),   TextColor.Blue,   FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.OTHER.toString(),   TextColor.Black,  FontType.Normal, 14, fonttype);

    valid = false;
    
    // create the text panel and assign it to the logger
    panel = new JTextPane();
    logger = new Logger((Component) panel, name, fontmap);
  
    // add mouse & key listeners for the bytecode viewer
    panel.addMouseListener(new BytecodeMouseListener());
    panel.addKeyListener(new BytecodeKeyListener());
}
  
  public JTextPane getTextPane() {
    return panel;
  }
  
  public boolean isValidBytecode() {
    return valid;
  }
  
  public boolean isMethodDisplayed(String classSelect, String methodSelect) {
    if (methodLoaded.equals(classSelect + "." + methodSelect) && !bytecode.isEmpty()) {
      return true;
    }
    return false;
  }
  
  public void highlightClear() {
    System.out.println("BytecodeLogger: highlightClear");
    panel.getHighlighter().removeAllHighlights();
    clearHighlighting(HIGHLIGHT_ALL);
  }

  public void highlightBranch(int line, boolean branch) {
    System.out.println("BytecodeLogger: highlightBranch " + line + " (" + branch + ")");
    // check for error conditions
    if (bytecode.isEmpty()) {
      System.err.println("highlightBranch: no bytecode found");
      return;
    }
    if (bytecode.size() < line) {
      System.err.println("highlightBranch: line " + line + " exceeds bytecode length " + bytecode.size());
      return;
    }
    BytecodeInfo bc = bytecode.get(line);
    if (!bc.isbranch) {
      System.err.println("highlightBranch: " + line + " not branch opcode: " + bc.opcode);
      return;
    }

    // clear all previous branch entries and remove all highlighting
    clearHighlighting(HIGHLIGHT_BRANCH);

    // mark the the branch line
    highlightSetMark(line, HIGHLIGHT_BRANCH);
    
    // determine if we take the branch or not to determine the next line to highlight
    if (branch) {
      // the branch destination (branch taken)
      int branchLine = findBranchLine(bc.param);
      if (branchLine >= 0) {
        highlightSetMark(branchLine, HIGHLIGHT_BRANCH);
      }
    } else {
      highlightSetMark(line + 1, HIGHLIGHT_BRANCH); // the line following the branch opcode
    }

    highlightUpdate();   // update the display
  }

  /**
   * reads the javap output and extracts and displays the opcodes for the selected method.
   * outputs the opcode info to the ArrayList 'bytecode' and saves the method name in 'methodLoaded'.
   * 
   * @param classSelect  - the class name of the method
   * @param methodSelect - the method name to search for
   * @param content      - the javap output
   */
  public void parseJavap(String classSelect, String methodSelect, String content) {
    // make sure we use dotted format for class name as that is what javap uses
    classSelect = classSelect.replaceAll("/", ".");
    LauncherMain.printCommandMessage("parseJavap: " + classSelect + "." + methodSelect);
    
    // javap uses the dot format for the class name, so convert it
    classSelect = classSelect.replace("/", ".");
    int lastoffset = -1;
    int curLine = 0;
    parseMode = ParseMode.NONE;
    
    // check if method is already loaded
    if (methodLoaded.equals(classSelect + "." + methodSelect) && !bytecode.isEmpty()) {
      // make sure highlighting is desabled for the text, and we're good to exit
      highlightClear();
      valid = true;
      return;
    }

    // else we need to start with a clean slate
    valid = false;
    int offset = methodSelect.indexOf("(");
    String signature = methodSelect.substring(offset);
    methodSelect = methodSelect.substring(0, offset);
    String clsCurrent = classSelect; // the default if no class specified
    methodLoaded = "";
    int linesRead = 0;
    logger.clear();
    bytecode = new ArrayList<>();

    String[] lines = content.split(NEWLINE);
    for (String entry : lines) {
      ++curLine;
      entry = entry.replace("\t", " ").trim();

      // modify this entry - it indicates the static initializer method
      if (entry.equals("static {};")) {
        entry = "<clinit>()";
      }
      
      // skip past these keywords
      if (entry.startsWith("public ") || entry.startsWith("private ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("static ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }

      String keyword = entry.trim();
      offset = keyword.indexOf(" ");
      if (offset > 0) {
        keyword = keyword.substring(0, offset);
      }
      switch (keyword) {
        case "class": // ignore this for now
          parseMode = ParseMode.CLASS;
          break; // handle the data on this line below
        case "LineNumberTable:":
          parseMode = ParseMode.LINENUM_TBL;
          LauncherMain.printCommandMessage("Line: " + curLine + " - LineNumberTable");
          continue;
        case "LocalVariableTable:":
          parseMode = ParseMode.LOCALVAR_TBL;
          LauncherMain.printCommandMessage("Line: " + curLine + " - LocalVariableTable");
          continue;
        case "Exception table:":
          parseMode = ParseMode.EXCEPTION_TBL;
          LauncherMain.printCommandMessage("Line: " + curLine + " - Exception table");
          continue;
        case "descriptor:":
          parseMode = ParseMode.DESCRIPTION;
          LauncherMain.printCommandMessage("Line: " + curLine + " - descriptor");
          break; // handle the data on this line below
        case "Code:":
          parseMode = ParseMode.OPCODE;
          LauncherMain.printCommandMessage("Line: " + curLine + " - Code");
          linesRead = 0;
          continue;
        default:
          break;
      }

      switch(parseMode) {
        case CLASS:
          // get the first word that follows "class"
          clsCurrent = entry.substring("class".length() + 1);
          offset = clsCurrent.indexOf(" ");
          if (offset > 0) {
            clsCurrent = clsCurrent.substring(0, offset);
          }
          clsCurrent = clsCurrent.trim();
          LauncherMain.printCommandMessage("Line: " + curLine + " - Class = " + clsCurrent);
          parseMode = ParseMode.NONE;
          continue;
        case LINENUM_TBL:
          if (keyword.equals("line")) {
            continue;
          }
          // else, we are no longer processing line numbers, revert to unknown state
          parseMode = ParseMode.NONE;
          break;
        case LOCALVAR_TBL:
          // this line we can ignore
          if (entry.equals("Start  Length  Slot  Name   Signature")) {
            // Start        (int) = bytecode offset to start of scope for parameter
            // Length       (int) = number of bytecode bytes in which it has scope
            // slot         (int) = parameter index
            // Name      (String) = parameter name
            // Signature (String) = data type
            continue;
          }
          // if starts with numeric, we have a valid table entry
          try {
            int val = Integer.parseUnsignedInt(keyword);
            // TODO; parse the local variable info
            continue;
          } catch (NumberFormatException ex) { }
          parseMode = ParseMode.NONE;
          break;
        case EXCEPTION_TBL:
          // this line we can ignore
          if (entry.equals("from    to  target type")) {
            continue;
          }
          // if starts with numeric, we have a valid table entry
          try {
            int val = Integer.parseUnsignedInt(keyword);
            // TODO: don't know if we want to use this info for showing exception jump points in the listing
            // from    (int) = bytecode offset to start of scope for exception
            // to      (int) = bytecode offset to end of scope for exception
            // target  (int) = bytecode offset to start of exception handler
            // type (String) = the exception that causes the jump
            continue;
          } catch (NumberFormatException ex) { }
          parseMode = ParseMode.NONE;
          break;
        case DESCRIPTION:
          if (!methodLoaded.isEmpty()) {
            // compare descriptor value with our method signature
            entry = entry.substring(entry.indexOf(" ") + 1).trim();
            if (entry.equals(signature)) {
              // print the method name as the 1st line
              printBytecodeMethod(methodLoaded);
              valid = true;
              LauncherMain.printCommandMessage("Line: " + curLine + " - signature match: " + entry);
            } else {
              LauncherMain.printCommandMessage("Line: " + curLine + " - no match: " + entry);
            }
          }
          parseMode = ParseMode.NONE;
          continue;
        case OPCODE:
          // check if we are at the correct method
          if (valid) {
            offset = getOpcodeOffset(entry, lastoffset);
            if (offset >= 0) {
              // process the bytecode instruction (save info in array for reference & display text in panel)
              ++linesRead;
              lastoffset = offset;
              entry = entry.substring(entry.indexOf(": ") + 1).trim();
              formatBytecode(offset, entry);
            }
          }
          continue;
        default:
          break;
      }
      
      // otherwise, let's check if it is a method definition
      // check for start of selected method (method must contain the parameter list)
      // first, eliminate the comment section, which may contain () and method names
      offset = entry.indexOf("//");
      if (offset > 0) {
        entry = entry.substring(0, offset);
      }
      offset = entry.indexOf("(");
      if (offset <= 0 || !entry.contains(")")) {
        continue;
      }

      // now remove the argument list, so the string will end with the method name
      // and eliminate any words preceeding it, so we just have the method name.
      String methName = entry.substring(0, offset);
      offset = methName.lastIndexOf(" ");
      if (offset > 0) {
        methName = methName.substring(offset + 1);
      }
      
      if (methName.isEmpty()) {
        continue; // method name not found - I guess we were wrong about this being method
      }

      // entry has paranthesis, must be a method name
      parseMode = ParseMode.METHOD;
          
      // seperate the name into class and method names
      String clsName = clsCurrent; // if no class specified, use the current class definition
      if (methName.equals(clsName)) { // the <init> method will have no name, just a class
        methName = "<init>";
      }
      offset = methName.lastIndexOf(".");
      if (offset > 0) {
        clsName = methName.substring(0, offset);
        methName = methName.substring(offset + 1);
      }
      
      // method entry found, let's see if it's the one we want
      LauncherMain.printCommandMessage("Line: " + curLine + " - Method: " + clsName + "." + methName);
      if (methodSelect.equals(methName) && classSelect.equals(clsName)) {
        methodLoaded = classSelect + "." + methodSelect + signature;
        LauncherMain.printCommandMessage("Line: " + curLine + " - Method match");
      } else if (valid) {
        LauncherMain.printCommandMessage("Line: " + curLine + " - opcode lines read: " + linesRead);
        return;
      }
    }
  }

  private class BytecodeInfo {
    public int     offset;      // byte offset of entry within the method
    public String  opcode;      // opcode
    public String  param;       // param entry(s)
    public String  comment;     // comment
    public boolean isbranch;    // true if opcode is a branch
    public int     ixStart;     // char offset in text of start of line
    public int     ixEnd;       // char offset in text of end of line
    public int     mark;        // highlight bits
  }
  
  private static void printBytecodeMethod(String methodName) {
    logger.printField("TEXT", "Method: ");
    logger.printField("METHOD", methodName + NEWLINE + NEWLINE);
  }
  
  private static boolean isBranchOpcode(String opcode) {
    return opcode.startsWith("if_")  ||
           opcode.startsWith("ifeq") ||
           opcode.startsWith("ifne") ||
           opcode.startsWith("ifgt") ||
           opcode.startsWith("ifge") ||
           opcode.startsWith("iflt") ||
           opcode.startsWith("ifle");
  }
  
  private static void printBytecodeOpcode(int line, String opcode, String param, String comment) {
    String type = "OTHER";
    if (opcode.startsWith("invoke")) {
      type = "INVOKE";
    } else if (isBranchOpcode(opcode)) {
      type = "BRANCH";
    }

    logger.printFieldAlignRight("TEXT", "" + line, 5);
    logger.printFieldAlignLeft(type, ":  " + opcode, 16);
    logger.printFieldAlignLeft("PARAM", param, 10);
    logger.printField("COMMENT", comment + NEWLINE);
  }
  
  private void highlightBytecode(int startOff, int endOff, Color color) {
    Highlighter highlighter = panel.getHighlighter();
    Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
    try {
      highlighter.addHighlight(startOff, endOff, painter);
    } catch (BadLocationException ex) { }
  }

  private void clearHighlighting(int type) {
    if (type == 0) {
      return;
    }
    
    // remove the specified type of highlighting from all lines
    for (int line = 0; line < bytecode.size(); line++) {
      BytecodeInfo bc = bytecode.get(line);
      if ((bc.mark & type) != 0) {
        bc.mark = bc.mark & ~type;
        bytecode.set(line, bc);
      }
    }
  }

  private void highlightSetMark(int line, int type) {
    BytecodeInfo bc = bytecode.get(line);
    bc.mark |= type;
    bytecode.set(line, bc);
  }
  
  private void highlightUpdate() {
    // clear all highlighting
    panel.getHighlighter().removeAllHighlights();

    // now re-apply the marked settings
    for (int line = 0; line < bytecode.size(); line++) {
      BytecodeInfo bc = bytecode.get(line);
      if (bc.mark != 0) {
        highlightBytecodeLine(line, bc.mark);
      }
    }
  }
  
  private void highlightCursorPosition(int cursor) {
    // make sure the cursor is within the range of the actual bytecode
    BytecodeInfo bc = bytecode.get(0);
    int start = bc.ixStart;
    bc = bytecode.get(bytecode.size() - 1);
    int end = bc.ixEnd;
    if (cursor < start || cursor > end) {
      return;
    }
    
    // find the current line that is cursor-highlighted
    int newpos = -1;
    for (int line = 0; line < bytecode.size(); line++) {
      bc = bytecode.get(line);
      if (cursor >= bc.ixStart && cursor <= bc.ixEnd) {
        // mark the selected line
        bc.mark = bc.mark | HIGHLIGHT_CURSOR;
        bytecode.set(line, bc);
        newpos = bc.ixStart;
        //System.out.println("highlightCursorPosition: selected line " + line + ", offset = "
        //    + bc.ixStart + " - " + bc.ixEnd);
      } else {
        // remove mark from all other lines
        bc.mark = bc.mark & ~HIGHLIGHT_CURSOR;
        bytecode.set(line, bc);
      }
    }

    // update the highlighting
    highlightUpdate();
  }
  
  private void highlightBytecodeLine(int line, int type) {
    BytecodeInfo bc = bytecode.get(line);

    // check if we already have a highlighting for the line
    bc.mark |= type;

    Color color;
    switch (bc.mark) {
      case HIGHLIGHT_CURSOR:
        color = Color.yellow;
        break;
      case HIGHLIGHT_BRANCH:
        color = Color.pink;
        break;
      case HIGHLIGHT_BRANCH | HIGHLIGHT_CURSOR:
        color = Color.orange;
        break;
      default:
        color = Color.white;
        break;
    }
    
    // highlight the the branch line
    highlightBytecode(bc.ixStart, bc.ixEnd + 1, color);
    
    // add entry to mapping of highlighted lines
    bytecode.set(line, bc);
  }
  
  private int findBranchLine(String param) {
    int offset;

    // convert branch location to an int value
    try {
      offset = Integer.parseUnsignedInt(param);
    } catch (NumberFormatException ex) {
      System.err.println("findBranchOffset: invalid branch location value: " + param);
      return -1;
    }
    
    // search for offset entry in array
    for (int ix = 0; ix < bytecode.size(); ix++) {
      if (offset == bytecode.get(ix).offset) {
        return ix;
      }
    }

    System.err.println("findBranchOffset: branch location value not found in method: " + param);
    return -1;
  }
  
  /**
   * outputs the bytecode message to the status display.
   * 
   * @param line
   * @param entry  - the line read from javap
   */
  private void formatBytecode(int line, String entry) {
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

      // get current length of text generated for the starting offset
      int startix = panel.getText().length();

      // add the line to the text display
      printBytecodeOpcode(line, opcode, param, comment);

      // add entry to array
      BytecodeInfo bc = new BytecodeInfo();
      bc.offset   = line;
      bc.opcode   = opcode;
      bc.param    = param;
      bc.comment  = comment;
      bc.isbranch = isBranchOpcode(opcode);
      bc.ixStart  = startix;
      bc.ixEnd    = panel.getText().length() - 1;
      bc.mark     = 0;
      bytecode.add(bc);
    }
  }

  /**
   * determine if line is valid opcode entry & return byte offset index if so.
   * check for line number followed by ": " followed by opcode, indicating this is bytecode.
   * 
   * @param entry - the line to be examined
   * @param lastoffset - the byte offset of the last line read (for verifying validity)
   * @return opcode offset (-1 if not opcode or found next method)
   */
  private int getOpcodeOffset(String entry, int lastoffset) {
    int ix = entry.indexOf(": ");
    if (ix <= 0) {
      return -1;
    }

    try {
      int offset = Integer.parseUnsignedInt(entry.substring(0, ix));
      if (offset > lastoffset) {
        return offset;
      }
    } catch (NumberFormatException ex) { }

    return -1;
  }
  
  private class BytecodeMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked (MouseEvent evt) {
      String contents = panel.getText();

      // set caret to the mouse location and get the caret position (char offset within text)
      panel.setCaretPosition(panel.viewToModel(evt.getPoint()));
      int curpos = panel.getCaretPosition();
      
      // now determine line number and offset within the line for the caret
      int line = 0;
      int offset = 0;
      for (int ix = 0; ix < curpos; ix++) {
        if (contents.charAt(ix) == '\n' || contents.charAt(ix) == '\r') {
          ++line;
          offset = ix;
        }
      }
      int curoffset = curpos - offset - 1;
      //System.out.println("cursor = " + curpos + ", line " + line + ", offset " + curoffset);
      
      // highlight the current line selection
      highlightCursorPosition(curpos);
    }
  }
  
  private class BytecodeKeyListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent ke) {
      // when the key is initially pressed
      //System.out.println("keyPressed: " + ke.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent ke) {
      // follows keyPressed and preceeds keyReleased when entered key is character type
      //System.out.println("keyTyped: " + ke.getKeyCode() + " = '" + ke.getKeyChar() + "'");
    }

    @Override
    public void keyReleased(KeyEvent ke) {
      // when the key has been released
      //System.out.println("keyReleased: " + ke.getKeyCode());
      int curpos = panel.getCaretPosition();
      switch (ke.getKeyCode()) {
        case KeyEvent.VK_UP:    // UP ARROW key
          //System.out.println("UP key: at cursor " + curpos);
          highlightCursorPosition(curpos);
          break;
        case KeyEvent.VK_DOWN:  // DOWN ARROW key
          //System.out.println("DN key: at cursor " + curpos);
          highlightCursorPosition(curpos);
          break;
        default:
          break;
      }
    }

  }
  
}
