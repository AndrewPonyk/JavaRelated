package com.packt.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "mail")
public class EmailMojo extends AbstractMojo{

	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("This is Mail Plugin : SENDING MAIL: 14.09.15:13:09");
	}

}
