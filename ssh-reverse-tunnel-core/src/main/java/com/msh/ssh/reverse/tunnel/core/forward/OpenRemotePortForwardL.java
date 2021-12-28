package com.msh.ssh.reverse.tunnel.core.forward;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.exception.PortUsedException;
import com.msh.ssh.reverse.tunnel.core.util.TunnelUtil;
import com.msh.ssh.reverse.tunnel.core.thread.TimeoutExecuteRunnable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 打开远端服务器的端口转发功能
 */
@Slf4j
public class OpenRemotePortForwardL implements AutoCloseable{
  private SshServerInfo sshServerInfo;

  /**
   * @param sshServerInfo 远程服务器信息
   * @param timeout 超时时间
   * @throws JSchException
   */
  public OpenRemotePortForwardL(SshServerInfo sshServerInfo, long timeout) throws JSchException {
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
          "OpenRemotePortForwardL超时检查"
      ).start();
    }
  }

  private Session session;
  private ChannelShell channel;
  private InputStream is;
  private OutputStream os;

  private static final String PORT_CHECK = "ss -ln|grep %d";
  private static final String CONNECT_CMD = "ssh -o StrictHostKeyChecking=no -o GSSAPIAuthentication=no -NL *:%d:localhost:%d %s@localhost";


  private volatile boolean isClose = false;


  private String cmdForward(int wanPort, int lanPort) {
    return String.format(CONNECT_CMD, wanPort, lanPort, sshServerInfo.getUsername());
  }
  private String cmdCheckPort(int port) {
    return String.format(PORT_CHECK, port);
  }

  private boolean portUsed(int port, String result){
    if(result.indexOf(":" + port) >= 0){
      return true;
    }
    return false;
  }


  /**
   * 开启转发
   * @param wanPort 外网端口号
   * @param lanPort 内网端口号
   * @throws PortUsedException
   */
  public void forward(int wanPort, int lanPort) throws PortUsedException, IOException, JSchException {
      //检测外网端口是否被占用
      String cmd = cmdCheckPort(wanPort);
      String exec = TunnelUtil.exec(session, cmd);
      log.debug("执行命令:{}\r\n返回结果{}", cmd, exec);
      if(portUsed(wanPort, exec)){
        throw new PortUsedException("端口已被占用,port:" + wanPort);
      }
      //检测内网端口是否被占用
      cmd = cmdCheckPort(lanPort);
      exec = TunnelUtil.exec(session, cmd);
      log.debug("执行命令:{}\r\n返回结果{}", cmd, exec);
      if(portUsed(lanPort, exec)){
        throw new PortUsedException("端口已被占用,port:" + lanPort);
      }
      localForwad(wanPort, lanPort);
  }

  /**
   * 本地转发命令
   * @throws JSchException
   * @throws IOException
   */
  private void localForwad(int wanPort, int lanPort) throws JSchException, IOException {
    channel= (ChannelShell) session.openChannel("shell");
    channel.setPty(true);
    channel.connect();
    is = channel.getInputStream();
    os = channel.getOutputStream();
    byte[] tmp=new byte[1024];
    while(true){
      if(is.available()>0){
        int i=is.read(tmp, 0, 1024);
        if(i<0){
          continue;
        }
        String str = new String(tmp, 0, i);
        log.debug("接收结果{}", str);
        if(str.startsWith("Last login")){
          //初次连接成功时会收到后面内容 Last login: Mon Dec 27 15:51:18 2021 from 192.168.70.1
          String cmd = cmdForward(wanPort, lanPort);
          log.debug("执行命令{}", cmd);
          os.write((cmd +"\n").getBytes(StandardCharsets.UTF_8));
          os.flush();
        }else if("\r\n".equals(str)){
          //输入密码后会返回,不做密码错误处理,请确保密码正确
          return;
        }else if(str.indexOf("password") >= 0){
          log.debug("写入密码");
          //输入密码
          os.write((sshServerInfo.getPassword() +"\n").getBytes(StandardCharsets.UTF_8));
          os.flush();
        }
      }
      if(isClose()){
        break;
      }
      try{
        TimeUnit.SECONDS.sleep(1);
        Thread.sleep(1000);
      }catch(Exception ee){}
    }
  }

  public synchronized boolean isClose(){
    return isClose;
  }

  @Override
  public synchronized void close(){
    if(isClose()){
      return;
    }
    log.debug("关闭OpenRemotePortForwardL");
    isClose = true;
    if(null != os){
      //退出shell
      try {
        os.write("exit\n".getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
      }
      // 关闭输出流
      try {
        os.close();
      } catch (IOException e) {
      }
    }
    if(null != is){
      try {
        is.close();
      } catch (IOException e) {
      }
    }
    if(null != channel){
      channel.disconnect();
    }
    if(null != session){
      session.disconnect();
    }
  }

}
