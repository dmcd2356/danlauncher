/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import debug.FontInfo.FontType;
import debug.FontInfo.TextColor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 *
 * @author dan
 */
public class Logger {
    
  // these are used for limiting the amount of text displayed in the logger display to limit
  // memory use. MAX_TEXT_BUFFER_SIZE defines the upper memory usage when the reduction takes
  // place and REDUCE_BUFFER_SIZE is the minimum number of bytes to reduce it by (it will look
  // for the next NEWLINE char).
  private final static int MAX_TEXT_BUFFER_SIZE = 150000;
  private final static int REDUCE_BUFFER_SIZE   = 50000;

  private final static String MAX_PADDING = "                    ";
  
  // the default point size and font types to use
  private static final int    DEFAULT_POINT = 14;
  private static final String DEFAULT_FONT = "Courier";
  private static final String NEWLINE = System.getProperty("line.separator");

  private static String   pnlname;
  private JTextPane       textPane = null;
  private JTextArea       textArea = null;
  private static final HashMap<String, FontInfo> messageTypeTbl = new HashMap<>();

  public Logger (Component pane, String name, HashMap<String, FontInfo> map) {
    pnlname = name;
    if (pane instanceof JTextPane) {
      textPane = (JTextPane) pane;
    } else if (pane instanceof JTextArea) {
      textArea = (JTextArea) pane;
      textArea.setWrapStyleWord(true);
      textArea.setAutoscrolls(true);
    } else {
      System.err.println("ERROR: Invalid component type for Logger!");
      System.exit(1);
    }

    // copy the font mapping info over (use deep-copy loop instead of shallow-copy putAll)
    if (map != null) {
      for (Map.Entry<String, FontInfo> entry : map.entrySet()) {
        messageTypeTbl.put(entry.getKey(), entry.getValue());
      }
    }
  }

  public final String getName() {
    return pnlname;
  }

  /**
   * clears the display.
   */
  public final void clear() {
    if (textPane != null) {
      textPane.setText("");
    } else {
      textArea.setText("");
    }
  }

  /**
   * updates the display immediately
   */
  public final void updateDisplay () {
    if (textPane != null) {
      Graphics graphics = textPane.getGraphics();
      if (graphics != null) {
        textPane.update(graphics);
      }
    }
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
  public final void print(int linenum, String elapsed, String threadid, String typestr, String content) {
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
        printRaw("INFO", linestr + "  ");
        printRaw("INFO", elapsed + " ");
        printRaw("INFO", threadid + " ");
        printRaw(typestr, typestr + ": " + content + NEWLINE);
      }
      else {
        // seperate into lines and print each independantly
        String[] msgarray = content.split(NEWLINE);
        for (String msg : msgarray) {
          printRaw("INFO", linestr + "  ");
          printRaw("INFO", elapsed + " ");
          printRaw("INFO", threadid + " ");
          printRaw(typestr, typestr + ": " + msg + NEWLINE);
        }
      }
    }
  }

  /**
   * adds ASCII space padding to right of string up to the specified length
   * 
   * @param content  - the message content
   */
  private String padToRight(String content, int length) {
    return (content + MAX_PADDING).substring(0, length);
  }
  
  /**
   * adds ASCII space padding to left of string up to the specified length
   * 
   * @param content  - the message content
   */
  private String padToLeft(String content, int length) {
    return (MAX_PADDING + content).substring(MAX_PADDING.length() + content.length() - length);
  }
  
  public final void printFormatted(String type, String message) {
    printRaw(type, message + NEWLINE);
  }
  
  public final void printUnformatted(String message) {
    printRaw("NOFMT", message + NEWLINE);
  }
  
  public final void printRawAlignLeft(String type, String message, int fieldlen) {
    printRaw(type, padToRight(message, fieldlen));
  }
  
  public final void printRawAlignRight(String type, String message, int fieldlen) {
    printRaw(type, padToLeft(message, fieldlen));
  }
  
  /**
   * displays a message in the debug window (no termination).
   * 
   * @param type  - the type of message to display
   * @param message - message contents to display
   */
  public final void printRaw(String type, String message) {
    if (message != null && !message.isEmpty()) {
      // set default values (if type was not found)
      TextColor color = TextColor.DkGrey; //TextColor.Black;
      FontType ftype = FontType.Italic; //FontType.Normal;
      int size = DEFAULT_POINT;
      String font = DEFAULT_FONT;

      // get the color and font for the specified type
      FontInfo fontinfo = messageTypeTbl.get(type.trim());
      if (fontinfo != null) {
        color = fontinfo.color;
        ftype = fontinfo.fonttype;
        font  = fontinfo.font;
        size  = fontinfo.size;
      }

      appendToPane(message, color, font, size, ftype);
    }
  }

  /**
   * A generic function for appending formatted text to a JTextPane.
   * 
   * @param tp    - the TextPane to append to
   * @param msg   - message contents to write
   * @param color - color of text
   * @param font  - the font selection
   * @param size  - the font point size
   * @param ftype - type of font style
   */
  private void appendToPane(String msg, TextColor color, String font, int size,
                                   FontType ftype) {
    AttributeSet aset = setTextAttr(color, font, size, ftype);
    if (textPane != null) {
      int len = textPane.getDocument().getLength();

      // trim off earlier data to reduce memory usage if we exceed our bounds
      if (len > MAX_TEXT_BUFFER_SIZE) {
        try {
          int oldlen = len;
          int start = REDUCE_BUFFER_SIZE;
          String text = textPane.getDocument().getText(start, 500);
          int offset = text.indexOf(NEWLINE);
          if (offset >= 0) {
            start += offset + 1;
          }
          textPane.getDocument().remove(0, start);
          len = textPane.getDocument().getLength();
          System.out.println("Reduced text from " + oldlen + " to " + len);
        } catch (BadLocationException ex) {
          System.out.println(ex.getMessage());
        }
      }

      textPane.setCaretPosition(len);
      textPane.setCharacterAttributes(aset, false);
      textPane.replaceSelection(msg);
    } else {
      String current = textArea.getText();
      textArea.setText(current + NEWLINE + msg);
    }
  }

  /**
   * generates the specified text color for the debug display.
   * 
   * @param colorName - name of the color to generate
   * @return corresponding Color value representation
   */
  private Color generateColor (TextColor colorName) {
    float hue, sat, bright;
    switch (colorName) {
      default:
      case Black:
        return Color.BLACK;
      case DkGrey:
        return Color.DARK_GRAY;
      case DkRed:
        hue    = (float)0;
        sat    = (float)100;
        bright = (float)66;
        break;
      case Red:
        hue    = (float)0;
        sat    = (float)100;
        bright = (float)90;
        break;
      case LtRed:
        hue    = (float)0;
        sat    = (float)60;
        bright = (float)100;
        break;
      case Orange:
        hue    = (float)20;
        sat    = (float)100;
        bright = (float)100;
        break;
      case Brown:
        hue    = (float)20;
        sat    = (float)80;
        bright = (float)66;
        break;
      case Gold:
        hue    = (float)40;
        sat    = (float)100;
        bright = (float)90;
        break;
      case Green:
        hue    = (float)128;
        sat    = (float)100;
        bright = (float)45;
        break;
      case Cyan:
        hue    = (float)190;
        sat    = (float)80;
        bright = (float)45;
        break;
      case LtBlue:
        hue    = (float)210;
        sat    = (float)100;
        bright = (float)90;
        break;
      case Blue:
        hue    = (float)240;
        sat    = (float)100;
        bright = (float)100;
        break;
      case Violet:
        hue    = (float)267;
        sat    = (float)100;
        bright = (float)100;
        break;
      case DkVio:
        hue    = (float)267;
        sat    = (float)100;
        bright = (float)66;
        break;
    }
    hue /= (float)360.0;
    sat /= (float)100.0;
    bright /= (float) 100.0;
    return Color.getHSBColor(hue, sat, bright);
  }

  /**
   * A generic function for appending formatted text to a JTextPane.
   * 
   * @param color - color of text
   * @param font  - the font selection
   * @param size  - the font point size
   * @param ftype - type of font style
   * @return the attribute set
   */
  private AttributeSet setTextAttr(TextColor color, String font, int size, FontType ftype) {
    boolean bItalic = false;
    boolean bBold = false;
    if (ftype == FontType.Italic || ftype == FontType.BoldItalic) {
      bItalic = true;
    }
    if (ftype == FontType.Bold || ftype == FontType.BoldItalic) {
      bBold = true;
    }

    StyleContext sc = StyleContext.getDefaultStyleContext();
    AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground,
                                        generateColor(color));

    aset = sc.addAttribute(aset, StyleConstants.FontFamily, font);
    aset = sc.addAttribute(aset, StyleConstants.FontSize, size);
    aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
    aset = sc.addAttribute(aset, StyleConstants.Italic, bItalic);
    aset = sc.addAttribute(aset, StyleConstants.Bold, bBold);
    return aset;
  }
    
}
