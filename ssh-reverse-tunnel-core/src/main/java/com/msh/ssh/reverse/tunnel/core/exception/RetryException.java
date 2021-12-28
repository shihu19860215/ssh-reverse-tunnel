package com.msh.ssh.reverse.tunnel.core.exception;

public class RetryException extends TunnelException{

  public RetryException(String message) {
    super(message);
  }

  public RetryException(String message, Throwable cause) {
    super(message, cause);
  }
}
