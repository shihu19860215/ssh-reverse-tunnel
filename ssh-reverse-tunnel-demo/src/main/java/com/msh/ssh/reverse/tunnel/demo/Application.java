package com.msh.ssh.reverse.tunnel.demo;

import com.msh.ssh.reverse.tunnel.core.forward.ReverseTunnel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author shihu
 * @mail m-sh@qq.com
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) throws Exception {
		SpringApplication app = new SpringApplication(Application.class);
		ConfigurableApplicationContext run = app.run(args);
		run.getBean(ReverseTunnel.class).openReverseTunnelRandom();
	}

}
