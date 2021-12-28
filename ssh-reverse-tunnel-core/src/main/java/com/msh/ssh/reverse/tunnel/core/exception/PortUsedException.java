package com.msh.ssh.reverse.tunnel.core.exception;

public class PortUsedException extends TunnelException{

  public PortUsedException(String message) {
    super(message);
  }

  public PortUsedException(String message, Throwable cause) {
    super(message, cause);
  }
}
