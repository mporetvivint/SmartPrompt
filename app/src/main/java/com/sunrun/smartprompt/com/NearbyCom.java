package com.sunrun.smartprompt.com;


import static com.google.android.gms.common.util.IOUtils.copyStream;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.sunrun.smartprompt.model.Status;


import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;


public class NearbyCom {

    ConnectionLifecycleCallback connectionCallback;
    ReceivePayloadCallback payloadCallback;
    ArrayList <String> endpoints;
    Context context;
    PipedInputStream inputStream;
    PipedOutputStream outputStream;


    public NearbyCom(Context context) {
       connectionCallback = null;
       payloadCallback = new ReceivePayloadCallback();
       endpoints = new ArrayList<>();
       this.context = context;
    }

    public void startAdvertising() {

        endpoints.clear();

        //setup pipe streams
        inputStream = new PipedInputStream();
        outputStream = new PipedOutputStream();
        try {
            outputStream.connect(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        connectionCallback = new ConnectionLifecycleCallback() {
            @Override
            public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
                switch (result.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_OK:
                        // We're connected! Can now start sending and receiving data.

                        //Start datastream if this is the first connected client
                        if(endpoints.size() == 0){
                            startDataStream();
                        }
                        endpoints.add(endpointId);
                        Payload streamPayload = Payload.fromStream(inputStream);
                        Nearby.getConnectionsClient(context).sendPayload(endpointId,streamPayload);
                        break;
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        // The connection was rejected by one or both sides.
                        break;
                    case ConnectionsStatusCodes.STATUS_ERROR:
                        // The connection broke before it was able to be accepted.
                        break;
                    default:
                        // Unknown status code
                }
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                endpoints.remove(endpointId);
                //Stop data stream if there are no connected clients
                if(endpoints.size() == 0){
                    stopDataStream();
                }
            }
        };



        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build();


        Nearby.getConnectionsClient(context)
                .startAdvertising(
                        "SmartPromptControl", "com.sunrun.smartprompt", connectionCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            Log.d("Nearby", "We're Advertising");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            Log.d("Nearby", "We're NOT Advertising");

                        });
    }

    public void startDiscovery(){

        endpoints.clear();

        connectionCallback = new ConnectionLifecycleCallback() {
            @Override
            public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback);
                Log.d("Nearby","Connection Initiated");

            }

            @Override
            public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
                Log.d("Nearby","Connection Result: " + result.getStatus().toString());
                switch (result.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_OK:
                        // We're connected! Can now start sending and receiving data.
                        endpoints.clear();
                        endpoints.add(endpointId);
                        break;
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        // The connection was rejected by one or both sides.
                        break;
                    case ConnectionsStatusCodes.STATUS_ERROR:
                        // The connection broke before it was able to be accepted.
                        break;
                    default:
                        // Unknown status code
                }
            }

            @Override
            public void onDisconnected(@NonNull String s) {

            }
        };

        //setup callback for discoverer
        EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
            @Override
            public void onEndpointFound(@NonNull String advertiserID, @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {
                Log.d("Nearby","Endpoint Found");
                // An endpoint was found. We request a connection to it.
                Nearby.getConnectionsClient(context)
                        .requestConnection("SmartPromptPromptr", advertiserID, connectionCallback)
                        .addOnSuccessListener(
                                (Void unused) -> {
                                    // We successfully requested a connection. Now both sides
                                    // must accept before the connection is established.
                                    Nearby.getConnectionsClient(context).acceptConnection(advertiserID, payloadCallback);
                                })
                        .addOnFailureListener(
                                (Exception e) -> {
                                    // Nearby Connections failed to request the connection.
                                });
            }

            @Override
            public void onEndpointLost(@NonNull String endpointID) {
                endpoints.remove(endpointID);
            }
        };

        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build();
        Nearby.getConnectionsClient(context)
                .startDiscovery("com.sunrun.smartprompt", endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're discovering!
                            Log.d("Nearby", "We're Discovering!");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            Log.d("Nearby", "We're Not Discovering!");
                        });
    }

    public void stopDiscovery(){
        Nearby.getConnectionsClient(context).stopDiscovery();
    }

    public void stopAdvertising(){
        Nearby.getConnectionsClient(context).stopAdvertising();
    }


    public void closeAll(){
        stopAdvertising();
        stopDiscovery();
        Log.d("Nearby", "ending nearby connections. Goodbye!");
    }

    //Stream Payload Callback Class
    static class ReceivePayloadCallback extends PayloadCallback {
        private final SimpleArrayMap<Long, Thread> backgroundThreads = new SimpleArrayMap<>();

        private static final long READ_STREAM_IN_BG_TIMEOUT = 5000;

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, PayloadTransferUpdate update) {
            if (backgroundThreads.containsKey(update.getPayloadId())
                    && update.getStatus() != PayloadTransferUpdate.Status.IN_PROGRESS) {
                backgroundThreads.get(update.getPayloadId()).interrupt();
            }
        }

        @Override
        public void onPayloadReceived(@NonNull String endpointId, Payload payload) {

            //Receiving Stream Payloads
            if (payload.getType() == Payload.Type.STREAM) {
                // Read the available bytes in a while loop to free the stream pipe in time. Otherwise, the
                // bytes will block the pipe and slow down the throughput.
                Thread backgroundThread =
                        new Thread() {
                            @Override
                            public void run() {
                                //TODO: Time every part of this loop to see whre latency is coming from

                                InputStream inputStream = payload.asStream().asInputStream();
                                long lastRead = SystemClock.elapsedRealtime();
                                ArrayList<Byte> scroll_position_raw = new ArrayList<>();
                                int scrl_pos = 0;
                                while (!Thread.interrupted()) {
                                    long time_taken = SystemClock.elapsedRealtime() - lastRead;
                                    Log.d("Receiver", "for took: " + time_taken);
                                    if ((SystemClock.elapsedRealtime() - lastRead) >= READ_STREAM_IN_BG_TIMEOUT) {
                                        Log.e("Receiver", "Read data from stream but timed out.");
                                        break;
                                    }

                                    try {
                                        int availableBytes = inputStream.available();
                                        if (availableBytes > 4) {
                                            byte[] bytes = new byte[availableBytes];
                                             if (inputStream.read(bytes) == availableBytes) {
                                                lastRead = SystemClock.elapsedRealtime();

                                                for (int i = 0; i < availableBytes; i++){
                                                    if(bytes[i] == -128 && bytes[i+1] == 0){
                                                        scrl_pos = ((bytes[i+1] & 0xFF) << 24) |
                                                                ((bytes[i+2] & 0xFF) << 16) |
                                                                ((bytes[i+3] & 0xFF) << 8) |
                                                                ((bytes[i + 4] & 0xFF));
                                                        Status.setScroll_position(scrl_pos);
                                                        i+=4;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (IOException e) {
                                        Log.e("MyApp", "Failed to read bytes from InputStream.", e);
                                        break;
                                    } // try-catch
                                } // while
                            }
                        };
                backgroundThread.start();
                backgroundThreads.put(payload.getId(), backgroundThread);
            }
        }

        private byte[] truncate(byte[] array, int newLength) {
            if (array.length < newLength) {
                return array;
            } else {
                byte[] truncated = new byte[newLength];
                System.arraycopy(array, 0, truncated, 0, newLength);

                return truncated;
            }
        }
    }


    //Background Thread to send dataStream
    final private Handler handler = new Handler();
    final private Handler inputHandler = new Handler();
    final private int delay = 4; //milliseconds
    byte[] send_bytes = new byte[5];
    private final Runnable outputStreamRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                System.arraycopy(ByteBuffer.allocate(4).putInt(Status.getScroll_position()).array(),
                        0,send_bytes,1,4);
                send_bytes[0] = -128;
                outputStream.write(send_bytes);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            handler.postDelayed(this, delay);
        }
    };
    public void startDataStream(){
        handler.postDelayed(outputStreamRunnable, delay);
//        inputHandler.postDelayed(inputStreamRunnable,delay);
    }
    public void stopDataStream(){
        handler.removeCallbacks(outputStreamRunnable);
//        inputHandler.removeCallbacks(inputStreamRunnable);
    }

    private final Runnable inputStreamRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while(inputStream.available()>0){
                    Log.d("Stream",Integer.toString(inputStream.read()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputHandler.postDelayed(this, delay);
        }
    };
}
