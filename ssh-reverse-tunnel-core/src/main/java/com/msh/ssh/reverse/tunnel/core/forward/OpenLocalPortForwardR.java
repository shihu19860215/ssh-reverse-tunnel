package com.msh.ssh.reverse.tunnel.core.forward;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.util.TunnelUtil;
import com.msh.ssh.reverse.tunnel.core.thread.TimeoutExecuteRunnable;
import lombok.extern.slf4j.Slf4j;

/**
 * 打开本地反向隧道端口转发
 */
@Slf4j
public class OpenLocalPortForwardR implements AutoCloseable {
  private SshServerInfo sshServerInfo;


  public OpenLocalPortForwardR(SshServerInfo sshServerInfo, long timeout) throws JSchException {
    this.sshServerInfo = sshServerInfo;
    session = TunnelUtil
        .getSession(sshServerInfo.getHost(), sshServerInfo.getPort(), sshServerInfo.getUsername(), sshServerInfo.getPassword());
    session.connect();
    if(timeout > 0){
      //超时关闭
      new Thread(null,
          new TimeoutExecuteRunnable(
              ()->close(),
              ()->{
                if(isClose()){
                  return true;
                }
                return false;
              },
              System.currentTimeMillis() + timeout
          ),
          "OpenLocalPortForwardR超时检查"
      ).start();
    }
  }

  private Session session;
  private volatile boolean isClose;
  /**
   * 超时时间
   */
  private static final long DEFAULT_TIMEOUT = 30*60*1000L;

  /**
   * 开发转发
   * @param port 打开远程端口对应ip
   * @throws JSchException
   */
  public void forward(int port) throws JSchException {
    session.setPortForwardingR(sshServerInfo.getHost(), port, "localhost", 22);
    log.debug("开启反隧道成功,端口:{}", port);
  }


  public synchronized boolean isClose(){
    return isClose;
  }

  @Override
  public synchronized void close(){
    if(isClose()){
      return;
    }
    isClose = true;
    if(null != session){
      session.disconnect();
    }
  }

}
