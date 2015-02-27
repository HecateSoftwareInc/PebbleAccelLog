package com.hecate.pebbleaccellog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

/**
 * Created by Josh on 2/19/2015.
 */
public class AccelData
{
    public byte did_vibrate;
    public long timestamp;
    public Date time;

    public short x;
    public short y;
    public short z;

    public AccelData()
    {

    }

    public static AccelData createFromBytes(byte[] bytes) throws IOException
    {
        final AccelData data = new AccelData();
        final ByteBuffer buf = ByteBuffer.wrap(bytes);

        buf.order(ByteOrder.LITTLE_ENDIAN);
        data.x = buf.getShort();
        data.y = buf.getShort();
        data.z = buf.getShort();
        data.did_vibrate = buf.get();
        data.timestamp = buf.getLong();

        return data;
    }

    public static AccelData createFromCSV(String line)
    {
        AccelData data = null;

        String[] s_data = line.split(",");
        if(s_data.length == 5)
        {
            data = new AccelData();
            data.did_vibrate = Byte.parseByte(s_data[0]);
            data.timestamp = Long.parseLong(s_data[1]);
            data.time = new Date(data.timestamp);
            data.x = Short.parseShort(s_data[2]);
            data.y = Short.parseShort(s_data[3]);
            data.z = Short.parseShort(s_data[4]);
        }

        return data;
    }
}
