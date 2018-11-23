/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

//import com.google.gson.annotations.Expose;
import java.util.ArrayList;
import java.util.HashMap;
import util.Utils;

/**
 *
 * @author dmcd2356
 */
public class MethodInfo {
  private String  fullName;       // full name of method (package, class, method + signature)
  private String  className;      // class name (no package info or method name)
  private String  methName;       // method name (no class info)
  private ArrayList<Integer> threadId; // the list of threads it was run in
  private HashMap<Integer, ThreadInfo> threadInfo; // the info for each individual thread
  private ThreadInfo total;        // contains the stats for all threads combined

  private ThreadInfo getThreadInfo(int tid) {
    if (tid < 0) {
      return null;
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
    } else if (tid >= 0) {
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
    }
    total.lineExcept = line;
  }
  
  public void setError(int tid, int line) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      tinfo.lineError = line;
    }
    total.lineError = line;
  }
  
  public String getFullName() {
    return fullName;
  }
  
  public String getClassAndMethod() {
    return className.isEmpty() ? methName : className + "." + methName;
  }
  
  public String getCGName() {
    return className.isEmpty() ? methName : className + Utils.NEWLINE + methName;
  }
  
  public String getClassName() {
    return fullName.substring(0, fullName.lastIndexOf("."));
  }

  public String getMethodName() {
    return methName;
  }

  public ArrayList<Integer> getThread() {
    return threadId;
  }
  
  public ArrayList<String> getParents(int tid) {
    if (tid < 0) {
      return total.parents;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return new ArrayList<>();
    }
    return tinfo.parents;
  }
  
  public int getInstructionCount(int tid) {
    if (tid < 0) {
      return total.instrCount;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.instrCount;
  }
  
  public boolean isReturned(int tid) {
    if (tid < 0) {
      return total.exit;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return false;
    }
    return tinfo.exit;
  }
  
  public int getCount(int tid) {
    if (tid < 0) {
      return total.callCount;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.callCount;
  }
  
  public int getExecption(int tid) {
    if (tid < 0) {
      return total.lineExcept;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return -1;
    }
    return tinfo.lineExcept;
  }
  
  public int getError(int tid) {
    if (tid < 0) {
      return total.lineError;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return -1;
    }
    return tinfo.lineError;
  }
  
  public int getFirstLine(int tid) {
    if (tid < 0) {
      return total.lineFirst;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.lineFirst;
  }
  
  public long getDuration(int tid) {
    if (tid < 0) {
      return total.duration_ms;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.duration_ms;
  }

}
