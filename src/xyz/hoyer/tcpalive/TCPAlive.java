package xyz.hoyer.tcpalive;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.MessageFormat;

import android.os.AsyncTask;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class TCPAlive extends AsyncTask<Void, Void, Void> {
private final static int SOL_TCP = 6;

private final static int TCP_KEEPIDLE = 4;
private final static int TCP_KEEPINTVL = 5;
private final static int TCP_KEEPCNT = 6;

    String dstAddress;
    int dstPort;
    String response = "";
    private TCPAlive.Listener mListener;
    boolean connected;
    boolean dodisconnect;
    boolean isRunning;
    WakeLock wakelock;

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        dodisconnect = true;
    }

    public void connect() {
        if (!this.isRunning)
            this.execute();
    }

    public interface Listener {
        void onConnect();

        void onMessage(String var1);

        void onMessage(byte[] var1);

        void onDisconnect(int var1, String var2);

        void onError(Exception var1);
    }

    TCPAlive(String addr, int port, TCPAlive.Listener listener, WakeLock wakelock) {
        dstAddress = addr;
        dstPort = port;
        this.mListener = listener;
        connected = false;
        dodisconnect = false;
        isRunning = false;
        this.wakelock = wakelock;
        Log.i("TCPKeepAlive", MessageFormat.format("created dstAddress {} dstPort {}", dstAddress, Integer.toString(dstPort)));
    }

    protected void setKeepaliveSocketOptions(Socket socket, int idleTimeout, int interval, int count) {
        try {
            socket.setKeepAlive(true);
            try {
                Field socketImplField = Class.forName("java.net.Socket").getDeclaredField("impl");
                socketImplField.setAccessible(true);
                if(socketImplField != null) {
                    Object plainSocketImpl = socketImplField.get(socket);
                    Field fileDescriptorField = Class.forName("java.net.SocketImpl").getDeclaredField("fd");
                    if(fileDescriptorField != null) {
                        fileDescriptorField.setAccessible(true);
                        FileDescriptor fileDescriptor = (FileDescriptor)fileDescriptorField.get(plainSocketImpl);
                        Class libCoreClass = Class.forName("libcore.io.Libcore");
                        Field osField = libCoreClass.getDeclaredField("os");
                        osField.setAccessible(true);
                        Object libcoreOs = osField.get(libCoreClass);
                        Method setSocketOptsMethod = Class.forName("libcore.io.ForwardingOs").getDeclaredMethod("setsockoptInt", FileDescriptor.class, int.class, int.class, int.class);
                        if(setSocketOptsMethod != null) {
                            setSocketOptsMethod.invoke(libcoreOs, fileDescriptor, SOL_TCP, TCP_KEEPIDLE, idleTimeout);
                            setSocketOptsMethod.invoke(libcoreOs, fileDescriptor, SOL_TCP, TCP_KEEPINTVL, interval);
                            setSocketOptsMethod.invoke(libcoreOs, fileDescriptor, SOL_TCP, TCP_KEEPCNT, count);
                        }
                    }
                }
            }
            catch (Exception reflectionException) {
                reflectionException.printStackTrace();
                Log.e("TCPKeepAlive", reflectionException.toString());
            }
        } catch (SocketException e) {
            e.printStackTrace();
            Log.e("TCPKeepAlive", e.toString());
        }
    }

    @Override
    protected Void doInBackground(Void... arg0) {
        isRunning = true;
        connected = false;
        Socket socket = null;

        try {
            Log.i("TCPKeepAlive", MessageFormat.format("exec dstAddress {} dstPort {}", dstAddress, Integer.toString(dstPort)));

            socket = new Socket(dstAddress, dstPort);
            setKeepaliveSocketOptions(socket, 600, 75, 9);
            if (!socket.isConnected())
                return null;
            this.mListener.onConnect();
            connected = true;

            byte[] buffer = new byte[1024];

            int bytesRead;
            InputStream inputStream = socket.getInputStream();

         /*
          * notice: inputStream.read() will block if no data return
          */
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (!wakelock.isHeld()) {
                    wakelock.acquire();
                }
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
                byteArrayOutputStream.write(buffer, 0, bytesRead);
                response = byteArrayOutputStream.toString("UTF-8");
                Log.i("TCPKeepAlive", "Response: " + response);
                mListener.onMessage(response);
                if (dodisconnect)
                    break;
                wakelock.release();
            }

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            response = "UnknownHostException: " + e.toString();
            Log.e("TCPKeepAlive", response);
            this.mListener.onError(e);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            response = "IOException: " + e.toString();
            Log.e("TCPKeepAlive", response);
            this.mListener.onError(e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                    this.mListener.onDisconnect(0, "EOF");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        connected = false;
        return null;
    }
}
