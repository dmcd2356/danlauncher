/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logging;

import java.util.HashMap;

/**
 *
 * @author dan
 */
public class FontInfo {

  public enum FontType {
    Normal, Bold, Italic, BoldItalic;
  }
    
  public enum TextColor {
    Black, DkGrey, DkRed, Red, LtRed, Orange, Brown, Gold, Green, Cyan,
    LtBlue, Blue, Violet, DkVio;
  }
    
  TextColor  color;      // the font color
  FontType   fonttype;   // the font attributes (e.g. Italics, Bold,..)
  String     font;       // the font family (e.g. Courier)
  int        size;       // the font size
        
  public FontInfo (TextColor col, FontType type, int fsize, String fontname) {
    color = col;
    fonttype = type;
    font = fontname;
    size = fsize;
  }

  /**
   * sets the association between a type of message and the characteristics
   * in which to print the message.
   * 
   * @param map   - the hasmap to assign the entry to
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   * @param size  - the size of the font
   * @param font  - the font family (e.g. Courier, Ariel, etc.)
   */
  public static void setTypeColor (HashMap<String, FontInfo> map, String type,
      TextColor color, FontType ftype, int size, String font) {
    FontInfo fontinfo = new FontInfo(color, ftype, size, font);
    if (map.containsKey(type)) {
      map.replace(type, fontinfo);
    }
    else {
      map.put(type, fontinfo);
    }
  }

}
