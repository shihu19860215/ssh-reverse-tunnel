package com.msh.ssh.reverse.tunnel.core.forward;

import com.jcraft.jsch.JSchException;
import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.exception.PortUsedException;
import com.msh.ssh.reverse.tunnel.core.exception.RetryException;
import com.msh.ssh.reverse.tunnel.core.util.TunnelUtil;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

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
    OpenLocalPortForwardR openLocalPortForwardR = new OpenLocalPortForwardR(sshServerInfo, timeout);
    OpenRemotePortForwardL openLocalPortForwardL = new OpenRemotePortForwardL(sshServerInfo, timeout);
    openLocalPortForwardL.forward(wanPort, lanPort);
    openLocalPortForwardR.forward(lanPort);
    this.openRemotePortForwardL = openLocalPortForwardL;
    this.openLocalPortForwardR = openLocalPortForwardR;
    this.wanPort = wanPort;
    this.lanPort = lanPort;
  }


  /**
   *  随机打开反隧道连接，端口占用后会重试
   * @throws JSchException
   * @throws IOException
   * @throws RetryException 超过重试次数
   */
  public void openReverseTunnelRandom() throws JSchException, IOException, RetryException {
    try {
      OpenLocalPortForwardR openLocalPortForwardR = new OpenLocalPortForwardR(sshServerInfo, timeout);
      OpenRemotePortForwardL openLocalPortForwardL = new OpenRemotePortForwardL(sshServerInfo, timeout);
      int wanPort;
      int lanPort;
      int i = retryTime;
      while (i > 0){
        wanPort = TunnelUtil.randomPort();
        lanPort = TunnelUtil.next(wanPort);
        try {
          openLocalPortForwardL.forward(wanPort, lanPort);
          openLocalPortForwardR.forward(lanPort);
          this.openRemotePortForwardL = openLocalPortForwardL;
          this.openLocalPortForwardR = openLocalPortForwardR;
          this.wanPort = wanPort;
          this.lanPort = lanPort;
          log.info("反隧道启动完成, 连接地址:{}, 端口:{}", sshServerInfo.getHost(), wanPort);
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
}
