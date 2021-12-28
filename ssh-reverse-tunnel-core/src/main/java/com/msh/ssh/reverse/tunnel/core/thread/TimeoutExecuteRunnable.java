package com.msh.ssh.reverse.tunnel.core.thread;

import com.msh.ssh.reverse.tunnel.core.interfaces.IExecute;
import com.msh.ssh.reverse.tunnel.core.interfaces.IExit;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeoutExecuteRunnable implements Runnable{
  private IExecute execute;
  private IExit exit;
  private long endTime;
  private long sleepMilliSeconds;

  public TimeoutExecuteRunnable(IExecute execute, IExit exit, long endTime) {
    this(execute, exit, endTime, 3000L);
  }

  public TimeoutExecuteRunnable(IExecute execute, IExit exit, long endTime, long sleepMilliSeconds) {
    this.execute = execute;
    this.exit = exit;
    this.endTime = endTime;
    this.sleepMilliSeconds = sleepMilliSeconds;
  }

  @Override
  public void run() {
    while(true){
      try {
        if(System.currentTimeMillis() > endTime){
          execute.execute();
          break;
        }
      }catch (Exception e){
        log.warn("TimeoutExcuteRunnable error.", e);
      }
      if(exit.isExit()){
        break;
      }

      try {
        TimeUnit.MILLISECONDS.sleep(sleepMilliSeconds);
      } catch (InterruptedException e) {
      }
    }
  }
}
