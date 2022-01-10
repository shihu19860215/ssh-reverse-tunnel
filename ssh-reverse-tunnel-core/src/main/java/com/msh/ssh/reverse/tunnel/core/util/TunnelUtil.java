package com.msh.ssh.reverse.tunnel.core.util;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelUtil {
  public static final int START_PORT = 5001;
  public static final int END_PORT = 65535;
  public static final Random RANDOM = new Random();


  public static int randomPort(){
    return RANDOM.nextInt(65535 - 5001) + START_PORT;
  }
  public static int next(int port){
    port ++;
    if(port > END_PORT){
      port = START_PORT;
    }
    return port;
  }

  public static Session getSession(String host, int port, String username, String password)
      throws JSchException {
    JSch jSch = new JSch();
    Session session = jSch.getSession(username, host, port);
    session.setPassword(password);
    session.setServerAliveInterval(60000);
    session.setServerAliveCountMax(5);
    session.setConfig("StrictHostKeyChecking", "no");
    session.setConfig("GSSAPIAuthentication", "no");
    return session;
  }


  public static String exec(Session session, String command) throws JSchException, IOException {
    ChannelExec exec = null;
    try {
      exec = (ChannelExec) session.openChannel("exec");
      InputStream is = exec.getInputStream();
      exec.setCommand(command);
      exec.connect();
      StringBuffer sb = new StringBuffer();
      byte[] bs = new byte[1024];
      int len = 0;
      while((len = is.read(bs)) >=0){
        sb.append(new String(bs, 0 , len, StandardCharsets.UTF_8));
      }
      return sb.toString();
    }finally {
      if(null != exec){
        exec.disconnect();
      }
    }

  }
}
