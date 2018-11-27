/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import callgraph.CallGraph;
import callgraph.MethodInfo;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JTextPane;
import logging.FontInfo;
import logging.FontInfo.FontType;
import logging.FontInfo.TextColor;
import logging.Logger;
import main.LauncherMain;
import util.Utils;

/**
 *
 * @author dan
 */
public class DebugLogger {

  // types of messages
  private enum MsgType { TSTAMP, NORMAL, INFO, WARN, ERROR, DUMP, START, ENTRY, AGENT, THREAD,
        CALL, RETURN, UNINST, STATS, STACK, STACKS, STACKI, LOCAL, LOCALS, SOLVE, BRANCH }

  private static JTextPane       panel;
  private static Logger          logger;
  private static String          tabName;
  private static int             curLine;
  private static int             endLine;
  private static int             linesRead;
  private static int             threadCount;
  private static int             errorCount;
  private static boolean         paused;
  private static boolean         tabSelected;
  private static final ArrayList<Integer> threadList = new ArrayList<>();
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  private static LinkedBlockingQueue<String> pauseQueue;
  
  public DebugLogger(String name) {
    curLine = 0;
    endLine = 0;
    linesRead = 0;
    errorCount = 0;
    threadCount = 0;
    paused = false;
    tabSelected = false;
    tabName = name;

    pauseQueue = new LinkedBlockingQueue<>();
    
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
    FontInfo.setTypeColor (fontmap, MsgType.BRANCH.toString(), TextColor.DkVio,  FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.SOLVE.toString(),  TextColor.DkVio,  FontType.Bold,   14, fonttype);

    // create the text panel and assign it to the logger
    panel = new JTextPane();
    logger = new Logger((Component) panel, name, fontmap);

    // add key listener for the debug viewer
    panel.addKeyListener(new DebugKeyListener());
  }
  
  public JTextPane getPanel() {
    return panel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public int getThreadCount() {
    return threadCount;
  }
  
  public void setMaxBufferSize(int bufSize) {
    logger.setMaxBufferSize(bufSize);
  }
  
  public void clear() {
    logger.clear();
    curLine = 0;
    endLine = 0;
    linesRead = 0;
    errorCount = 0;
    threadCount = 0;
    paused = false;
    pauseQueue.clear();
  }

  private void addToQueue(String message) {
    try {
      // add message to queue. if limit exceeded, remove oldest entry
      pauseQueue.put(message);
      if (pauseQueue.size() > 50) {
        pauseQueue.take();
      }
    } catch (InterruptedException ex) { /* ignore */ }
  }
  
  private void processFromQueue() {
    while (!pauseQueue.isEmpty()) {
      try {
        // read next message from input buffer
        String message = pauseQueue.take();
        MessageInfo msgInfo = new MessageInfo(message);
        if (msgInfo.valid) {
          printDebug(msgInfo.linenum, msgInfo.timestr, msgInfo.threadid, msgInfo.type, msgInfo.content);
        } else {
          printDebug(message);
        }
        curLine++;

      } catch (InterruptedException ex) { /* ignore */ }
    }
  }
  public int processMessage(String message, CallGraph callGraph) {
    if (message == null) {
      return callGraph.getMethodCount();
    }

    // seperate message into the message type and the message content
    MessageInfo msgInfo = new MessageInfo(message);
    linesRead++;
    if (paused) {
      addToQueue(message);
    } else if (msgInfo.valid) {
      printDebug(msgInfo.linenum, msgInfo.timestr, msgInfo.threadid, msgInfo.type, msgInfo.content);
      curLine++;
    } else {
      printDebug(message);
      curLine++;
      return callGraph.getMethodCount();
    }
          
    // check if we have thread id value embedded in message contents
    if (!threadList.contains(msgInfo.tid)) {
      threadList.add(msgInfo.tid);
      threadCount = threadList.size();
    }
    // enable the thread highlighting controls if we have more than 1 thread
    if (threadCount > 1) {
      LauncherMain.setThreadEnabled(true);
    }

    // get the current method that is being executed
    MethodInfo mthNode = callGraph.getLastMethod(msgInfo.tid);
    
    // extract call processing info and send to CallGraph
    String content = msgInfo.content.trim();
    switch (msgInfo.type.trim()) {
      case "CALL":
        String[] splited = content.split(" ");
        if (splited.length < 2) {
          printDebug("invalid syntax for CALL command");
          LauncherMain.printCommandError("ERROR: invalid CALL message on line " + msgInfo.linenum);
          return callGraph.getMethodCount(); // invalid syntax - ignore
        }

        String icount = splited[0].trim();
        String method = splited[1].trim();
        callGraph.methodEnter(msgInfo.tid, msgInfo.tstamp, icount, method, msgInfo.linenum);
        break;
      case "RETURN":
        callGraph.methodExit(msgInfo.tid, msgInfo.tstamp, content);
        break;
      case "ENTRY":
        if (content.startsWith("catchException")) {
          if (mthNode != null) {
            mthNode.setExecption(msgInfo.tid, msgInfo.linenum);
          }
        }
        break;
      case "ERROR":
        // increment the error count
        errorCount++;
        if (mthNode != null) {
          mthNode.setError(msgInfo.tid, msgInfo.linenum);
        }
        break;
      default:
        break;
    }
    
    return callGraph.getMethodCount();
  }
  
  private static void printDebug(String content) {
    logger.printLine(content);
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
      if (!content.contains(Utils.NEWLINE)) {
        logger.printField("INFO", linestr + "  ");
        logger.printField("INFO", elapsed + " ");
        logger.printField("INFO", threadid + " ");
        logger.printField(typestr, typestr + ": " + content + Utils.NEWLINE);
      }
      else {
        // seperate into lines and print each independantly
        String[] msgarray = content.split(Utils.NEWLINE);
        for (String msg : msgarray) {
          logger.printField("INFO", linestr + "  ");
          logger.printField("INFO", elapsed + " ");
          logger.printField("INFO", threadid + " ");
          logger.printField(typestr, typestr + ": " + msg + Utils.NEWLINE);
        }
      }
    }
  }
  
  public class MessageInfo {
    public boolean valid = false;
    public int    linenum = 0;
    public long   tstamp = 0L;
    public int    tid = -1;

    public String lineval = "";
    public String timestr = "";
    public String threadid = "";
    public String type = "";
    public String content = "";
    
    public MessageInfo(String message) {
      if (message != null && message.length() >= 30) {
        String[] array = message.split("\\s+", 3);
        if (array.length < 3) {
          return;
        }

        lineval = array[0];
        timestr = array[1];
        message = array[2];
        // check if we have thread id value embedded in message contents
        if (message.startsWith("<") && message.contains(">")) {
          array = message.split("\\s+", 2);
          threadid = array[0];
          message = array[1];
          threadid = threadid.substring(1, threadid.indexOf(">"));
        }

        // make sure we have a valid time stamp & the message length is valid
        // timestamp = [00:00.000] (followed by a space)
        if (timestr.charAt(0) != '[' || timestr.charAt(10) != ']') {
          return;
        }
        String timeMin = timestr.substring(1, 3);
        String timeSec = timestr.substring(4, 6);
        String timeMs  = timestr.substring(7, 10);

        // verify numeric entries
        try {
          linenum = Integer.parseInt(lineval);
          tstamp = ((Integer.parseInt(timeMin) * 60) + Integer.parseInt(timeSec)) * 1000;
          tstamp += Integer.parseInt(timeMs);
          if (!threadid.isEmpty()) {
            tid = Integer.parseInt(threadid);
          }
        } catch (NumberFormatException ex) {
          return;
        }

        type = message.substring(0, 6).toUpperCase(); // 6-char message type (may contain space)
        content = message.substring(8);     // message content to display
        valid = true;
      }
    }
  }
  
  
  private class DebugKeyListener implements KeyListener {

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
          case KeyEvent.VK_UP:
            if (!paused) {
              endLine = curLine;
            }
            paused = true;
            curLine = curLine - 1;
            if (curLine < 0) {
              curLine = 0;
            }
            break;
          case KeyEvent.VK_DOWN:
            curLine += 1;
            if (curLine >= endLine) {
              curLine = endLine;
              processFromQueue();
              paused = false;
            }
            break;
          case KeyEvent.VK_PAGE_UP:
            if (!paused) {
              endLine = curLine;
            }
            paused = true;
            curLine = curLine - 20;
            if (curLine < 0) {
              curLine = 0;
            }
            break;
          case KeyEvent.VK_PAGE_DOWN:
            curLine += 20;
            if (curLine >= endLine) {
              curLine = endLine;
              processFromQueue();
              paused = false;
            }
            break;
          default:
            break;
        }
      }
    }
  }

}
