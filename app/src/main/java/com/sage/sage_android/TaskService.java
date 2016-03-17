package com.sage.sage_android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sage.sage_android.data.AndroidNode;
import com.sage.sage_android.data.AppInit;
import com.sage.sage_android.data.Storage;
import com.sage.sage_android.data.Task;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by cyberpirate on 3/5/2016.
 */
public class TaskService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        AppInit.appInit(getApplicationContext());
        taskRunner = new TaskRunner();
        taskRunner.start();
    }

    @Override
    public void onDestroy() {
        taskRunner.stopRunning();
        taskRunner = null;
    }

    TaskRunner taskRunner;

    private class TaskRunner extends Thread {

        private AtomicBoolean running = new AtomicBoolean(true);
        private AtomicBoolean paused = new AtomicBoolean(false);
        private Object syncObject = new Object();

        public void stopRunning() {
            running.set(false);
        }

        public void setPaused(boolean p) {
            paused.set(p);
            syncObject.notify();
        }

        public void run() {

            while(running.get()) {
                downloadAndRunTask();

                while(paused.get()) {
                    synchronized (syncObject) {
                        try {
                            syncObject.wait();
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        private void downloadAndRunTask() {
            try {

                if("-1".equals(Storage.getInstance().nodeId)) {
                    checkAndroidNode();
                }

                Task taskData = getNextReady();
                Task.DecodedDex dex = taskData.getDecodedDex();

                byte[] result = dex.runSageTask();
            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        private Task getNextReady() throws IOException {
            URL url = new URL("http://sage-ws.ddns.net:8080/sage/alpaca/jobs/nextReady/" + Storage.getInstance().nodeId);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("GoogleToken", Storage.getInstance().googleToken);
            conn.setRequestProperty("SageToken", Storage.getInstance().sageToken);

            conn.connect();

            InputStream is = conn.getInputStream();

            Gson gson = new GsonBuilder().create();
            return gson.fromJson(new InputStreamReader(is), Task.class);
        }

        private void sendAndroidNode() throws IOException {
            URL url = new URL("http://sage-ws.ddns.net:8080/sage/alpaca/androidNodes");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("GoogleToken", Storage.getInstance().googleToken);
            conn.setRequestProperty("SageToken", Storage.getInstance().sageToken);

            conn.connect();

            Gson gson = new GsonBuilder().create();
            OutputStream os = conn.getOutputStream();
            IOUtils.write(gson.toJson(new AndroidNode()), os);
            os.close();

            InputStream is = conn.getInputStream();
            String id = IOUtils.toString(is);

            Storage.getInstance().nodeId = id;
            Storage.saveStorage();
        }

        private void checkAndroidNode() throws IOException {
            URL url = new URL("http://sage-ws.ddns.net:8080/sage/alpaca/androidNodes");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("GoogleToken", Storage.getInstance().googleToken);
            conn.setRequestProperty("SageToken", Storage.getInstance().sageToken);

            conn.connect();

            Gson gson = new GsonBuilder().create();
            InputStream is = conn.getInputStream();

            AndroidNode[] nodes = gson.fromJson(new InputStreamReader(is), AndroidNode[].class);

            for(AndroidNode node : nodes) {
                if(AndroidNode.getUUID().equals(node.androidId)) {
                    Storage.getInstance().nodeId = node.nodeId;
                    Storage.saveStorage();
                    return;
                }
            }

            sendAndroidNode();
        }
    }

}
