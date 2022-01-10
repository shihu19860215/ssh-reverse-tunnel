package com.msh.ssh.reverse.tunnel.core.forward;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.exception.PortUsedException;
import com.msh.ssh.reverse.tunnel.core.exception.RetryException;
import com.msh.ssh.reverse.tunnel.core.util.TunnelUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 反隧道类
 * 可断线重连
 */
@Slf4j
public class ReverseTunnel {
  private SshServerInfo sshServerInfo;
  private long timeout;
  private int retryTime;

  /**
   * 超时时间
   */
  private static final long DEFAULT_TIMEOUT = 30*60*1000L;
  /**
   * 重试次数
   */
  private static final int DEFAULT_RETRY_TIME = 5;


  /**
   * 构建反隧道
   * 默认30分钟后断开连接
   * 随机获取连接端口，如果端口被禁用则重试
   * 默认重试5次
   * @param sshServerInfo  远程服务器信息
   */
  public ReverseTunnel(SshServerInfo sshServerInfo) {
    this(sshServerInfo, DEFAULT_TIMEOUT, DEFAULT_RETRY_TIME);
  }

  /**
   * 构建反隧道
   * @param sshServerInfo 远程服务器信息
   * @param timeout 超时时间
   * @param retryTime 重试次数
   */
  public ReverseTunnel(SshServerInfo sshServerInfo, long timeout, int retryTime) {
    this.sshServerInfo = sshServerInfo;
    this.timeout = timeout;
    this.retryTime = retryTime;
  }

  private Integer wanPort;
  private Integer lanPort;
  private OpenLocalPortForwardR openLocalPortForwardR;
  private OpenRemotePortForwardL openRemotePortForwardL;
  private static final String PORT_PID = "ss -lntps|grep %s";
  private static final String PORT_FLAG = ":%s ";
  private static final String PID_KILL = "kill -9 %s";


  /**
   * 打开指定端口反隧道连接
   * @param wanPort
   * @param lanPort
   * @throws JSchException
   * @throws PortUsedException
   * @throws IOException
   */
  public void openReverseTunnel(int wanPort, int lanPort)
      throws JSchException, PortUsedException, IOException {
    OpenLocalPortForwardR openLocalPortForwardR = null;
    OpenRemotePortForwardL openLocalPortForwardL = null;
    try {
      openLocalPortForwardR = new OpenLocalPortForwardR(sshServerInfo, 0);
      openLocalPortForwardL = new OpenRemotePortForwardL(sshServerInfo, 0);
      openLocalPortForwardL.forward(wanPort, lanPort);
      openLocalPortForwardR.forward(lanPort);
      this.openRemotePortForwardL = openLocalPortForwardL;
      this.openLocalPortForwardR = openLocalPortForwardR;
      this.wanPort = wanPort;
      this.lanPort = lanPort;
      startMonitor();
    }catch (JSchException | PortUsedException | IOException e){
      if(null != openLocalPortForwardR){
        openLocalPortForwardR.close();
      }
      if(null != openLocalPortForwardL){
        openLocalPortForwardL.close();
      }
      throw e;
    }
  }


  /**
   *  随机打开反隧道连接，端口占用后会重试
   * @throws JSchException
   * @throws IOException
   * @throws RetryException 超过重试次数
   */
  public void openReverseTunnelRandom() throws JSchException, IOException, RetryException {
    OpenLocalPortForwardR openLocalPortForwardR = null;
    OpenRemotePortForwardL openRemotePortForwardL = null;
    try {
      openLocalPortForwardR = new OpenLocalPortForwardR(sshServerInfo, 0);
      openRemotePortForwardL = new OpenRemotePortForwardL(sshServerInfo, 0);
      int wanPort;
      int lanPort;
      int i = retryTime;
      while (i > 0){
        wanPort = TunnelUtil.randomPort();
        lanPort = TunnelUtil.next(wanPort);
        try {
          openRemotePortForwardL.forward(wanPort, lanPort);
          openLocalPortForwardR.forward(lanPort);
          this.openRemotePortForwardL = openRemotePortForwardL;
          this.openLocalPortForwardR = openLocalPortForwardR;
          this.wanPort = wanPort;
          this.lanPort = lanPort;
          log.info("反隧道启动完成, 连接地址:{}, 端口:{}", sshServerInfo.getHost(), wanPort);
          startMonitor();
          return;
        }catch (PortUsedException portException){
          log.info(portException.getMessage());
        }
        i--;
      }
      throw new RetryException("重试多次仍未成功");
    }catch (Exception e){
      if(null != openLocalPortForwardR){
        openLocalPortForwardR.close();
      }
      if(null != openRemotePortForwardL){
        openRemotePortForwardL.close();
      }
      throw e;
    }
  }

  @SuppressWarnings("AlibabaAvoidManuallyCreateThread")
  private void startMonitor(){
    new Thread(null, new MonitorRunnable(timeout), "反隧道监控").start();
  }

  public class MonitorRunnable implements Runnable{
    private Long exitTime;

    public MonitorRunnable(Long timeout) {
      if(timeout > 0){
        this.exitTime = System.currentTimeMillis() + timeout;
      }
    }

    @Override
    public void run() {
      while(true){
        // 超时则关闭退出
        if(null != exitTime && System.currentTimeMillis() > exitTime){
          openLocalPortForwardR.close();
          openRemotePortForwardL.close();
          try {
            closePort();
          } catch (JSchException | IOException  e) {
            log.error("关闭端口失败", e);
          }
          return;
        }
        // 有一个链接关闭则断开
        if(!openRemotePortForwardL.isConnect() || !openLocalPortForwardR.isConnect()){
          openLocalPortForwardR.close();
          openRemotePortForwardL.close();
        }
        // 关闭当前所有链接，重写链接 ，结束当前监控
        if(openRemotePortForwardL.isClose() && openLocalPortForwardR.isClose()){
          try {
            closePort();
            openReverseTunnel(wanPort, lanPort);
            return;
          } catch (JSchException | IOException | PortUsedException e) {
            log.error("重写连接失败", e);
          }
        }

        try {
          TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  private String cmdPortPid(Integer wanPort, Integer lanPort){
    StringBuilder sb = new StringBuilder();
    sb.append("-e ")
        .append(wanPort)
        .append(" -e ")
        .append(lanPort);
    return String.format(PORT_PID, sb.toString());
  }

  private Set<String> getPortPid(String cmdResult, Integer wanPort, Integer lanPort){
    return Arrays
      .stream(cmdResult.split("\n"))
      .filter(
          s-> s.contains(String.format(PORT_FLAG, wanPort)) || s.contains(String.format(PORT_FLAG, lanPort))
      )
      .map(s->{
        String pidStr = s.substring(s.indexOf("pid=") + 4);
        pidStr = pidStr.substring(0, pidStr.indexOf(","));
        return pidStr;
      }).collect(Collectors.toSet());
  }

  private void closePort() throws JSchException, IOException {
    Session session = null;
    try {
      session = TunnelUtil
          .getSession(sshServerInfo.getHost(), sshServerInfo.getPort(), sshServerInfo.getUsername(),
              sshServerInfo.getPassword());
      session.connect();
      //检测外网端口是否被占用
      String cmd = cmdPortPid(wanPort, lanPort);
      String exec = TunnelUtil.exec(session, cmd);
      log.debug("执行命令:{}\r\n返回结果{}", cmd, exec);
      Set<String> portPids = getPortPid(exec, wanPort, lanPort);
      if (null != portPids && portPids.size() > 0) {
        String pids = portPids.stream().collect(Collectors.joining(" "));
        cmd = String.format(PID_KILL, pids);
        exec = TunnelUtil.exec(session, cmd);
        log.debug("执行命令:{}\r\n返回结果{}", cmd, exec);
        try {
          TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
        }
      }
    }finally {
      if(null != session){
        session.disconnect();
      }
    }
  }

}
