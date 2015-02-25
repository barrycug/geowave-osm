package mil.nga.giat.osm.mapreduce;

import mil.nga.giat.osm.accumulo.osmschema.Schema;
import mil.nga.giat.osm.types.generated.LongArray;
import mil.nga.giat.osm.types.generated.Node;
import mil.nga.giat.osm.types.generated.Primitive;
import mil.nga.giat.osm.types.generated.Way;
import org.apache.accumulo.core.data.Mutation;
import org.apache.avro.mapred.AvroKey;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.NullWritable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class OSMWayMapper extends OSMMapperBase<Way> {


    @Override
    public void map(AvroKey<Way> key, NullWritable value, Context context) throws IOException, InterruptedException {

        Way way = key.datum();
        Primitive p = way.getCommon();

        Mutation m = new Mutation(getIdHash(p.getId()));

        put(m, Schema.CF.WAY, Schema.CQ.ID, p.getId());


        List<Long> allRefs = new ArrayList<>();
        long lastRef = 0;
        for (long r : way.getNodes()) {
            lastRef += r;
            allRefs.add(lastRef);
        }
        LongArray lr = new LongArray();
        lr.setIds(allRefs);

        put(m, Schema.CF.WAY, Schema.CQ.REFERENCES, lr);


        if (!Long.valueOf(0).equals(p.getVersion())) {
            put(m, Schema.CF.WAY, Schema.CQ.VERSION, p.getVersion());
        }

        if (!Long.valueOf(0).equals(p.getTimestamp())) {
            put(m, Schema.CF.WAY, Schema.CQ.TIMESTAMP, p.getTimestamp());
        }

        if (!Long.valueOf(0).equals(p.getChangesetId())) {
            put(m, Schema.CF.WAY, Schema.CQ.CHANGESET, p.getChangesetId());
        }

        if (!Long.valueOf(0).equals(p.getUserId())) {
            put(m, Schema.CF.WAY, Schema.CQ.USER_ID, p.getUserId());
        }


        put(m, Schema.CF.WAY, Schema.CQ.USER_TEXT, p.getUserName());
        put(m, Schema.CF.WAY, Schema.CQ.OSM_VISIBILITY, p.getVisible());

        for (Map.Entry<CharSequence, CharSequence> kvp : p.getTags().entrySet()) {
            put(m, Schema.CF.WAY, kvp.getKey().toString().getBytes(Schema.CHARSET), kvp.getValue().toString());
        }


        context.write(_tableName, m);

    }
}