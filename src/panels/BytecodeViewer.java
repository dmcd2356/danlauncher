/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import logging.FontInfo;
import logging.FontInfo.FontType;
import logging.FontInfo.TextColor;
import logging.Logger;
import main.LauncherMain;
import util.Utils;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 *
 * @author dan
 */
public final class BytecodeViewer {
  
  // these are the bitmask values for highlighting types
  private static final int HIGHLIGHT_CURSOR = 0x0001;
  private static final int HIGHLIGHT_BRANCH = 0x0002;
  private static final int HIGHLIGHT_ALL    = 0xFFFF;
  
  // This is how the switch opcode branch data gets saved in the comment
  private static final String SWITCH_DELIMITER = "//-- { ";

  // the number of lines that are placed in the bytecode text pane prior to the bytecode info
  // (this is used for converting from the user selected line number to the bytecode index value)
  private static final int LINES_PRECEEDING_BYTECODE = 2;
  
  // types of messages
  private static enum MsgType { ERROR, METHOD, TEXT, PARAM, COMMENT,
                                SYMBRA, BRANCH, SWITCH, GOTO,  INVOKE, RETURN, LOAD, STORE, OTHER }
  
  // states for parseJavap
  private static enum ParseMode { NONE, CLASS, METHOD, DESCRIPTION, OPCODE, OPSWITCH, LINENUM_TBL, LOCALVAR_TBL, EXCEPTION_TBL }
  
  // types of opcode instructions that we handle
  public static enum OpcodeType { SYMBRA, BRANCH, SWITCH, GOTO, INVOKE, RETURN, LOAD, STORE, OTHER }
  
  private static JTextPane       panel;
  private static Logger          logger;
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  private static HashMap<Integer, Integer> boff2Line = new HashMap<>(); // byte offset to line number reference
  private static String          methodLoaded = "";
  private static ParseMode       parseMode;
  private static boolean         valid;

  // these are needed by BytecodeGraph
  public static ArrayList<BytecodeInfo>  bytecode = new ArrayList<>();
  public static HashMap<Integer, String> exceptions = new HashMap<>();

  public BytecodeViewer(String name) {
    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.ERROR.toString(),   TextColor.Red,    FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.METHOD.toString(),  TextColor.Violet, FontType.Italic, 16, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.TEXT.toString(),    TextColor.Black,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.PARAM.toString(),   TextColor.Brown,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.COMMENT.toString(), TextColor.Green,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.SYMBRA.toString(),  TextColor.DkVio,  FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.INVOKE.toString(),  TextColor.Gold,   FontType.Bold  , 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.SWITCH.toString(),  TextColor.Blue,   FontType.Normal, 14, fonttype);

    FontInfo.setTypeColor (fontmap, MsgType.LOAD.toString(),    TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STORE.toString(),   TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.BRANCH.toString(),  TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.GOTO.toString(),    TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.RETURN.toString(),  TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.OTHER.toString(),   TextColor.Black,  FontType.Normal, 14, fonttype);
    
    // create the text panel and assign it to the logger
    logger = new Logger(name, Logger.PanelType.TEXTPANE, true, fontmap);
    panel = (JTextPane) logger.getTextPanel();
  
    // add mouse & key listeners for the bytecode viewer
    panel.addMouseListener(new BytecodeMouseListener());
    panel.addKeyListener(new BytecodeKeyListener());
    
    clear();
}
  
  public void clear() {
    parseMode = ParseMode.NONE;
    methodLoaded = "";
    valid = false;

    logger.clear();
    boff2Line.clear();
    bytecode.clear();
    exceptions.clear();
  }
  
  public JTextPane getTextPanel() {
    return panel;
  }
  
  public JScrollPane getScrollPanel() {
    return logger.getScrollPanel();
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

  public static Integer byteOffsetToLineNumber(int offset) {
    // check for exact match
    if (boff2Line.containsKey(offset)) {
      return boff2Line.get(offset);
    }
    
    Integer line = 0;
    for (BytecodeInfo bc : bytecode) {
      if (offset == bc.offset) {
        return line;
      }
      if (offset < bc.offset) {
        return line - 1;
      }
      ++line;
    }
    return null;
  }
  
  public void highlightClear() {
    LauncherMain.printCommandMessage("BytecodeLogger: highlightClear");
    panel.getHighlighter().removeAllHighlights();
    clearHighlighting(HIGHLIGHT_ALL);
  }

  public ArrayList<Integer> highlightBranch(int line, boolean branch) {
    ArrayList<Integer> branchMarks = new ArrayList<>();
    LauncherMain.printCommandMessage("BytecodeLogger: highlightBranch " + line + " (" + branch + ")");
    // check for error conditions
    if (bytecode.isEmpty()) {
      LauncherMain.printCommandError("ERROR: highlightBranch: no bytecode found");
      return null;
    }
    if (bytecode.size() < line) {
      LauncherMain.printCommandError("ERROR: highlightBranch: line " + line +
          " exceeds bytecode length " + bytecode.size());
      return null;
    }
    BytecodeInfo bc = bytecode.get(line);
    if (bc.optype != OpcodeType.SYMBRA) {
      LauncherMain.printCommandError("ERROR: highlightBranch: " + line +
          " not branch opcode: " + bc.opcode);
      return null;
    }

    // clear all previous branch entries and remove all highlighting
    clearHighlighting(HIGHLIGHT_BRANCH);

    // mark the the branch line
    highlightSetMark(line, HIGHLIGHT_BRANCH);
    branchMarks.add(bc.offset);
    
    // determine if we take the branch or not to determine the next line to highlight
    if (branch) {
      // the branch destination (branch taken)
      int branchLine = findBranchLine(bc.param);
      if (branchLine >= 0) {
        highlightSetMark(branchLine, HIGHLIGHT_BRANCH);
        try {
          int nextLine = Integer.parseUnsignedInt(bc.param);
          branchMarks.add(nextLine);
        } catch (NumberFormatException ex) {
          LauncherMain.printCommandError("ERROR: highlightBranch: " + line +
              " invalid branch location: " + bc.param);
        }
      }
    } else {
      highlightSetMark(line + 1, HIGHLIGHT_BRANCH); // the line following the branch opcode
      branchMarks.add(bytecode.get(line + 1).offset);
    }

    highlightUpdate();   // update the display
    return branchMarks;
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
    
    // check if method is already loaded
    if (methodLoaded.equals(classSelect + "." + methodSelect) && !bytecode.isEmpty()) {
      // make sure highlighting is desabled for the text, and we're good to exit
      LauncherMain.printCommandMessage(classSelect + "." + methodSelect + " is already loaded");
      highlightClear();
      valid = true;
      return;
    }

    // else we need to start with a clean slate
    clear();
    int offset = methodSelect.indexOf("(");
    String signature = methodSelect.substring(offset);
    methodSelect = methodSelect.substring(0, offset);
    String clsCurrent = classSelect; // the default if no class specified
    int linesRead = 0;
    String switchInProcess = "";
    int switchOffset = 0;
    boolean validLine = false;

    String[] lines = content.split(Utils.NEWLINE);
    for (String entry : lines) {
      ++curLine;
      entry = entry.replace("\t", " ").trim();

      // look for start of file - skip anything before this as it may be a remnant of a terminated run.
      if (!validLine) {
        if (entry.startsWith("Compiled from ")) {
          validLine = true;
        } else if (entry.startsWith("class ") && entry.endsWith("{")) {
          validLine = true;
        }
        continue;
      }
      
      // modify this entry - it indicates the static initializer method
      if (entry.equals("static {};")) {
        entry = "<clinit>()";
      }
      
      // skip past these keywords
      if (entry.startsWith("public ") || entry.startsWith("private ") || entry.startsWith("protected ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("static ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("final ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("synchronized ")) {
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
        case "Exception": //  table:
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
          // add them to the local variable table display
          try {
            String[] words = entry.split("\\s+");
            if (words.length >= 5) {
              int start = Integer.parseUnsignedInt(words[0]);
              int len   = Integer.parseUnsignedInt(words[1]);
              int slot  = Integer.parseUnsignedInt(words[2]);
              if (valid) {
                LauncherMain.addLocalVariable(words[3], words[4], words[2], words[0], "" + (start + len - 1));
              }
              continue;
            }
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
            // from    (int) = bytecode offset to start of scope for exception
            // to      (int) = bytecode offset to end of scope for exception
            // target  (int) = bytecode offset to start of exception handler
            // type (String) = the exception that causes the jump
            String[] words = entry.split("\\s+");
            if (words.length >= 4) {
              Integer target = Integer.parseUnsignedInt(words[2]);
              if (valid) {
                String  type = words[3];
                if (exceptions.containsKey(target)) {
                  String prev = exceptions.get(target);
                  if (!prev.equals(type)) {
                    exceptions.replace(target, "(multi)");
                  }
                } else {
                  if (type.equals("Class") && words.length >= 5) {
                    type = words[4];
                    offset = type.lastIndexOf("/");
                    if (offset > 0) {
                      type = type.substring(offset + 1);
                    }
                  }
                  exceptions.put(target, type);
                }
              }
            }
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
          if (!valid) {
            parseMode = ParseMode.NONE;
            break; // check for method
          }

          // else, verify entry beings with "<numeric>: " header
          offset = getOpcodeOffset(entry, lastoffset);
          if (offset >= 0) {
            // check for a SWITCH opcode (special handling for multi-line opcodes)
            if ((entry.contains("tableswitch") || entry.contains("lookupswitch"))) {
              // save the opcode info up to, but excluding the comment field
              switchInProcess = entry.substring(0, entry.indexOf("//")) + SWITCH_DELIMITER;
              switchOffset = offset;
              parseMode = ParseMode.OPSWITCH;
              LauncherMain.printCommandMessage("Line: " + curLine + " - SWITCH statement");
              continue;
            }

            // process the bytecode instruction (save info in array for reference & display text in panel)
            lastoffset = offset;
            entry = entry.substring(entry.indexOf(": ") + 1).trim();
            formatBytecode(linesRead, offset, entry);
            ++linesRead;
            continue;
          }
          break; // else, we probably completed the opcodes, so check for next method definition
        case OPSWITCH:
          // to eliminate having multi-line opcodes "tableswitch" and lookupswitch" we're going to
          // check if we are decoding the specified method and have found the start of one of these.
          // if so, we keep reading input and removing the NEWLINEs until we reach the end.
          // that way, we have all this info on a single line
          if (switchInProcess.isEmpty()) {
            break;
          }
          if (entry.startsWith("}")) {
            // this terminates the SWITCH mode
            if (switchInProcess.endsWith(", ")) {
              switchInProcess = switchInProcess.substring(0, switchInProcess.length() - 2);
            }
            entry = switchInProcess + " }";
            // now we can process the opcode (since offset was already validated, this will always pass)
            lastoffset = switchOffset;
            entry = entry.substring(entry.indexOf(": ") + 1).trim();
            formatBytecode(linesRead, switchOffset, entry);
            ++linesRead;
            parseMode = ParseMode.OPCODE; // switch back to normal opcode handling
          } else {
            // this is the next switch line that holds the selection followed by the branch
            // location to jump to. add the entry in a comma-separated list.
            switchInProcess += entry + ", ";
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

      // now remove the argument list, so the string will end with the method name and
      // seperate the remaining words. This should consist of one of the forllowing formats:
      // <className>                      - <init> method for specified class
      // <retType> <methName>             - class is inferred from last Class: definition
      // <className> <methName>           - static class is not same as last Class: definition
      // <retType> <className> <methName> - class is not same as last Class: definition
      entry = entry.substring(0, offset);
      String[] words = entry.split(" ");
      String clsName = "";
      String methName = "";
      switch (words.length) {
        case 1: // <init> method for specified class
          clsName = words[0].trim();
          methName = "<init>";
          break;
        case 2: // a method from the given class
          clsName = words[0].trim(); // assume the 1st word is the class
          if (!classSelect.equals(clsName)) {
            clsName = clsCurrent;   // if it isn't, try the last Class definitionn
          }
          methName = words[1].trim();
          break;
        case 3: // a method for an inner class
          clsName = words[1].trim();
          methName = words[2].trim();
          break;
        default:
          LauncherMain.printCommandError("ERROR: invalid list for method: " + entry);
          break;
      }
      LauncherMain.printCommandMessage("Line: " + curLine + " Type: " + words.length +
              " - Method: " + clsName + "." + methName);

      // entry has paranthesis, must be a method name
      parseMode = ParseMode.METHOD;
          
      // method entry found, let's see if it's the one we want
      if (methodSelect.equals(methName) && classSelect.equals(clsName)) {
        methodLoaded = classSelect + "." + methodSelect + signature;
        LauncherMain.printCommandMessage("Line: " + curLine + " - Method match");
      } else if (valid) {
        // exit parsing when we hit the method following the correct one
        break;
      }
    }

    LauncherMain.printCommandMessage("Line: " + curLine + " - opcode lines read: " + linesRead);
  }

  public class BytecodeInfo {
    public int     line;        // line number in method (assumes 1 opcode per line)
    public int     offset;      // byte offset of entry within the method
    public String  opcode;      // opcode
    public String  param;       // param entry(s)
    public String  comment;     // comment
    public OpcodeType optype;   // the type of opcode
    public int     ixStart;     // char offset in text of start of line
    public int     ixEnd;       // char offset in text of end of line
    public int     mark;        // highlight bits
    public String  callMeth;    // for INVOKEs, the method being called
    public HashMap<String, Integer> switchinfo; // for SWITCHs
    
    public BytecodeInfo() {
      switchinfo = new HashMap<>();
    }
  }
  
  private static void printBytecodeMethod(String methodName) {
    logger.printField("TEXT"  , "Method: ");
    logger.printField("METHOD", methodName + Utils.NEWLINE + Utils.NEWLINE);
  }
  
  private static void printBytecodeOpcode(int boffset, String opcode, OpcodeType optype, String param, String comment) {
    logger.printFieldAlignRight("TEXT", "" + boffset, 5);
    logger.printField          ("TEXT", ":  ");
    logger.printFieldAlignLeft (optype.toString(), opcode, 19);
    logger.printFieldAlignLeft ("PARAM", param, 10);
    logger.printField          ("COMMENT", comment + Utils.NEWLINE);
  }
  
  private static OpcodeType getOpcodeType(String opcode) {
    // determine the opcode type
    OpcodeType type = OpcodeType.OTHER; // the default type
    switch (opcode.toUpperCase()) {
      case "IF_ICMPEQ":
      case "IF_ICMPNE":
      case "IF_ICMPGT":
      case "IF_ICMPLE":
      case "IF_ICMPLT":
      case "IF_ICMPGE":
      case "IFEQ":
      case "IFNE":
      case "IFGT":
      case "IFLE":
      case "IFLT":
      case "IFGE":
        // NOTE: these branch instructions check for symbolic values
        type = OpcodeType.SYMBRA; // Symbollic branches
        break;
      
      case "IF_ACMPEQ":
      case "IF_ACMPNE":
      case "IFNULL":
      case "IFNONNULL":
        // NOTE: these do NOT check for symbolic types
        type = OpcodeType.BRANCH; // Other branches
        break;
      
      case "TABLESWITCH":
      case "LOOKUPSWITCH":
        // NOTE: these do NOT check for symbolic types
        type = OpcodeType.SWITCH; // switch instructions that may take several different paths
        break;
      
      case "GOTO":
        type = OpcodeType.GOTO; // a simple non-conditional redirect to another byte offset
        break;
      
      case "INVOKESTATIC":
      case "INVOKEVIRTUAL":
      case "INVOKESPECIAL":
      case "INVOKEINTERFACE":
      case "INVOKEDYNAMIC":
        type = OpcodeType.INVOKE; // method calls
        break;
      
      case "ALOAD":
      case "ALOAD_0":
      case "ALOAD_1":
      case "ALOAD_2":
      case "ALOAD_3":
      case "ILOAD":
      case "ILOAD_0":
      case "ILOAD_1":
      case "ILOAD_2":
      case "ILOAD_3":
      case "LLOAD":
      case "LLOAD_0":
      case "LLOAD_1":
      case "LLOAD_2":
      case "LLOAD_3":
      case "FLOAD":
      case "FLOAD_0":
      case "FLOAD_1":
      case "FLOAD_2":
      case "FLOAD_3":
      case "DLOAD":
      case "DLOAD_0":
      case "DLOAD_1":
      case "DLOAD_2":
      case "DLOAD_3":
//      case "AALOAD:"
//      case "CALOAD:"
//      case "SALOAD:"
//      case "IALOAD:"
//      case "LALOAD:"
//      case "FALOAD:"
//      case "DALOAD:"
        type = OpcodeType.LOAD; // load local parameter
        break;
      
      case "ASTORE":
      case "ASTORE_0":
      case "ASTORE_1":
      case "ASTORE_2":
      case "ASTORE_3":
      case "ISTORE":
      case "ISTORE_0":
      case "ISTORE_1":
      case "ISTORE_2":
      case "ISTORE_3":
      case "LSTORE":
      case "LSTORE_0":
      case "LSTORE_1":
      case "LSTORE_2":
      case "LSTORE_3":
      case "FSTORE":
      case "FSTORE_0":
      case "FSTORE_1":
      case "FSTORE_2":
      case "FSTORE_3":
      case "DSTORE":
      case "DSTORE_0":
      case "DSTORE_1":
      case "DSTORE_2":
      case "DSTORE_3":
//      case "AASTORE:"
//      case "CASTORE:"
//      case "SASTORE:"
//      case "IASTORE:"
//      case "LASTORE:"
//      case "FASTORE:"
//      case "DASTORE:"
        type = OpcodeType.STORE; // store local parameter
        break;
      
      case "IRETURN":
      case "LRETURN":
      case "FRETURN":
      case "DRETURN":
      case "ARETURN":
      case "RETURN":
        type = OpcodeType.RETURN; // return to previous stack frame
        break;
      default:
        break;
    }
    return type;
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
        LauncherMain.printStatusMessage("Bytecode byte offset " + bc.offset + " = line " + line);
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
      LauncherMain.printCommandError("ERROR: findBranchLine: invalid branch location value: " + param);
      return -1;
    }
    
    // search for offset entry in array
    for (int ix = 0; ix < bytecode.size(); ix++) {
      if (offset == bytecode.get(ix).offset) {
        return ix;
      }
    }

    LauncherMain.printCommandError("ERROR: findBranchLine: branch location value not found in method: " + param);
    return -1;
  }
  
  /**
   * outputs the bytecode message to the status display.
   * 
   * @param lineCount - line offset in method (actually the opcode index in the method, starting at 0)
   * @param boffset - byte offset of the instruction
   * @param entry  - the line read from javap
   */
  private void formatBytecode(int lineCount, int boffset, String entry) {
    if (entry == null || entry.isEmpty()) {
      return;
    }
    
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
      
    // check special case of tableswitch and lookupswitch - these are multiline cases
    String switchList = "";
    if (comment.startsWith(SWITCH_DELIMITER)) {
      switchList = comment.substring(SWITCH_DELIMITER.length()).trim(); // remove the delimiter
      offset = switchList.lastIndexOf("}");   // remove trailing end brace
      if (offset > 0) {
        switchList = switchList.substring(0, offset).trim();
      }
    }

    // get current length of text generated for the starting offset
    int startix = panel.getText().length();

    // get the opcode type
    OpcodeType optype = getOpcodeType(opcode);
      
    // if opcode is INVOKE, get the method being called (from the comment section)
    boolean bIsInstr = false;
    String callMethod = "";
    if (optype == OpcodeType.INVOKE) {
      offset = comment.indexOf("Method ");
      if (offset > 0) {
        callMethod = comment.substring(offset + "Method ".length()).trim();
        callMethod = callMethod.replaceAll("\"", ""); // remove any quotes placed around the <init>
        offset = callMethod.indexOf(":");
        if (offset > 0) {
          callMethod = callMethod.substring(0, offset) + callMethod.substring(offset + 1);
        }
          
        // check if method call is instrumented
        bIsInstr = LauncherMain.isInstrumentedMethod(callMethod);
      }

      if (!bIsInstr) {
        optype = OpcodeType.OTHER; // only count the calls to instrumented code
      }
    }
      
    // add the line to the text display
    printBytecodeOpcode(boffset, opcode, optype, param, comment);
    
    // add entry to array
    BytecodeInfo bc = new BytecodeInfo();
    bc.line     = lineCount;
    bc.offset   = boffset;
    bc.opcode   = opcode;
    bc.param    = param;
    bc.comment  = comment;
    bc.optype   = optype;
    bc.ixStart  = startix;
    bc.ixEnd    = panel.getText().length() - 1;
    bc.mark     = 0;
    bc.callMeth = callMethod;

    // for switch statements, let's gather up the conditions and the corresponding branch locations
    bc.switchinfo = new HashMap<>();
    String[] entries = switchList.split(",");
    for (String switchval : entries) {
      offset = switchval.indexOf(":");
      if (offset > 0) {
        String key = switchval.substring(0, offset).trim();
        String val = switchval.substring(offset + 1).trim(); // the branch location
        try {
          Integer branchpt = Integer.parseUnsignedInt(val);
          bc.switchinfo.put(key, branchpt);
        } catch (NumberFormatException ex) {
          LauncherMain.printCommandError("ERROR: invalid branch location on line " + lineCount + ": " + val);
        }
      }
    }

    // now make a reference of the byte offset to the line number and from the line number
    // to the bytecode array index, then add the entry to the array
    boff2Line.put(boffset, lineCount);
    bytecode.add(bc);
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
      //LauncherMain.printCommandMessage("BytecodeViewer: cursor = " + curpos);
      
      // now determine line number and offset within the line for the caret
      int line = 0;
      int offset = 0;
      int curoffset = curpos;
      for (int ix = 0; ix < curpos; ix++) {
        if (contents.charAt(ix) == '\n' || contents.charAt(ix) == '\r') {
          ++line;
          offset = ix;
          curoffset -= offset;
        }
      }
      // cursor position within line = curoffset - 1;
      
      // highlight the current line selection
      highlightCursorPosition(curpos);

      line -= LINES_PRECEEDING_BYTECODE; // subtract off the lines preceeding the bytecode info
      if (line >= 0) {
        BytecodeInfo bc = bytecode.get(line);
        //LauncherMain.printCommandMessage("BytecodeViewer: line " + line + " Opcode: " +
        //    bc.opcode + ", type = " + bc.optype.toString());
        if (bc.optype == OpcodeType.LOAD || bc.optype == OpcodeType.STORE) {
          // user selected a line containing a load/store to a local parameter.
          // First, determine which parameter is selected
          String dtype = bc.opcode.substring(0, 1).toUpperCase();
          String paramStr = bc.param; // the case of a parameter being passed for the value
          int index = bc.opcode.indexOf("_");
          if (index > 0) {
            paramStr = bc.opcode.substring(index + 1);
          }

          int paramNum;
          try {
            paramNum = Integer.parseUnsignedInt(paramStr); // should be an integer
          } catch (NumberFormatException ex) {
            LauncherMain.printStatusError("Missing slot selection for parameter!");
            return;
          }
          // Next, attempt to find it in the local arg list
          ParamTable.LocalParamInfo entry = ParamTable.getParam(paramNum, bc.offset);
          if (entry == null) {
            // not found - use default values (user can change them in SymbolTbl)
            entry = new ParamTable.LocalParamInfo();
            entry.name = "";
            entry.type = dtype.equals("A") ? "Ljava/lang/Object;" : dtype;
            entry.start = 0;
            entry.end = 0;
            entry.slot = paramNum;
          }

          // Then, ask user if we wants to add this parameter to the symbolic parameter list.
          String[] selection = {"Yes", "No" };
          int which = JOptionPane.showOptionDialog(null,
            "Do you wish to add" + Utils.NEWLINE + Utils.NEWLINE +
            "Method: " + methodLoaded + Utils.NEWLINE +
            "Param : " + paramNum + Utils.NEWLINE +
            "Name  : " + entry.name + Utils.NEWLINE +
            "Type  : " + entry.type + Utils.NEWLINE +
            "Start : " + entry.start + Utils.NEWLINE +
            "End   : " + entry.end + Utils.NEWLINE + Utils.NEWLINE +
            "to the symbolic parameter list?",
            "Add symbolic parameter", // title of pane
            JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
            null, // icon
            selection, selection[1]);

          if (which >= 0 && selection[which].equals("Yes")) {
            // get the corresponding opcode line offset values for the given byte offsets
            int opstrt = byteOffsetToLineNumber(entry.start);
            int oplast = byteOffsetToLineNumber(entry.end);
            
            // add the entry to the current param list
            String name = LauncherMain.addSymbVariable(methodLoaded, entry.name, entry.type,
                paramNum + "", entry.start + "", entry.end + "", opstrt, oplast);
            if (name == null) {
              LauncherMain.printStatusError("This symbolic value already exists");
            } else {
              // now update the danfig file
              LauncherMain.updateDanfigFile();
            }
          }
        }
      }
    }
  }
  
  private class BytecodeKeyListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent ke) {
      // when the key is initially pressed
      //LauncherMain.printCommandMessage("BytecodeViewer: keyPressed: " + ke.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent ke) {
      // follows keyPressed and preceeds keyReleased when entered key is character type
      //LauncherMain.printCommandMessage("BytecodeViewer: keyTyped: " + ke.getKeyCode() + " = '" + ke.getKeyChar() + "'");
    }

    @Override
    public void keyReleased(KeyEvent ke) {
      // when the key has been released
      //LauncherMain.printCommandMessage("BytecodeViewer: keyReleased: " + ke.getKeyCode());
      int curpos = panel.getCaretPosition();
      switch (ke.getKeyCode()) {
        case KeyEvent.VK_UP:    // UP ARROW key
          //LauncherMain.printCommandMessage("BytecodeViewer: UP key: at cursor " + curpos);
          highlightCursorPosition(curpos);
          break;
        case KeyEvent.VK_DOWN:  // DOWN ARROW key
          //LauncherMain.printCommandMessage("BytecodeViewer: DN key: at cursor " + curpos);
          highlightCursorPosition(curpos);
          break;
        default:
          break;
      }
    }

  }
  
}
