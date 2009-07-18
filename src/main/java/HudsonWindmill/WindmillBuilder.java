package HudsonWindmill;

import hudson.Launcher;
import hudson.util.FormFieldValidator;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;


/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class WindmillBuilder extends Builder {

    private String browser;
    private final String startURL;
    private final String tests;
    private final String port;
    private final String other;
    private final boolean enablessl;


    @DataBoundConstructor
    public WindmillBuilder(String browser, String startURL, String tests, String port, String other, boolean enablessl) {
        this.browser = browser;
        this.startURL = startURL;
        this.tests = tests;
        this.port = port;
        this.other = other;
        this.enablessl = enablessl;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getBrowser() {
        return browser;
    }
    public String setBrowser(String browser) {
        this.browser = browser;
        return this.browser;
    }

    public String getStartURL() {
        return startURL;
    }

    public String getTests() {
        return tests;
    }

    public String getPort() {
        return port;
    }

    public String getOther() {
        return other;
    }
    
    public boolean getEnablessl(){
        return enablessl;
    }

    public String buildCommand(){

        if (browser.equals("")){
            String browser = "firefox";
        }

        String runner = "";
        if (port.equals("") || port.equals("4444")){
            runner = "windmill "+ browser + " " + startURL + " test=" + tests + " exit " + other;
        } else {
            runner = "windmill "+ browser + " " + startURL + " test=" + tests + " port="+port + " exit " + other;
        }
        if (enablessl){
            runner += " ssl";
        }

        return runner;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener)  throws IOException, InterruptedException {
        // this is where you 'build' the project
        //We don't need to show the port if it's the default
        int exitCode = 0;
        if (this.getTests().equals("")){
            listener.error("Windmill can't run without any tests...");
            build.setResult(Result.FAILURE);
        }
        else if (this.getStartURL().equals("")){
            listener.error("Windmill requires that you provide a starting URL...");
            build.setResult(Result.FAILURE);
        }
        else {
            Map<String,String> envVars = build.getEnvVars();
            envVars.putAll(build.getBuildVariables());

            //if there is a dynamically set browser, use that
            if (envVars.containsKey("browser")){
                this.setBrowser(envVars.get("browser"));
            }

            //build the exec string
            String cmd = this.buildCommand();
            
            //do magical replacement of %ACCOUNT% string
            if (cmd.contains("%ACCOUNT%")){
                if (browser.equals("firefox")){
                   cmd = cmd.replaceAll("%ACCOUNT%", "1");
                }
                else {
                    cmd = cmd.replaceAll("%ACCOUNT%", "2");
                }
            }

            if (this.DESCRIPTOR.cleanup()){
                try {
                    launcher.launch("clean_run.py windmill " + browser, envVars, listener.getLogger(), build.getProject().getWorkspace()).join();
                }
                catch(IOException e){
                    //do nothing because it's alright
                }
            }

            //listener.getLogger().println("Starting Windmill Test Run\n" +cmd);
            exitCode = launcher.launch(cmd, envVars, listener.getLogger(), build.getProject().getWorkspace()).join();
        }
        

        if (exitCode != 0){
             build.setResult(Result.UNSTABLE);
        }
        
        return true;
    }

    public Descriptor<Builder> getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    public static final class DescriptorImpl extends Descriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean cleanup = true;

        DescriptorImpl() {
            super(WindmillBuilder.class);
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
//        public void doCheckBrowser(StaplerRequest req, StaplerResponse rsp, @QueryParameter final String value) throws IOException, ServletException {
//            new FormFieldValidator(req,rsp,null) {
//                /**
//                 * The real check goes here. In the end, depending on which
//                 * method you call, the browser shows text differently.
//                 */
//                protected void check() throws IOException, ServletException {
//                    if(value.length()==0)
//                        error("Please set a name");
//                    else
//                    if(value.length()<1)
//                        warning("Isn't the name too short?");
//                    else
//                        ok();
//
//                }
//            }.process();
//        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Windmill Test";
        }

        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            // to persist global configuration information,
            // set that to properties and call save().
            cleanup = o.getBoolean("cleanup");
            save();
            return super.configure(req);
        }

        /**
         * This method returns true if the global configuration says we should speak
         */
        public boolean cleanup() {
            return cleanup;
        }

    }
}

