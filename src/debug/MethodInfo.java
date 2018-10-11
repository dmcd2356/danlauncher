/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

//import com.google.gson.annotations.Expose;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author dmcd2356
 */
public class MethodInfo {
  private static final String NEWLINE = System.getProperty("line.separator");

  private String  fullName;       // full name of method (package, class, method + signature)
  private String  className;      // class name (no package info or method name)
  private String  methName;       // method name (no class info)
  private ArrayList<Integer> threadId; // the list of threads it was run in
  private HashMap<Integer, ThreadInfo> threadInfo; // the info for each individual thread
  private ThreadInfo total;        // contains the stats for all threads combined

  private ThreadInfo getThreadInfo(int tid) {
    if (tid < 0) {
      return total;
    }

    return threadInfo.get(tid);
  }
  
  public MethodInfo(int tid, String method, String parent, long tstamp, int insCount, int line) {
    threadId = new ArrayList<>();
    total = new ThreadInfo(tid, tstamp, insCount, line, parent);
    if (threadInfo == null) {
      threadInfo = new HashMap<>();
    }
    if (tid >= 0 && !threadId.contains(tid)) {
      ThreadInfo entry = new ThreadInfo(tid, tstamp, insCount, line, parent);
      threadId.add(tid);
      threadInfo.put(tid, entry);
    }

    fullName = className = methName = "";
    if (method != null && !method.isEmpty()) {
      // fullName should be untouched - it is used for comparisons
      fullName = method;
      String cleanName = method.replace("/", ".");
      if (cleanName.contains("(")) {
        cleanName = cleanName.substring(0, cleanName.lastIndexOf("("));
      }
      if (!cleanName.contains(".")) {
        methName = cleanName;
      } else {
        methName = cleanName.substring(cleanName.lastIndexOf(".") + 1);
        className = cleanName.substring(0, cleanName.lastIndexOf("."));
        if (className.contains(".")) {
          className = className.substring(className.lastIndexOf(".") + 1);
        }
      }
    }
  }
  
  public void repeatedCall(int tid, String parent, long tstamp, int insCount, int line) {
    if (tid < 0) {
      return;
    }

    // get the method info for the specified thread id
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      // if found, reset the reference values for elapsed time and instruction counts,
      // increment the # times called, and add the parent if not already listed
      tinfo.resetReference(tstamp, insCount);
      tinfo.incCount();
      if (!tinfo.parents.contains(parent)) {
        tinfo.addParent(parent);
      }
    } else {
      // else, this is 1st time this method was called on this thread - create a new entry for it
      threadId.add(tid);
      tinfo = new ThreadInfo(tid, tstamp, insCount, line, parent);
      threadInfo.put(tid, tinfo);
    }
    
    // update total info for method
    total.incCount();
    if (!total.parents.contains(parent)) {
      total.addParent(parent);
    }
  }
  
  public void exit(int tid, long tstamp, int insCount) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      // update elapsed time and instruction count for specified thread
      tinfo.exit(tstamp, insCount);

      // update the total elapsed and instructions
      total.incElapsed(tinfo.duration_ms);
      total.incInstructions(tinfo.instrCount);
    }
  }
  
  public void setExecption(int tid, int line) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      tinfo.lineExcept = line;
      total.lineExcept = line;
    }
  }
  
  public void setError(int tid, int line) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      tinfo.lineError = line;
      total.lineError = line;
    }
  }
  
  public String getFullName() {
    return fullName;
  }
  
  public String getClassAndMethod() {
    return className.isEmpty() ? methName : className + "." + methName;
  }
  
  public String getCGName() {
    return className.isEmpty() ? methName : className + NEWLINE + methName;
  }
  
  public String getMethodName() {
    return methName;
  }

  public ArrayList<Integer> getThread() {
    return threadId;
  }
  
  public ArrayList<String> getParents(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return new ArrayList<>();
    }
    return tinfo.parents;
  }
  
  public int getInstructionCount(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.instrCount;
  }
  
  public boolean isReturned(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return false;
    }
    return tinfo.exit;
  }
  
  public int getCount(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.callCount;
  }
  
  public int getExecption(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return -1;
    }
    return tinfo.lineExcept;
  }
  
  public int getError(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return -1;
    }
    return tinfo.lineError;
  }
  
  public int getFirstLine(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.lineFirst;
  }
  
  public long getDuration(int tid) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.duration_ms;
  }

  private class ThreadInfo {
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
      //System.out.println("start time: " + start_ref + " (init) - " + fullName);
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
      //System.out.println("exit time: " + currentTime + ", elapsed " + duration_ms + " - " +  fullName);
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

}
