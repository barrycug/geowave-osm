package mil.nga.giat.osm;

import com.beust.jcommander.Parameter;


public class OSMCommandArgs {

    protected OSMCommandArgs(){}

    protected OSMCommandArgs(String zookeepers, String instanceName, String user, String pass, String osmNamespace, String visibility, Boolean dropOSMData, String ingestDirectory, String hdfsSequenceFile, String nameNode){
        this.zookeepers = zookeepers;
        this.instanceName = instanceName;
        this.user = user;
        this.pass = pass;
        this.osmNamespace = osmNamespace;
        this.visibility = visibility;
        this.dropOSMData = dropOSMData;
        this.ingestDirectory = ingestDirectory;
        this.hdfsSequenceFile = hdfsSequenceFile;
        this.nameNode = nameNode;
    }

    @Parameter(names = {"-z","--zookeepers"} , required = false, description = "list of zookeeper:port instances, comma separated")
    protected String zookeepers;

    @Parameter(names = {"-i","--instanceName"}, required = false, description = "accumulo instance name")
    protected String instanceName;

    @Parameter(names = {"-au","--accumuloUser"}, required = false, description = "accumulo username")
    protected String user;

    @Parameter(names = {"-ap","--accumuloPass"}, required = false, description = "accumulo password")
    protected String pass;

    @Parameter(names = {"-n","--osmNamespace"}, required = false, description = "namespace for OSM data")
    protected String osmNamespace;

    @Parameter(names = {"-v","--osmDefaultVisibility"}, required = false, description = "default visibility for  OSM data.")
    protected String visibility = "public";

    @Parameter(names = {"--dropOSMData"}, required = false, description = "delete all OSM data for the specified namespace")
    protected boolean dropOSMData;

    @Parameter(names = {"-in", "--inputDirectory"}, required = false, description = "directory to ingest files from - will match all files with the .pbf extension")
    protected String ingestDirectory;

    @Parameter(names = {"-out", "--hdfsOutput"}, required = false, description = "file to stage hdfs files to  - user must have write permissions")
    protected String hdfsSequenceFile = "/user/" + System.getProperty("user.name") + "/osm_stage";

    @Parameter(names = {"-nn", "--hdfsNamenode"}, required = false, description = "hdfs namenode in the format hostname:port")
    protected String nameNode;

    protected String extension = ".pbf";

}
