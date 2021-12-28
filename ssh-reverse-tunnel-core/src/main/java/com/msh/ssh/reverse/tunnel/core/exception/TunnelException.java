package com.msh.ssh.reverse.tunnel.core.exception;

public class TunnelException extends Exception{

  public TunnelException(String message) {
    super(message);
  }

  public TunnelException(String message, Throwable cause) {
    super(message, cause);
  }
}
