package mil.nga.giat.osm.mapreduce;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import mil.nga.giat.geowave.store.data.field.BasicWriter;
import mil.nga.giat.osm.accumulo.osmschema.Schema;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import org.openstreetmap.osmosis.osmbinary.Fileformat.Blob;
import org.openstreetmap.osmosis.osmbinary.Osmformat.*;
import org.openstreetmap.osmosis.osmbinary.Osmformat.Relation.MemberType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.InflaterInputStream;


public class OSMPBFMapper extends Mapper<LongWritable, BytesWritable, Text, Mutation> {

    private static final Logger log = LoggerFactory.getLogger(OSMPBFMapper.class);
    private static final HashFunction _hf = Hashing.murmur3_128(1);

    private static final BasicWriter.LongWriter _longWriter = new BasicWriter.LongWriter();
    private static final BasicWriter.IntWriter _intWriter = new BasicWriter.IntWriter();
    private static final BasicWriter.StringWriter _stringWriter = new BasicWriter.StringWriter();
    private static final BasicWriter.DoubleWriter _doubleWriter = new BasicWriter.DoubleWriter();
    private static final BasicWriter.BooleanWriter _booleanWriter = new BasicWriter.BooleanWriter();
    private static final BasicWriter.CalendarWriter _calendarWriter = new BasicWriter.CalendarWriter();

    private ColumnVisibility _visibility = new ColumnVisibility("public".getBytes(Schema.CHARSET));
    private Text _tableName = new Text("OSM");

    private static double parseLat(long degree, int granularity, long lat_offset) {
        return .000000001 * (granularity * degree + lat_offset);
    }

    private static double parseLon(long degree, int granularity, long lon_offset) {
        return .000000001 * (granularity * degree + lon_offset);
    }

    private static long parseTimestamp(long timestamp, int granularity) {
        return timestamp * granularity;
    }

    private static String getString(int id, StringTable table) {
        return table.getS(id).toStringUtf8();
    }

    private byte[] getIdHash(long id) {
        return _hf.hashLong(id).asBytes();
    }

    private void put(Mutation m, byte[] cf, byte[] cq, long val) {
        m.put(cf, cq, _visibility, _longWriter.writeField(val));
    }

    private void put(Mutation m, byte[] cf, byte[] cq, int val) {
        m.put(cf, cq, _visibility, _intWriter.writeField(val));
    }

    private void put(Mutation m, byte[] cf, byte[] cq, double val) {
        m.put(cf, cq, _visibility, _doubleWriter.writeField(val));
    }

    private void put(Mutation m, byte[] cf, byte[] cq, String val) {
        m.put(cf, cq, _visibility, _stringWriter.writeField(val));
    }

    private void put(Mutation m, byte[] cf, byte[] cq, boolean val) {
        m.put(cf, cq, _visibility, _booleanWriter.writeField(val));
    }

    private void put(Mutation m, byte[] cf, byte[] cq, Calendar val) {
        m.put(cf, cq, _visibility, _calendarWriter.writeField(val));
    }

    @Override
    public void setup(Context context) throws IOException, InterruptedException {
        _tableName = new Text(context.getConfiguration().get("tableName"));
        _visibility = new ColumnVisibility(context.getConfiguration().get("osmVisibility").getBytes(Schema.CHARSET));
    }

    @Override
    public void map(LongWritable key, BytesWritable value, Context context) throws IOException, InterruptedException {

        Blob blob = Blob.parseFrom(new ByteArrayInputStream(value.getBytes(), 0, value.getLength()));

        InputStream blobData;
        if (blob.hasZlibData()) {
            blobData = new InflaterInputStream(blob.getZlibData().newInput());
        } else {
            blobData = blob.getRaw().newInput();
        }

        PrimitiveBlock pb = PrimitiveBlock.parseFrom(blobData);
        blobData.close();


        for (PrimitiveGroup pg : pb.getPrimitivegroupList()) {

            //nodes
            for (Node node : pg.getNodesList()) {
                Mutation m = new Mutation(getIdHash(node.getId()));

                put(m, Schema.CF.NODE, Schema.CQ.ID, node.getId());
                put(m, Schema.CF.NODE, Schema.CQ.LONGITUDE, parseLon(node.getLon(), pb.getGranularity(), pb.getLonOffset()));
                put(m, Schema.CF.NODE, Schema.CQ.LATITUDE, parseLat(node.getLat(), pb.getGranularity(), pb.getLatOffset()));

                if (node.getInfo() != null) {
                    put(m, Schema.CF.NODE, Schema.CQ.VERSION, node.getInfo().getVersion());
                    put(m, Schema.CF.NODE, Schema.CQ.TIMESTAMP, parseTimestamp(node.getInfo().getTimestamp(), pb.getDateGranularity()));
                    put(m, Schema.CF.NODE, Schema.CQ.CHANGESET, node.getInfo().getChangeset());
                    put(m, Schema.CF.NODE, Schema.CQ.USER_ID, node.getInfo().getUid());
                    put(m, Schema.CF.NODE, Schema.CQ.USER_TEXT, getString(node.getInfo().getUserSid(), pb.getStringtable()));
                    if (node.getInfo().hasVisible()) {
                        put(m, Schema.CF.NODE, Schema.CQ.OSM_VISIBILITY, node.getInfo().getVisible());
                    } else {
                        //put(m, Schema.CF.NODE, Schema.CQ.OSM_VISIBILITY, true);
                    }
                }

                for (int k = 0; k < node.getKeysCount(); k++) {
                    String keyString = getString(node.getKeys(k), pb.getStringtable());
                    String keyValue = getString(node.getVals(k), pb.getStringtable());
                    put(m, Schema.CF.NODE_TAG, keyString.getBytes(Schema.CHARSET), keyValue);
                }
                context.write(_tableName, m);
            }

            //ways
            for (Way way : pg.getWaysList()) {
                Mutation m = new Mutation(getIdHash(way.getId()));
                put(m, Schema.CF.WAY, Schema.CQ.ID, way.getId());

                List<String> allRefs = new ArrayList<>();
                long lastRef = 0;
                for (long r : way.getRefsList()) {
                    lastRef += r;
                    allRefs.add(String.valueOf(lastRef));
                }

                //todo - convert to avro
                put(m, Schema.CF.WAY, Schema.CQ.REFERENCES, StringUtils.join(allRefs, ","));

                for (int k = 0; k < way.getKeysCount(); k++) {
                    String keyString = getString(way.getKeys(k), pb.getStringtable());
                    String keyValue = getString(way.getVals(k), pb.getStringtable());
                    put(m, Schema.CF.WAY_TAG, keyString.getBytes(Schema.CHARSET), keyValue);
                }

                if (way.getInfo() != null) {
                    put(m, Schema.CF.WAY, Schema.CQ.VERSION, way.getInfo().getVersion());
                    put(m, Schema.CF.WAY, Schema.CQ.TIMESTAMP, way.getInfo().getTimestamp());
                    put(m, Schema.CF.WAY, Schema.CQ.CHANGESET, way.getInfo().getChangeset());
                    put(m, Schema.CF.WAY, Schema.CQ.USER_ID, way.getInfo().getUid());
                    put(m, Schema.CF.WAY, Schema.CQ.USER_TEXT, getString(way.getInfo().getUserSid(), pb.getStringtable()));

                    if (way.getInfo().hasVisible()) {
                        put(m, Schema.CF.WAY, Schema.CQ.OSM_VISIBILITY, way.getInfo().getVisible());
                    } else {
                        //put(m, Schema.CF.WAY, _visibility, true);
                    }
                }

                context.write(_tableName, m);
            }


            for (Relation rel : pg.getRelationsList()) {

                long lastMemid = 0;
                Mutation m = new Mutation(getIdHash(rel.getId()));

                put(m,Schema.CF.RELATION, Schema.CQ.ID, rel.getId());

                if (rel.getInfo() != null) {
                    put(m, Schema.CF.RELATION, Schema.CQ.VERSION, rel.getInfo().getVersion());
                    put(m, Schema.CF.RELATION, Schema.CQ.TIMESTAMP, rel.getInfo().getTimestamp());
                    put(m, Schema.CF.RELATION, Schema.CQ.CHANGESET, rel.getInfo().getChangeset());
                    put(m, Schema.CF.RELATION, Schema.CQ.USER_ID, rel.getInfo().getUid());
                    put(m, Schema.CF.RELATION, Schema.CQ.USER_TEXT, getString(rel.getInfo().getUserSid(), pb.getStringtable()));

                    if (rel.getInfo().hasVisible()) {
                        put(m, Schema.CF.RELATION, Schema.CQ.OSM_VISIBILITY, rel.getInfo().getVisible());
                    } else {
                        //put(m, Schema.CF.RELATION, Schema.CQ.OSM_VISIBILITY, true);
                    }
                }
                for (int k = 0; k < rel.getKeysCount(); k++) {
                    String keyString = getString(rel.getKeys(k), pb.getStringtable());
                    String keyValue = getString(rel.getVals(k), pb.getStringtable());
                    put(m, Schema.CF.RELATION_TAG, keyString.getBytes(Schema.CHARSET), keyValue);
                }

                //parallel arrays for rols/memids/types

                for (int i = 0; i < rel.getRolesSidCount(); i++) {
                    lastMemid += rel.getMemids(i);
                    put(m, Schema.CF.RELATION, (Schema.CQ.REFERENCE_ROLEID_PREFIX + "_" + i).getBytes(Schema.CHARSET), getString(rel.getRolesSid(i), pb.getStringtable()));
                    put(m, Schema.CF.RELATION, (Schema.CQ.REFERENCE_MEMID_PREFIX + "_" + i).getBytes(Schema.CHARSET), lastMemid);
                    String memberType = null;
                    MemberType mt = rel.getTypes(i);
                    if (mt.getNumber() == 0) {
                        memberType = "NODE";
                    } else if (mt.getNumber() == 1) {
                        memberType = "WAY";
                    } else {
                        memberType = "RELATION";
                    }
                    put(m, Schema.CF.RELATION, (Schema.CQ.REFERENCE_TYPE_PREFIX + "_" + i).getBytes(Schema.CHARSET), memberType);
                }
                context.write(_tableName, m);
            }

            if (pg.hasDense()) {

                DenseNodes dn = pg.getDense();

                long lastId = 0;
                long lastLat = 0;
                long lastLon = 0;
                long lastTimestamp = 0;
                long lastChangeset = 0;
                int lastUid = 0;
                int lastSid = 0;

                int tagLocation = 0;

                boolean hasVisibility = dn.getDenseinfo().getVisibleList() != null && dn.getDenseinfo().getVisibleList().size() > 0;

                for (int i = 0; i < dn.getIdCount(); i++) {


                    //it's all relative encoded
                    lastId += dn.getId(i);
                    lastLat += dn.getLat(i);
                    lastLon += dn.getLon(i);

                    Mutation m = new Mutation(getIdHash(lastId));

                    put(m, Schema.CF.NODE, Schema.CQ.ID, lastId);
                    put(m, Schema.CF.NODE, Schema.CQ.LATITUDE, parseLat(lastLat, pb.getGranularity(), pb.getLatOffset()));
                    put(m, Schema.CF.NODE, Schema.CQ.LONGITUDE, parseLon(lastLon, pb.getGranularity(), pb.getLonOffset()));

                    //Weird spec - keys and values are mashed sequentially, and end of data for a particular node is denoted by a value of 0
                    if (dn.getKeysValsCount() > 0) {
                        while (dn.getKeysVals(tagLocation) != 0) {
                            String tagK = getString(dn.getKeysVals(tagLocation), pb.getStringtable());
                            tagLocation++;
                            String tagV = getString(dn.getKeysVals(tagLocation), pb.getStringtable());
                            tagLocation++;
                            put(m, Schema.CF.NODE_TAG, tagK.getBytes(Schema.CHARSET), tagV);
                        }
                    }

                    if (dn.getDenseinfo() != null) {

                        lastTimestamp += dn.getDenseinfo().getTimestamp(i);
                        lastChangeset += dn.getDenseinfo().getChangeset(i);
                        lastUid += dn.getDenseinfo().getUid(i);
                        lastSid += dn.getDenseinfo().getUserSid(i);

                        put(m, Schema.CF.NODE, Schema.CQ.VERSION, dn.getDenseinfo().getVersion(i));
                        put(m, Schema.CF.NODE, Schema.CQ.TIMESTAMP, parseTimestamp(lastTimestamp, pb.getDateGranularity()));
                        put(m, Schema.CF.NODE, Schema.CQ.CHANGESET, lastChangeset);
                        put(m, Schema.CF.NODE, Schema.CQ.USER_ID, lastUid);
                        put(m, Schema.CF.NODE, Schema.CQ.USER_TEXT, getString(lastSid, pb.getStringtable()));

                        if (hasVisibility) {
                            put(m, Schema.CF.NODE, Schema.CQ.OSM_VISIBILITY, dn.getDenseinfo().getVisible(i));
                        } else {
                            //put(m, Schema.CF.NODE, Schema.CQ.OSM_VISIBILITY, true);
                        }
                    }
                    context.write(_tableName, m);
                }

            }
        }
    }
}
