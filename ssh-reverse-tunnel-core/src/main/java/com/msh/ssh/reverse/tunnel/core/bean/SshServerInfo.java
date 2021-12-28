package com.msh.ssh.reverse.tunnel.core.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SshServerInfo {
  private String host;
  private int port;
  private String username;
  private String password;

}
