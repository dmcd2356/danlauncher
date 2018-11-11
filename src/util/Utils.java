/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import main.LauncherMain;

/**
 *
 * @author dan
 */
public class Utils {
  
  public static final String NEWLINE = System.getProperty("line.separator");
  
  public static String readTextFile(String filename) {

    String content = "";
    File file = new File(filename);
    if (!file.isFile()) {
      LauncherMain.printCommandError("file not found: " + filename);
    } else {
      try {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          content += line + NEWLINE;
        }
      } catch (IOException ex) {
        LauncherMain.printCommandError(ex.getMessage());
      }
    }

    return content;
  }

  public static void saveTextFile(String filename, String content) {
    // make sure all dir paths exist
    int offset = filename.lastIndexOf("/");
    if (offset > 0) {
      String newpathname = filename.substring(0, offset);
      File newpath = new File(newpathname);
      if (!newpath.isDirectory()) {
        newpath.mkdirs();
      }
    }
    
    // delete file if it already exists
    File file = new File(filename);
    if (file.isFile()) {
      file.delete();
    }
    
    // create a new file and copy text contents to it
    BufferedWriter bw = null;
    try {
      FileWriter fw = new FileWriter(file);
      bw = new BufferedWriter(fw);
      bw.write(content);
    } catch (IOException ex) {
      LauncherMain.printCommandError(ex.getMessage());
    } finally {
      if (bw != null) {
        try {
          bw.close();
        } catch (IOException ex) {
          LauncherMain.printCommandError(ex.getMessage());
        }
      }
    }
  }
  
}
