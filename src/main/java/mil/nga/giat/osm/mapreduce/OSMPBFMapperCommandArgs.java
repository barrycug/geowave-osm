package mil.nga.giat.osm.mapreduce;

import com.beust.jcommander.Parameter;
import mil.nga.giat.geowave.accumulo.util.AccumuloUtils;


public class OSMPBFMapperCommandArgs {

    public OSMPBFMapperCommandArgs(){}

    public OSMPBFMapperCommandArgs(String zookeepers, String instanceName, String user, String pass, String osmNamespace, String visibility, String hdfsSequenceFile, String jobName){
        this.zookeepers = zookeepers;
        this.instanceName = instanceName;
        this.user = user;
        this.pass = pass;
        this.osmNamespace = osmNamespace;
        this.visibility = visibility;
        this.hdfsBasePath = hdfsSequenceFile;
        this.jobName = jobName;
    }

    @Parameter(names = {"-z","--zookeepers"} , required = false, description = "list of zookeeper:port instances, comma separated")
    public String zookeepers;

    @Parameter(names = {"-i","--instanceName"}, required = false, description = "accumulo instance name")
	public String instanceName;

    @Parameter(names = {"-au","--accumuloUser"}, required = false, description = "accumulo username")
	public String user;

    @Parameter(names = {"-ap","--accumuloPass"}, required = false, description = "accumulo password")
	public String pass;

    @Parameter(names = {"-n","--osmNamespace"}, required = false, description = "namespace for OSM data")
	public String osmNamespace;

    @Parameter(names = {"-v","--osmDefaultVisibility"}, required = false, description = "default visibility for  OSM data.")
	public String visibility = "public";

    @Parameter(names = {"-out", "--hdfsBasePath"}, required = false, description = "file to stage hdfs files to  - user must have write permissions")
	public String hdfsBasePath = "/user/" + System.getProperty("user.name") + "/osm_stage/";

    @Parameter(names = {"-jn", "--jobName"}, required = false, description = "Name of mapreduce job")
	public String jobName = "PBF Ingest (" + System.getProperty("user.name") + ")";

    protected String osmTableName = "OSM";

	public String GetQualifiedTableName(){
        return AccumuloUtils.getQualifiedTableName(osmNamespace,osmTableName);
    }

}
