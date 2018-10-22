/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import debug.GuiPanel.Visitor;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

// provides callback interface
interface MyListener{
  void exit();
}

/**
 *
 * @author dan
 */
public final class ServerThread extends Thread implements MyListener {
 
  public static final String BUFFER_FILE_NAME = "debug.log";

  private static LinkedBlockingQueue<String> recvBuffer;
  
  protected static DatagramSocket dataSocket = null;
  protected static ServerSocket   serverSocket = null;
  protected static Socket         connectionSocket = null;

  private static int              serverPort;
  private static boolean          running;
  private static int              pktsRead;
  private static int              pktsLost;
  private static int              lastPktCount;
  private static String           storageFileName;
  private static BufferedReader   inFromClient = null;    // TCP input reader
//  private static BufferedReader   bufferedReader = null;  // msg storage file reader
//  private static BufferedWriter   bufferedWriter = null;  // msg storage file writer
//  private static Timer            fileTimer;
  private static FileSaver        fileSaver;
  private static Visitor          guiCallback;

  public ServerThread(int port, boolean tcp, String fname, Visitor connection) throws IOException {
    super("ServerThread");
    
    serverPort = port;
    recvBuffer = new LinkedBlockingQueue<>();
    guiCallback = connection;
    
    try {
      // init statistics
      lastPktCount = -1;
      pktsRead = 0;
      pktsLost = 0;
      storageFileName = BUFFER_FILE_NAME;

      // create file to hold the data from receive buffer (so we don't use too much memory)
      setBufferFile(fname);
      
      // open the communications socket
      if (tcp) {
        serverSocket = new ServerSocket(serverPort);
        System.out.println("TCP server started on port: " + serverPort + ", waiting for connection");
      } else {
        dataSocket = new DatagramSocket(serverPort);
        System.out.println("UDP server started on port: " + serverPort);
      }

      running = true;

    } catch (Exception ex) {
      System.out.println("port " + serverPort + "failed to start");
      System.out.println(ex.getMessage());
      System.exit(1);
    }
  }
  
  public void clear() {
      // reset statistics and empty the buffer
      lastPktCount = -1;
      pktsRead = 0;
      pktsLost = 0;
      recvBuffer.clear();
      // TODO: flush the bufferedReader
  }
  
  public int getQueueSize() {
    return recvBuffer.size();
  }
  
  public int getPktsRead() {
    return pktsRead;
  }
  
  public int getPktsLost() {
    return pktsLost;
  }
  
  public String getOutputFile() {
    return storageFileName;
  }
  
  public void resetInput() {
    if (fileSaver != null) {
      fileSaver.resetInput();
    }
  }

  public String getNextMessage() {
    return (fileSaver == null) ? null : fileSaver.getNextMessage();
  }

  public void setBufferFile(String fname) {
    // ignore if name was not changed or empty name given
    if (fname == null || fname.isEmpty() || fname.equals(storageFileName)) {
      return;
    }

    // remove any existing file and save the full path of the assigned file name
    File file = new File(fname);
    storageFileName = file.getAbsolutePath();
    if (file.isFile()) {
      file.delete();
    }

    // setup the file to save to
    if (fileSaver == null) {
      System.err.println("fileSaver not started yet!");
    } else {
      fileSaver.setFile(storageFileName);
    }
  }

  public void eraseBufferFile() {
    // ignore if name was not changed or empty name given
    if (storageFileName == null || storageFileName.isEmpty()) {
      return;
    }

    // remove any existing file and save the full path of the assigned file name
//    File file = new File(storageFileName);
//    if (file.isFile()) {
//      file.delete();
//    }

    // setup the file to save to (deletes old file first)
    if (fileSaver == null) {
      System.err.println("fileSaver not started yet!");
    } else {
      fileSaver.setFile(storageFileName);
    }
  }

  /**
   * this is the callback to run when exiting
   */
  @Override
  public void exit() {
    running = false;
    if (fileSaver != null) {
      fileSaver.exit();
    }
  }

  @Override
  public void run() {
    // start the thread for copying input to file
    fileSaver = new FileSaver(storageFileName, recvBuffer);
    Thread t = new Thread(fileSaver);
    t.start();

    while (running) {
      try {
        if (dataSocket != null) {
          // UDP socket connection...
          // get next packet received
          byte[] buf = new byte[1024];
          DatagramPacket packet = new DatagramPacket(buf, buf.length);
          dataSocket.receive(packet);
        
          // add message to buffer (lose messages if we are overrunning the buffer)
          if (recvBuffer.size() < 100000) {
            byte[] bytes = packet.getData();
            ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
            DataInputStream dataIn = new DataInputStream(byteIn);
            String message = dataIn.readUTF();

            recvBuffer.add(message);
            ++pktsRead;

            // keep track of lost packets (1st 8 chars are the incrementing line count)
            int count = Integer.parseInt(message.substring(0, 8));
            if (lastPktCount >= 0 && count > lastPktCount + 1) {
              pktsLost += count - lastPktCount - 1;
              message = "ERROR : Lost packets: " + (count - lastPktCount - 1);
              recvBuffer.add(message);
            }
            lastPktCount = count;
          }
        } else {
          // TCP socket connection...
          // accept connection if no connection yet
          // we only handle 1 connection at a time - any prev connection must first be closed
          if (connectionSocket == null) {
            try {
              connectionSocket = serverSocket.accept();
              String connection = connectionSocket.getInetAddress().getHostAddress() + ":" +
                                  connectionSocket.getPort();
              System.out.println("connected to client: " + connection);
              this.guiCallback.showConnection(connection);
              InputStream cis = connectionSocket.getInputStream();
              inFromClient = new BufferedReader(new InputStreamReader(cis));
              //DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
            } catch (IOException ex) {
              System.err.println("ERROR: " + ex.getMessage());
              try {
                serverSocket.close();
              } catch (IOException ex1) {
                // ignore - we are exiting anyway
              } finally {
                System.exit(1);
              }
            }
          }
        
          // read input from client and add to buffer
          String message = inFromClient.readLine();
          if (message != null) {
            // recvBuffer.add(message);
            try {
              recvBuffer.put(message);
            } catch (InterruptedException ex) { /* ignore */ }
            ++pktsRead;
          } else {
            // client disconnected - close the socket so a new connection can be made
            connectionSocket.shutdownInput();
            connectionSocket.close();
            connectionSocket = null;
            this.guiCallback.resetConnection();
          }
        }
      } catch (IOException ex) {
        System.err.println("ERROR: " + ex.getMessage());
      }
    }

    // terminating loop - close all sockets
    if (dataSocket != null) {
      System.out.println("closing UDP socket for port: " + serverPort);
      dataSocket.close();
    }
    try {
      if (connectionSocket != null) {
        System.out.println("closing TCP connection socket");
        connectionSocket.close();
        this.guiCallback.resetConnection();
      }
      if (serverSocket != null) {
        System.out.println("closing TCP server socket for port: " + serverPort);
        serverSocket.close();
      }
    } catch (IOException ex) {
      // ignore
    }
  }

 
}
