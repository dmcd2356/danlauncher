/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import java.util.ArrayList;

/**
 *
 * @author dan
 */
public class ThreadInfo {
  public int     tid;            // thread id for this thread
  public int     lineFirst;      // line number corresponding to 1st call to method
  public int     callCount;      // number of times method called
  public int     instrCount;     // the number of instructions executed by the method
  public long    duration_ms;    // total duration in method
  public int     lineExcept;     // line number of last exception that occurred in this method
  public int     lineError;      // line number of last error that occurred in this method
  public ArrayList<String> parents; // list of caller methods

  // these are intermediate values
  public long    start_ref;      // timestamp when method last called
  public int     instrEntry;     // the number of instructions executed upon entry to the method
  public boolean exit;           // true if return has been logged, false if method just entered

  public ThreadInfo(int tid, long tstamp, int insCount, int line, String parent) {
    init(tid, tstamp, insCount, line, parent);
  }
    
  public final void init(int tid, long tstamp, int insCount, int line, String parent) {
    // init 1st caller of method
    this.parents = new ArrayList<>();
    if (parent != null && !parent.isEmpty()) {
      this.parents.add(parent);
    }

    this.lineFirst = line;
    this.callCount = 1;
    this.start_ref = tstamp;
    this.duration_ms = -1;
    this.instrEntry = insCount;
    this.instrCount = -1;
    this.exit = false;
    this.lineExcept = -1;
    this.lineError = -1;
    this.tid = tid;
    //LauncherMain.printCommandMessage("start time: " + start_ref + " (init) - " + fullName);
  }
    
  public void resetReference(long tstamp, int insCount) {
    this.start_ref = tstamp;
    this.instrEntry = insCount;
    this.exit = false;
  }
    
  public void addParent(String parent) {
    // if caller entry not already in list, add it
    if (parents.indexOf(parent) < 0) {
      parents.add(parent);
    }
  }
  
  public void exit(long tstamp, int insCount) {
    long elapsedTime = (tstamp > start_ref) ? tstamp - start_ref : 0;
    incElapsed(elapsedTime);
    
    // if instruction count was defined, calc the time spent in the method & add it to current value
    if (insCount >= 0 && instrEntry >= 0) {
      int delta = (insCount > instrEntry) ? insCount - instrEntry : 0;
      incInstructions(delta);
    }
    exit = true;
    //LauncherMain.printCommandMessage("exit time: " + currentTime + ", elapsed " + duration_ms + " - " +  fullName);
  }
  
  public void incElapsed(long delta) {
    if (duration_ms < 0) {
      duration_ms = 0;
    }
    duration_ms += delta;
  }
    
  public void incInstructions(int delta) {
    if (instrCount < 0) {
      instrCount = 0;
    }
    instrCount += delta;
  }
    
  public void incCount() {
    ++callCount;
  }
}

