/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author dan
 */
  public class FileSaver extends Thread {

    private static PrintWriter writer;
    private static BufferedReader reader;
    private static LinkedBlockingQueue<String> queue;

    public FileSaver(String fname, LinkedBlockingQueue<String> recvBuffer) {
      queue = recvBuffer;
      reader = null;
      writer = null;

      // setup the file to save the data in
      setFile(fname);
      System.out.println("FileSaver: started");
    }
    
    @Override
    public void run() {
      // wait for input and copy to file
      while(true) {
        if (writer != null && queue != null) {
          try {
            // read next message from input buffer
            String message = queue.take();

            // append message to file
            writer.write(message + System.getProperty("line.separator"));
            writer.flush();
          } catch (InterruptedException ex) {
            System.out.println("FileSaver: " + ex.getMessage());
          }
        }
      }
    }

    public void exit() {
      if (writer != null) {
        System.out.println("FileSaver: closing");
        writer.close();
      }
    }
    
    public String getNextMessage() {
      String message = null;
      if (reader != null) {
        try {
          message = reader.readLine();
        } catch (IOException ex) {
          System.out.println("getNextMessage: " + ex.getMessage());
        }
      }

      return message;
    }
    
    public static void resetInput() {
      if (writer != null) {
        String message = "# Logfile started: " + LocalDateTime.now();
        message = message.replace('T', ' ');
        writer.write("----------------------------------------------------------------------" +
            System.getProperty("line.separator"));
        writer.write(message + System.getProperty("line.separator"));
        writer.flush();
      }
    }

    public static void setFile(String fname) {
      // ignore if name was not changed
      if (fname == null || fname.isEmpty()) {
        return;
      }

      try {
        // close any current reader
        if (reader != null) {
          System.out.println("FileSaver: closing reader");
          reader.close();
        }
        reader = null;

        // close any current writer
        if (writer != null) {
          System.out.println("FileSaver: closing writer");
          writer.close();
        }
        writer = null;

        // remove any existing file
        File file = new File(fname);
        if (file.isFile()) {
          System.out.println("FileSaver: deleting file");
          file.delete();
        }

        // set the buffer file to use for capturing input
        System.out.println("FileSaver: writing to " + fname);
        writer = new PrintWriter(new FileWriter(fname, true));

        // output time log started
        String message = "# Logfile started: " + LocalDateTime.now();
        message = message.replace('T', ' ');
        writer.write(message + System.getProperty("line.separator"));
        writer.flush();
      
        // attach new file reader for output to gui
        reader = new BufferedReader(new FileReader(new File(fname)));
        System.out.println("FileSaver: reader status: " + reader.ready());

      } catch (IOException ex) {  // includes FileNotFoundException
        System.out.println(ex.getMessage());
      }
    }

  }  
