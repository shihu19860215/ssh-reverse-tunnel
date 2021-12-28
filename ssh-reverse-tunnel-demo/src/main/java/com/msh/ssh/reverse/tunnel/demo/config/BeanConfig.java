package com.msh.ssh.reverse.tunnel.demo.config;

import com.msh.ssh.reverse.tunnel.core.bean.SshServerInfo;
import com.msh.ssh.reverse.tunnel.core.forward.ReverseTunnel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {
  @Value("${msh.host}")
  private String host;
  @Value("${msh.port}")
  private int port;
  @Value("${msh.username}")
  private String username;
  @Value("${msh.password}")
  private String password;
  @Value("${msh.timeout:0}")
  private Long timeout;

  @Bean
  public ReverseTunnel reverseTunnel(){
    SshServerInfo sshServerInfo = new SshServerInfo(host, port, username, password);
    return new ReverseTunnel(sshServerInfo, timeout, 5);
  }
}
