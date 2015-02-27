package com.hecate.pebbleaccellog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;


public class Main_PebbleAccelLog extends ActionBarActivity {

    private final static UUID PEBBLE_APP_UUID = UUID.fromString("609a1291-dc9b-49a6-ab29-978ce04e7a1d");

    // App elements
    private int sampleRate = -1;
    private int powerStart = -1;
    private int powerEnd = -1;
    private PebbleKit.PebbleDataReceiver dataReceiver = null;
    private PebbleKit.PebbleDataLogReceiver dataLoggingReceiver = null;
    private TextView tRate;
    private TextView tSample;
    private ListView listLogs;
    private Button bToggleLogging;
    private Button bExportLog;
    private Button bDeleteLog;
    private File externalDirectory;
    private File tempDirectory;
    private String logFileName;
    private String logFullPath;
    private FileWriter logFile;
    private String selectedLog;
    private boolean logging = false;
    private int sampleCount = 0;
    private long sampleT0 = 0;

    private GraphView gLog;
    private PointsGraphSeries<DataPoint> sX;
    private PointsGraphSeries<DataPoint> sY;
    private PointsGraphSeries<DataPoint> sZ;

    ArrayList<String> externalFiles;

    private File getLogStorageDir(String logsName)
    {
        // Get the directory for the user's public pictures directory.
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), logsName);
        if (!file.mkdirs())
        {

        }
        return file;
    }

    private void plotSelectedLog()
    {
        try
        {
            ArrayList<AccelData> allData = new ArrayList<AccelData>();
            FileReader copy = new FileReader(new File(externalDirectory.getAbsolutePath(), selectedLog));
            BufferedReader readCopy = new BufferedReader(copy);

            String line = "";
            int r = 0;
            long t0 = 0;
            int freq = -1;
            int powStart = -1;
            int powEnd = -1;
            int countLines = 0;
            while((line = readCopy.readLine()) != null)
            {
                countLines++;
                int i = line.indexOf(',');
                int type = Integer.parseInt(line.substring(0, i));
                String lineData = line.substring(i + 1);

                switch(type)
                {
                    case 5000:
                        AccelData data = AccelData.createFromCSV(lineData);
                        long t = t0;
                        if(r == 0)
                            t0 = data.timestamp;

                        allData.add(data);
                        r++;
                        break;
                    case 5001:
                        String[] split = lineData.split(",");
                        freq = Integer.parseInt(split[0]);
                        powStart = Integer.parseInt(split[1]);
                        break;
                    case 5002:
                        powEnd = Integer.parseInt(lineData);
                        break;
                }
            }
            readCopy.close();
            copy.close();

            DataPoint[] dX = new DataPoint[allData.size()];
            DataPoint[] dY = new DataPoint[allData.size()];
            DataPoint[] dZ = new DataPoint[allData.size()];
            for(int i = 0; i < allData.size(); i++)
            {
                AccelData data = allData.get(i);
                double t = (data.timestamp - t0) / 1000.0;
                dX[i] = new DataPoint(t, data.x / 1000.0);
                dY[i] = new DataPoint(t, data.y / 1000.0);
                dZ[i] = new DataPoint(t, data.z / 1000.0);
            }
            gLog.setTitle(selectedLog + ", " + freq + " Hz");
            sX.resetData(dX);
            sY.resetData(dY);
            sZ.resetData(dZ);

            gLog.getViewport().setMinX(sX.getLowestValueX());
            gLog.getViewport().setMaxX(sX.getHighestValueX());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clearPlot()
    {
        gLog.setTitle("");
        sX.resetData(new DataPoint[] {});
        sY.resetData(new DataPoint[] {});
        sZ.resetData(new DataPoint[] {});
    }

    private void updateLogList()
    {
        externalFiles.clear();
        File[] files = externalDirectory.listFiles();
        for(File file : files)
            externalFiles.add(file.getName());
        ArrayAdapter<String> adapter = (ArrayAdapter<String>)listLogs.getAdapter();
        adapter.notifyDataSetChanged();
    }

    private void clearTempFiles()
    {
        File[] tempFiles = tempDirectory.listFiles();
        for(int i = 0; i < tempFiles.length; i++)
        {
            File file = tempFiles[i];
            file.delete();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_pebble_accel_log);

        //Initialize log directories
        externalDirectory = getLogStorageDir("Pebble_AccelLog");
        tempDirectory = getLogStorageDir("Pebble_AccelLog_Temp");
        externalFiles = new ArrayList<String>();

        //Initialize references to UI elements
        tRate = (TextView)findViewById(R.id.tRate);
        tSample = (TextView)findViewById(R.id.tSample);
        listLogs = (ListView)findViewById(R.id.listLogs);
        listLogs.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listLogs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if(logging) return;

                selectedLog = listLogs.getItemAtPosition(position).toString();
                plotSelectedLog();

                for(int i = 0; i < parent.getChildCount(); i++)
                {
                    parent.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                }
                view.setBackgroundColor(Color.LTGRAY);
            }
        });

        listLogs.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, externalFiles));
        updateLogList();

        bToggleLogging = (Button)findViewById(R.id.bToggleLogging);
        bToggleLogging.setText("Start");
        bToggleLogging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                logging = !logging;

                bExportLog.setEnabled(!logging);
                bDeleteLog.setEnabled(!logging);
                listLogs.setEnabled(!logging);

                if(logging)
                {
                    sampleCount = 0;
                    bToggleLogging.setText("Stop");
                    logFileName = new SimpleDateFormat("yyyy_MM_dd-hh_mm_ss").format(new Date());
                    logFullPath = externalDirectory.getAbsolutePath() + "/" + logFileName;

                    try {
                        logFile = new FileWriter(logFullPath);
                        logFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if(dataReceiver == null)
                    {
                        dataReceiver = new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID)
                        {
                            @Override
                            public void receiveData(Context context, int i, PebbleDictionary data)
                            {
                                int messageType = data.getInteger(0).intValue();
                                switch(messageType)
                                {
                                    case 5000:
                                        break;
                                    case 5001:
                                        sampleRate = data.getInteger(1).intValue();
                                        powerStart = data.getInteger(2).intValue();
                                        try {
                                            logFile = new FileWriter(logFullPath, true);
                                            PrintWriter out = new PrintWriter(logFile);
                                            out.println(5001 + "," + sampleRate + "," + powerStart);
                                            tRate.setText(sampleRate + " Hz");
                                            gLog.setTitle(logFileName + ", " + sampleRate + " Hz");
                                            out.close();
                                            logFile.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    case 5002:
                                        powerEnd = data.getInteger(1).intValue();
                                        try {
                                            logFile = new FileWriter(logFullPath, true);
                                            PrintWriter out = new PrintWriter(logFile);
                                            out.println(5002 + "," + powerEnd);
                                            out.close();
                                            logFile.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                }
                            }
                        };
                    }

                    updateLogList();
                    clearPlot();
                    gLog.setTitle(logFileName);

                    if (dataLoggingReceiver == null) {
                        dataLoggingReceiver = new PebbleKit.PebbleDataLogReceiver(PEBBLE_APP_UUID) {
                            @Override
                            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                                try {
                                    AccelData accelData = AccelData.createFromBytes(data);
                                    logFile = new FileWriter(logFullPath, true);

                                    PrintWriter out = new PrintWriter(logFile);
                                    out.println(5000 + "," + accelData.did_vibrate + "," + accelData.timestamp + "," + accelData.x + "," + accelData.y + "," + accelData.z);
                                    out.close();
                                    logFile.close();

                                    sampleCount++;
                                    if(sampleCount == 1)
                                        sampleT0 = accelData.timestamp;

                                    double t = (accelData.timestamp - sampleT0) / 1000.0;
                                    tSample.setText(sampleCount + ":" + accelData.x + ", " + accelData.y + ", " + accelData.z);
                                    sX.appendData(new DataPoint(t, accelData.x / 1000.0), false, Integer.MAX_VALUE);
                                    sY.appendData(new DataPoint(t, accelData.y / 1000.0), false, Integer.MAX_VALUE);
                                    sZ.appendData(new DataPoint(t, accelData.z / 1000.0), false, Integer.MAX_VALUE);
                                    gLog.getViewport().setMinX(Math.max(sX.getHighestValueX() - 60, 0));
                                    gLog.getViewport().setMaxX(sX.getHighestValueX());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                    }

                    PebbleKit.registerReceivedDataHandler(Main_PebbleAccelLog.this, dataReceiver);
                    PebbleKit.registerDataLogReceiver(Main_PebbleAccelLog.this, dataLoggingReceiver);
                }
                else
                {
                    bToggleLogging.setText("Start");
                    if(dataReceiver != null)
                    {
                        unregisterReceiver(dataReceiver);
                    }
                    if(dataLoggingReceiver != null)
                    {
                        unregisterReceiver(dataLoggingReceiver);
                    }
                }
            }
        });
        bExportLog = (Button)findViewById(R.id.bExportLog);
        bExportLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if(logging) return;

                clearTempFiles();

                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/html");
                i.putExtra(Intent.EXTRA_SUBJECT, selectedLog);
                i.putExtra(Intent.EXTRA_TEXT   , "Logging " + selectedLog);
                File attach = new File(tempDirectory.getAbsolutePath(), "AccelLog_" + selectedLog + ".csv");

                try
                {
                    logFile = new FileWriter(attach);
                    PrintWriter out = new PrintWriter(logFile);
                    out.println(Build.MANUFACTURER + " " + Build.MODEL);
                    out.println(Build.USER);

                    DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                    Date now = new Date();
                    out.println(dateFormat.format(now));


                    ArrayList<AccelData> allData = new ArrayList<AccelData>();
                    FileReader copy = new FileReader(new File(externalDirectory.getAbsolutePath(), selectedLog));
                    BufferedReader readCopy = new BufferedReader(copy);

                    String line = "";
                    long t0 = 0;
                    int freq = -1;
                    int powStart = -1;
                    int powEnd = -1;
                    while((line = readCopy.readLine()) != null)
                    {
                        int i_type = line.indexOf(',');
                        int type = Integer.parseInt(line.substring(0, i_type));
                        String lineData = line.substring(i_type + 1);

                        switch(type)
                        {
                            case 5000:
                                AccelData data = AccelData.createFromCSV(lineData);
                                allData.add(data);
                                break;
                            case 5001:
                                String[] split = lineData.split(",");
                                freq = Integer.parseInt(split[0]);
                                powStart = Integer.parseInt(split[1]);
                                break;
                            case 5002:
                                powEnd = Integer.parseInt(lineData);
                                break;
                        }
                    }

                    out.println(freq + " Hz");
                    out.println(powStart + ", " + powEnd);
                    out.println("t,v,x,y,z");

                    for(int d = 0; d < allData.size(); d++)
                    {
                        AccelData data = allData.get(d);

                        long t = t0;
                        if(d == 0)
                            t0 = data.timestamp;
                        t = data.timestamp - t0;
                        out.println((t/1000.0) + "," + data.did_vibrate + "," + data.x + "," + data.y + "," + data.z);
                    }

                    readCopy.close();
                    copy.close();

                    out.close();
                    logFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(attach));
                try {
                    startActivity(i);
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(Main_PebbleAccelLog.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        bDeleteLog = (Button)findViewById(R.id.bDeleteLog);
        bDeleteLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if(logging) return;

                File[] files = externalDirectory.listFiles();
                for(int i = 0; i < files.length; i++)
                {
                    File file = files[i];
                    if(file.getName().equals(selectedLog))
                        file.delete();
                }
                updateLogList();
                clearPlot();
            }
        });

        gLog = (GraphView)findViewById(R.id.gLog);
        DecimalFormat nf = (DecimalFormat)DecimalFormat.getInstance();
        nf.applyPattern("0.0");
        gLog.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));
        gLog.getLegendRenderer().setVisible(true);
        gLog.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
        gLog.getLegendRenderer().setMargin(0);
        gLog.getLegendRenderer().setSpacing(5);
        gLog.getLegendRenderer().setPadding(5);
        gLog.getGridLabelRenderer().setHorizontalAxisTitle("Time since start (s)");
        gLog.getGridLabelRenderer().setVerticalAxisTitle("Acceleration (g)");
        gLog.getViewport().setScrollable(true);
        gLog.getViewport().setXAxisBoundsManual(true);
        gLog.removeAllSeries();

        sX = new PointsGraphSeries<DataPoint>();
        sX.setColor(Color.RED);
        sX.setSize(3.5f);
        sX.setShape(PointsGraphSeries.Shape.POINT);
        sX.setTitle("x");
        gLog.addSeries(sX);

        sY = new PointsGraphSeries<DataPoint>();
        sY.setColor(Color.GREEN);
        sY.setSize(3.5f);
        sY.setShape(PointsGraphSeries.Shape.POINT);
        sY.setTitle("y");
        gLog.addSeries(sY);

        sZ = new PointsGraphSeries<DataPoint>();
        sZ.setColor(Color.BLUE);
        sZ.setSize(3.5f);
        sZ.setShape(PointsGraphSeries.Shape.POINT);
        sZ.setTitle("z");
        gLog.addSeries(sZ);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_pebble_accel_log, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
