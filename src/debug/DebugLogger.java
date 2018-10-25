/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import callgraph.CallGraph;
import callgraph.MethodInfo;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JTextPane;
import logging.FontInfo;
import logging.FontInfo.FontType;
import logging.FontInfo.TextColor;
import logging.Logger;
import main.GuiPanel;

/**
 *
 * @author dan
 */
public class DebugLogger {

  private static final String NEWLINE = System.getProperty("line.separator");

  // types of messages
  private enum MsgType { TSTAMP, NORMAL, INFO, WARN, ERROR, DUMP, START, ENTRY, AGENT, THREAD,
        CALL, RETURN, UNINST, STATS, STACK, STACKS, STACKI, LOCAL, LOCALS, SOLVE, PATH }

  private static JTextPane       panel;
  private static Logger          logger;
  private static int             linesRead;
  private static int             threadCount;
  private static int             errorCount;
  private static final ArrayList<Integer> threadList = new ArrayList<>();
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  
  public DebugLogger(String name) {
    errorCount = 0;
    threadCount = 0;

    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.TSTAMP.toString(), TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.ERROR.toString(),  TextColor.Red,    FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.WARN.toString(),   TextColor.Orange, FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.INFO.toString(),   TextColor.Black,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.NORMAL.toString(), TextColor.Black,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.DUMP.toString(),   TextColor.Orange, FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.START.toString(),  TextColor.Black,  FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.ENTRY.toString(),  TextColor.Brown,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.AGENT.toString(),  TextColor.Violet, FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.THREAD.toString(), TextColor.DkVio,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.CALL.toString(),   TextColor.Gold,   FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.RETURN.toString(), TextColor.Gold,   FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.UNINST.toString(), TextColor.Gold,   FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STATS.toString(),  TextColor.Gold,   FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACK.toString(),  TextColor.Blue,   FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACKS.toString(), TextColor.Blue,   FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACKI.toString(), TextColor.Blue,   FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.LOCAL.toString(),  TextColor.Green,  FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.LOCALS.toString(), TextColor.Green,  FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.PATH.toString(),   TextColor.DkVio,  FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.SOLVE.toString(),  TextColor.DkVio,  FontType.Bold,   14, fonttype);

    // create the text panel and assign it to the logger
    panel = new JTextPane();
    logger = new Logger((Component) panel, name, fontmap);
  }
  
  public JTextPane getTextPane() {
    return panel;
  }
  
  public void setMaxBufferSize(int bufSize) {
    logger.setMaxBufferSize(bufSize);
  }
  
  public void clear() {
    logger.clear();
  }
  
  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param linenum  - the line number
   * @param elapsed  - the elapsed time
   * @param threadid - the thread id
   * @param typestr  - the type of message to display (all caps)
   * @param content  - the message content
   */
  private static void printDebug(int linenum, String elapsed, String threadid, String typestr, String content) {
    if (linenum >= 0 && elapsed != null && typestr != null && content != null && !content.isEmpty()) {
      // make sure the linenum is 8-digits in length and the type is 6-chars in length
      String linestr = "00000000" + linenum;
      linestr = linestr.substring(linestr.length() - 8);
      typestr = (typestr + "      ").substring(0, 6);
      if (!threadid.isEmpty()) {
        threadid = "<" + threadid + ">";
      }
      
      // print message (seperate into multiple lines if ASCII newlines are contained in it)
      if (!content.contains(NEWLINE)) {
        logger.printField("INFO", linestr + "  ");
        logger.printField("INFO", elapsed + " ");
        logger.printField("INFO", threadid + " ");
        logger.printField(typestr, typestr + ": " + content + NEWLINE);
      }
      else {
        // seperate into lines and print each independantly
        String[] msgarray = content.split(NEWLINE);
        for (String msg : msgarray) {
          logger.printField("INFO", linestr + "  ");
          logger.printField("INFO", elapsed + " ");
          logger.printField("INFO", threadid + " ");
          logger.printField(typestr, typestr + ": " + msg + NEWLINE);
        }
      }
    }
  }

  private static void printDebug(String content) {
    logger.printLine(content);
  }
  
  public static boolean processMessage(String message) {
    // seperate message into the message type and the message content
    if (message == null) {
      return false;
    }
    if (message.length() < 30) {
      printDebug(message);
      return false;
    }

    // read the specific entries from the message
    String[] array = message.split("\\s+", 3);
    if (array.length < 3) {
      printDebug(message);
      return false;
    }
    String linenum = array[0];
    String timestr = array[1];
    message = array[2];
    int tid = -1; // this is an indication that no thread info was found in the message
    String threadid = "";
    if (message.startsWith("<") && message.contains(">")) {
      array = message.split("\\s+", 2);
      message = array[1];
      threadid = array[0];
      threadid = threadid.substring(1, threadid.indexOf(">"));
      tid = Integer.parseInt(threadid);
      if (!threadList.contains(tid)) {
        threadList.add(tid);
        threadCount = threadList.size();
      }
      // enable the thread highlighting controls if we have more than 1 thread
      if (threadCount > 1) {
        GuiPanel.setThreadEnabled(true);
      }
    }
    String typestr = message.substring(0, 6).toUpperCase(); // 6-char message type (may contain space)
    String content = message.substring(8);     // message content to display

    // make sure we have a valid time stamp & the message length is valid
    // timestamp = [00:00.000] (followed by a space)
    if (timestr.charAt(0) != '[' || timestr.charAt(10) != ']') {
      printDebug(message);
      return false;
    }
    String timeMin = timestr.substring(1, 3);
    String timeSec = timestr.substring(4, 6);
    String timeMs  = timestr.substring(7, 10);
    int  linecount = 0;
    long tstamp = 0;
    try {
      linecount = Integer.parseInt(linenum);
      tstamp = ((Integer.parseInt(timeMin) * 60) + Integer.parseInt(timeSec)) * 1000;
      tstamp += Integer.parseInt(timeMs);
    } catch (NumberFormatException ex) {
      // invalid syntax - skip
      printDebug(message);
      return false;
    }

    // if we are set to reset when a new session starts and one just started, do a reset (duh!)
    if (typestr.trim().equals("START")) {
      System.out.println("START DETECTED...");
    }
    if (typestr.trim().equals("START")) {
//    if (typestr.trim().equals("START") && GuiPanel.mainFrame.getCheckbox("BTN_AUTORESET").isSelected()) {
      System.out.println("RESET PERFORMED...");
      GuiPanel.resetLoggedTime();
      GuiPanel.resetCapturedInput();  // this clears the displayed data and stats
//      logger.clear();            // clear the text panel
    }
    else if (GuiPanel.isElapsedModeReset() && linecount == 0) {
      // else if we detect the start of a new debug session, restart our elapsed timer
      GuiPanel.resetLoggedTime();
    }

    // send message to the debug display
    printDebug(linecount, timestr, threadid, typestr, content);
          
    linesRead++;
//    (GuiPanel.mainFrame.getTextField("TXT_PROCESSED")).setText("" + GuiPanel.linesRead);

    // get the current method that is being executed
    MethodInfo mthNode = CallGraph.getLastMethod(tid);
    
    // extract call processing info and send to CallGraph
    content = content.trim();
    switch (typestr.trim()) {
      case "CALL":
        String[] splited = content.split(" ");
        if (splited.length < 2) {
          printDebug("invalid syntax for CALL command");
          System.out.println("ERROR: invalid CALL message on line " + linecount);
          return false; // invalid syntax - ignore
        }

        String icount = splited[0].trim();
        String method = splited[1].trim();
        CallGraph.methodEnter(tid, tstamp, icount, method, linecount);
        break;
      case "RETURN":
        CallGraph.methodExit(tid, tstamp, content);
        break;
      case "ENTRY":
        if (content.startsWith("catchException")) {
          if (mthNode != null) {
            mthNode.setExecption(tid, linecount);
          }
        }
        break;
      case "ERROR":
        // increment the error count
        errorCount++;
        if (mthNode != null) {
          mthNode.setError(tid, linecount);
        }
        break;
      default:
        break;
    }
    
    return (linecount == 0);
  }

}
