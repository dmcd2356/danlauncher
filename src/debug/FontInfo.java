/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

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
        
  FontInfo (TextColor col, FontType type, int fsize, String fontname) {
    color = col;
    fonttype = type;
    font = fontname;
    size = fsize;
  }
}
