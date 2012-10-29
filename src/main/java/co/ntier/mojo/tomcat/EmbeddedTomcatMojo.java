package co.ntier.mojo.tomcat;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.loader.VirtualWebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import co.ntier.mongo.tomcat.MongoSessionManager;
import co.ntier.mongo.tomcat.MongoSessionTrackerValve;

import com.mongodb.ServerAddress;

/**
 * 
 * @goal tomcat
 * @requiresProject true
 * @requiresDependencyResolution compile+runtime
 * 
 */
public class EmbeddedTomcatMojo extends AbstractMojo {

	/**
	 * @parameter default-value="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * @parameter
	 */
	private File propFile;

	private Properties properties = new Properties();

	public void execute() throws MojoExecutionException {

		try {
			if (propFile == null) {
				getLog().info("No properties file specified");
			} else {
				getLog().info("Loading properties from " + propFile);
				properties.load(new FileInputStream(propFile));
			}

			String defaultPath = "target/"+ project.getBuild().getFinalName();
			String war = properties.getProperty("target.path", defaultPath);
			getLog().info("War path: " + war);

			EmbeddedTomcat tomcat = new EmbeddedTomcat();
			initializePort(tomcat);
			Context context = initializeWar(tomcat, war);
			configureManager(context);

			getLog().info("Starting tomcat");
			tomcat.start();
			getLog().info("Started tomcat");
			tomcat.getServer().await();
			getLog().info("Closing tomcat...");
			
		} catch (Exception e) {
			getLog().error("Failed starting tomcat: " + e.getMessage(), e);
			throw new MojoExecutionException("Failed starting tomcat", e);
		}
	}

	private void initializePort(Tomcat tomcat) {
		String webPort = System.getenv("PORT");
		if (webPort == null || webPort.isEmpty()) {
			webPort = properties.getProperty("tomcat.port", "8080");
		}
		tomcat.setPort(Integer.valueOf(webPort));
		getLog().info("Configured tomcat for port " + webPort);
	}
	
	private Context initializeWar(Tomcat tomcat, String war) throws ServletException, MalformedURLException{
		File webappFile = new File(war);
		String absPath = webappFile.getAbsolutePath();
		Context context = tomcat.addWebapp("/", absPath);
		getLog().info("Deployed app from " + absPath);
		
		// add project to mojo's classloader
		VirtualWebappLoader vloader = new VirtualWebappLoader(Thread.currentThread().getContextClassLoader());
		vloader.setVirtualClasspath(absPath);
		context.setLoader(vloader);
		
		// TODO load a context.xml file (in a configurable manner)
		// context.setConfigFile(new URL("file://" + absPath + "/WEB-INF/config/deploy/context.xml"));
		
		return context;
	}
	
	private void configureManager(Context context) throws UnknownHostException{
		
		String 	host 		= properties.getProperty("mongo.session.host"),
				db 			= properties.getProperty("mongo.session.db"),
				user 		= properties.getProperty("mongo.session.user"),
				password 	= properties.getProperty("mongo.session.password");
		
		if ( host != null ) {
			// just an FYI: this throws a MongoException w/ the message
			// "unauthorized" OR "can't find a master" if you have the wrong
			// details here
			ServerAddress address = new ServerAddress(host);
			MongoSessionManager manager = new MongoSessionManager(address, db, user, password);
			
			context.getPipeline().addValve(new MongoSessionTrackerValve());
			context.setManager(manager);
			getLog().info("Established Mongo Tomcat Manager connection to " + address);
		}else{
			getLog().info("No Mongo Tomcat Manager detected");
		}
	}
}
