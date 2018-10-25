/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bytecode;

import java.awt.Color;
import java.awt.Component;
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

/**
 *
 * @author dan
 */
public class BytecodeLogger {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  // these are the bitmask values for highlighting types
  private static final int HIGHLIGHT_CURSOR = 0x0001;
  private static final int HIGHLIGHT_BRANCH = 0x0002;
  private static final int HIGHLIGHT_ALL    = 0xFFFF;
  
  // types of messages
  private enum MsgType { ERROR,METHOD, TEXT, PARAM, COMMENT, BRANCH, INVOKE, LOAD, STORE, OTHER }
  
  private static JTextPane       panel;
  private static Logger          logger;
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  private static ArrayList<BytecodeInfo> bytecode = new ArrayList<>();
  private static String          methodLoaded = "";

  public BytecodeLogger(String name) {
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

    // create the text panel and assign it to the logger
    panel = new JTextPane();
    logger = new Logger((Component) panel, name, fontmap);
  }
  
  public JTextPane getTextPane() {
    return panel;
  }
  
  public void clear() {
    logger.clear();
  }
  
  public boolean isMethodDisplayed(String classSelect, String methodSelect) {
    if (methodLoaded.equals(classSelect + "." + methodSelect) && !bytecode.isEmpty()) {
      return true;
    }
    return false;
  }
  
  public void highlightClear() {
    System.out.println("highlightClear");
    panel.getHighlighter().removeAllHighlights();
    clearHighlighting(HIGHLIGHT_ALL);
  }

  public void showCurrentLine(int line) {
    if (line < 2) {
      return;
    }
    
    // because we can't change the highlight color once set, clear everything and start afresh
    // clear all previous branch entries and remove all highlighting
    panel.getHighlighter().removeAllHighlights();
    clearHighlighting(HIGHLIGHT_CURSOR); // clears the CURSOR bits from the bytecode info

    // now mark the current line (account for the 2 line header at top of file)
    highlightSetMark(line - 2, HIGHLIGHT_CURSOR);
    
    highlightUpdate();   // update the display

//    highlightUpdate(HIGHLIGHT_BRANCH);   // update the display
//
//    // now mark the current line (account for the 2 line header at top of file)
//    highlightBytecodeLine(line - 2, HIGHLIGHT_CURSOR);
  }
  
  public void highlightBranch(int branchLine, boolean branch) {
    // check for error conditions
    if (bytecode.isEmpty()) {
      System.err.println("highlightBranch: no bytecode found");
      return;
    }
    if (bytecode.size() < branchLine) {
      System.err.println("highlightBranch: line " + branchLine + " exceeds bytecode length " + bytecode.size());
      return;
    }
    BytecodeInfo bc = bytecode.get(branchLine);
    if (!bc.isbranch) {
      System.err.println("highlightBranch: " + branchLine + " not branch opcode: " + bc.opcode);
      return;
    }

    // clear all previous branch entries and remove all highlighting
    panel.getHighlighter().removeAllHighlights();
    clearHighlighting(HIGHLIGHT_BRANCH);

    // if a cursor selection was made, set it now
//    highlightUpdate(HIGHLIGHT_CURSOR);
    
    // highlight the the branch line
//    highlightBytecodeLine(branchLine, HIGHLIGHT_BRANCH);
    highlightSetMark(branchLine, HIGHLIGHT_BRANCH);
    
    // determine if we take the branch or not to determine the next line to highlight
    int nextLine = branchLine + 1; // the line following the branch opcode (branch not taken)
    if (branch) {
      nextLine = findBranchLine(bc.param); // the branch destination (branch taken)
    }

    if (nextLine >= 0) {
      highlightSetMark(nextLine, HIGHLIGHT_BRANCH);
//      highlightBytecodeLine(nextLine, HIGHLIGHT_BRANCH);
    }

    highlightUpdate();   // update the display
  }

  /**
   * reads the javap output and extracts and displays the opcodes for the selected method.
   * outputs the info to the bytecode logger.
   * 
   * @param classSelect  - the class name of the method
   * @param methodSelect - the method name to search for
   * @param content      - the javap output
   */
  public void parseJavap(String classSelect, String methodSelect, String content) {
    // javap uses the dot format for the class name, so convert it
    classSelect = classSelect.replace("/", ".");
    int lastoffset = -1;
    
    // check if method is already loaded
    if (methodLoaded.equals(classSelect + "." + methodSelect) && !bytecode.isEmpty()) {
      // make sure highlighting is desabled for the text, and we're good to exit
      highlightClear();
      return;
    }

    // else we need to start with a clean slate
    methodLoaded = "";
    bytecode = new ArrayList<>();

    String[] lines = content.split(NEWLINE);
    for (String entry : lines) {
      entry = entry.replace("\t", " ").trim();
        
      // ignore these entries for now
      if (entry.startsWith("public class ") ||
          entry.startsWith("private class ") ||
          entry.startsWith("class ") ||
          entry.startsWith("descriptor:") ||
          entry.startsWith("Code:")) {
        continue;
      }

      // if the method was found, start checking for opcodes
      if (methodLoaded.equals(classSelect + "." + methodSelect)) {
        // check for valid opcode definition
        int offset = getOpcodeOffset(entry, lastoffset);
        if (offset < 0) {
          // non-opcode line:
          // if we haven't found bytecodes yet, keep checking
          if (bytecode.isEmpty()) {
            continue;
          }
          // else, we are done (assumes once we start the opcode phase, all entries are opcodes
          // until the end of the method.)
          return;
        }

        // process the bytecode instruction (save info in array for reference & display text in panel)
        lastoffset = offset;
        entry = entry.substring(entry.indexOf(": ") + 1).trim();
        formatBytecode(offset, entry);
        
      } else {
        // searching for specified method within the class
        // check for start of selected method (method must contain the parameter list)
        if (entry.contains("(") && entry.contains(")")) {
          // extract the word that contains the param list
          String methName = "";
          String array[] = entry.split("\\s+");
          for (String word : array) {
            if (word.contains("(") && word.contains(")")) {
              methName = word;
            }
          }
          if (methName.isEmpty()) {
            continue;
          }
          // extract the method name from the entry (the <init> method will also include the class)
          if (methName.startsWith(classSelect)) {
            methName = methName.substring(classSelect.length());
          }
          // ignore the return value - it's not part of the signature
          methName = methName.substring(0, methName.indexOf(")"));
          // TODO: for now, we just look for method name, not signature because javap uses
          // nomenclature like 'int' and 'long' instead of 'I' and 'J', making comparisons
          // a little tougher. This will be a problem for classes that hane multiple signatures
          // of the same class name.
          methName = methName.substring(0, methName.indexOf("("));
          if (methName.isEmpty()) { // if no name - it must be the <init> contructor method
            methName = "<init>";
          }
          // method entry found, let's see if it's the one we want
          if (methodSelect.startsWith(methName)) {
            methodLoaded = classSelect + "." + methodSelect;
            printBytecodeMethod(methodLoaded);
          }
        }
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
System.out.println("clearHighlighting: clear line " + line + " to " + bc.mark + " (removed bit " + type + ")");
      }
    }
  }

  private void highlightSetMark(int line, int type) {
    BytecodeInfo bc = bytecode.get(line);
    bc.mark |= type;
    bytecode.set(line, bc);
System.out.println("highlightSetMark: set line " + line + " to " + bc.mark);
  }
  
  private void highlightUpdate() {
    for (int line = 0; line < bytecode.size(); line++) {
      BytecodeInfo bc = bytecode.get(line);
      if (bc.mark != 0) {
        highlightBytecodeLine(line, bc.mark);
      }
    }
  }
  
  private void highlightUpdate(int type) {
    for (int line = 0; line < bytecode.size(); line++) {
      BytecodeInfo bc = bytecode.get(line);
      if ((bc.mark & type) != 0) {
        highlightBytecodeLine(line, type);
      }
    }
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
    highlightBytecode(bc.ixStart, bc.ixEnd, color);
    
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

      // get current length of text genereated for the starting offset
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
      bc.ixEnd    = panel.getText().length();
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
  
}
