package com.msh.ssh.reverse.tunnel.core.forward;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.thread.TimeoutMonitorRunnable;
import com.msh.ssh.reverse.tunnel.core.util.TunnelUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 打开本地反向隧道端口转发
 * 不会做断线重连
 */
@Slf4j
@SuppressWarnings("AlibabaAvoidManuallyCreateThread")
public class OpenLocalPortForwardR implements AutoCloseable {
  private SshServerInfo sshServerInfo;
  private long timeout;


  public OpenLocalPortForwardR(SshServerInfo sshServerInfo, long timeout) throws JSchException {
    this.sshServerInfo = sshServerInfo;
    this.timeout = timeout;
    session = TunnelUtil
        .getSession(sshServerInfo.getHost(), sshServerInfo.getPort(), sshServerInfo.getUsername(), sshServerInfo.getPassword());
    session.connect();
    startMonitor();
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


  public boolean isConnect(){
    log.debug("session is connect:{}.", session.isConnected());
    return session.isConnected();
  }

  public synchronized boolean isClose(){
    return isClose;
  }

  @Override
  public synchronized void close(){
    if(isClose()){
      return;
    }
    log.debug("关闭OpenLocalPortForwardR");
    isClose = true;
    if(null != session){
      session.disconnect();
    }
  }

  private void startMonitor(){
    if(timeout <= 0) {
      return;
    }
    //超时关闭
    new Thread(null,
        new TimeoutMonitorRunnable(
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
