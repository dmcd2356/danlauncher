/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import java.awt.Color;
import java.awt.Graphics;
import java.util.HashMap;
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
  
  private enum FontType {
    Normal, Bold, Italic, BoldItalic;
  }
    
  private enum TextColor {
    Black, DkGrey, DkRed, Red, LtRed, Orange, Brown, Gold, Green, Cyan,
    LtBlue, Blue, Violet, DkVio;
  }
    
  // the default point size and font types to use
  private final static int    DEFAULT_POINT = 14;
  private final static String DEFAULT_FONT = "Courier";
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static JTextPane       debugTextPane = null;
  private static final HashMap<String, FontInfo> messageTypeTbl = new HashMap<>();

  public Logger (String name, JTextPane textpane) {
    if (textpane == null) {
      System.out.println("ERROR: Textpane passed to '" + name + "' Logger was null!");
      System.exit(1);
    }
    debugTextPane = textpane;
    setColors();
  }

  /**
   * clears the display.
   */
  public final void clear() {
    debugTextPane.setText("");
  }

  /**
   * updates the display immediately
   */
  public final void updateDisplay () {
    Graphics graphics = debugTextPane.getGraphics();
    if (graphics != null) {
      debugTextPane.update(graphics);
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
  
  private String opcodeColor(String opcode) {
//    if (opcode.startsWith("iload") ||
//        opcode.startsWith("lload") ||
//        opcode.startsWith("fload") ||
//        opcode.startsWith("dload") ||
//        opcode.startsWith("aload")) {
//      return "LOAD";
//    }
//    if (opcode.startsWith("istore") ||
//        opcode.startsWith("lstore") ||
//        opcode.startsWith("fstore") ||
//        opcode.startsWith("dstore") ||
//        opcode.startsWith("astore")) {
//      return "STORE";
//    }
    if (opcode.startsWith("invoke")) {
      return "INVOKE";
    }
    if (opcode.startsWith("if_") ||
        opcode.startsWith("ifeq") ||
        opcode.startsWith("ifne") ||
        opcode.startsWith("ifgt") ||
        opcode.startsWith("ifge") ||
        opcode.startsWith("iflt") ||
        opcode.startsWith("ifle")) {
      return "BRANCH";
    }
    
    return "OTHER";
  }
  
  /**
   * outputs the bytecode message to the status display.
   * 
   * @param line
   * @param opcode
   * @param param
   * @param comment
   */
  public final void printBytecode(int line, String opcode, String param, String comment) {
    String color = opcodeColor(opcode);
    String linenum = "" + line;

    printRaw("TEXT", padToLeft(linenum, 5) + "  ");
    printRaw(color, padToRight(opcode, 16));
    printRaw("PARAM", padToRight(param, 10));
    printRaw("COMMENT", comment + NEWLINE);
  }
  
  public final void printMethod(String methodName) {
    printRaw("TEXT", "Method: ");
    printRaw("METHOD", methodName + NEWLINE + NEWLINE);
  }
  
  public final void printUnformatted(String message) {
    printRaw("NOFMT", message + NEWLINE);
  }
  
  private void setColors () {
    // these are for public consumption
    setTypeColor ("NOFMT",   TextColor.DkGrey, FontType.Italic);
    setTypeColor ("ERROR",   TextColor.Red,    FontType.Bold);
    setTypeColor ("WARN",    TextColor.Orange, FontType.Bold);

    setTypeColor ("METHOD",  TextColor.Violet, FontType.Italic, 16, DEFAULT_FONT);  // the class/method
    setTypeColor ("TEXT",    TextColor.Black,  FontType.Italic);  // generic text
    setTypeColor ("PARAM",   TextColor.Brown,  FontType.Normal);  // opcode parameter values
    setTypeColor ("COMMENT", TextColor.Green,  FontType.Italic);  // comments in the code

    setTypeColor ("BRANCH",  TextColor.DkVio,  FontType.Bold);    // branch instructions
    setTypeColor ("INVOKE",  TextColor.Gold,   FontType.Bold);    // invoke calls
    setTypeColor ("LOAD",    TextColor.Green,  FontType.Normal);  // opcodes that load from local
    setTypeColor ("STORE",   TextColor.Blue,   FontType.Normal);  // opcodes that store to local
    setTypeColor ("OTHER",   TextColor.Black,  FontType.Normal);  // all other opcodes

//    setTypeColor ("DUMP",   TextColor.Orange, FontType.Bold);
//    setTypeColor ("START",  TextColor.Black,  FontType.BoldItalic);
//    setTypeColor ("AGENT",  TextColor.Violet, FontType.Italic);
//    setTypeColor ("THREAD", TextColor.DkVio,  FontType.Italic);
//    setTypeColor ("RETURN", TextColor.Gold,   FontType.Bold);
//    setTypeColor ("UNINST", TextColor.Gold,   FontType.BoldItalic);
//    setTypeColor ("STATS",  TextColor.Gold,   FontType.BoldItalic); // obsolete
//    setTypeColor ("STACKS", TextColor.Blue,   FontType.Italic);
//    setTypeColor ("STACKI", TextColor.Blue,   FontType.Bold);
//    setTypeColor ("LOCAL",  TextColor.Green,  FontType.Normal);
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
    int len = debugTextPane.getDocument().getLength();

    // trim off earlier data to reduce memory usage if we exceed our bounds
    if (len > MAX_TEXT_BUFFER_SIZE) {
      try {
        int oldlen = len;
        int start = REDUCE_BUFFER_SIZE;
        String text = debugTextPane.getDocument().getText(start, 500);
        int offset = text.indexOf(NEWLINE);
        if (offset >= 0) {
          start += offset + 1;
        }
        debugTextPane.getDocument().remove(0, start);
        len = debugTextPane.getDocument().getLength();
        System.out.println("Reduced text from " + oldlen + " to " + len);
      } catch (BadLocationException ex) {
        System.out.println(ex.getMessage());
      }
    }

    debugTextPane.setCaretPosition(len);
    debugTextPane.setCharacterAttributes(aset, false);
    debugTextPane.replaceSelection(msg);
  }

  /**
   * sets the association between a type of message and the characteristics
   * in which to print the message.
   * 
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   */
  private void setTypeColor (String type, TextColor color, FontType ftype) {
    setTypeColor (type, color, ftype, DEFAULT_POINT, DEFAULT_FONT);
  }
    
  /**
   * same as above, but lets user select font family and size as well.
   * 
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   * @param size  - the size of the font
   * @param font  - the font family (e.g. Courier, Ariel, etc.)
   */
  private void setTypeColor (String type, TextColor color, FontType ftype, int size, String font) {
    FontInfo fontinfo = new FontInfo(color, ftype, size, font);
    if (messageTypeTbl.containsKey(type)) {
      messageTypeTbl.replace(type, fontinfo);
    }
    else {
      messageTypeTbl.put(type, fontinfo);
    }
  }
    
  /**
   * displays a message in the debug window (no termination).
   * 
   * @param type  - the type of message to display
   * @param message - message contents to display
   */
  private void printRaw(String type, String message) {
    if (message != null && !message.isEmpty()) {
      // set default values (if type was not found)
      TextColor color = TextColor.Black;
      FontType ftype = FontType.Normal;
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
    
  public class FontInfo {
    TextColor  color;      // the font color
    FontType   fonttype;   // the font attributes (e.g. Italics, Bold,..)
    String     font;       // the font family (e.g. Courier)
    int        size;       // the font size
        
    FontInfo (TextColor col, FontType type, int fsize, String fontname) {
      color = col;
      fonttype = type;
      font = fontname;
      size = fsize;
    }
  }
}
