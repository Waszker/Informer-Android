package waszker.pl.informer.net;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>
 * MessageSender thread is responsible for sending various objects to the server.
 * </p>
 * Created by Piotr Waszkiewicz on 30.01.17.
 */

class MessageSender extends Thread {
    private boolean shouldStopWork;
    private ObjectOutputStream outStream;
    private final ConcurrentLinkedQueue<Object> objects;

    MessageSender(Socket connection) throws IOException {
        this.outStream = new ObjectOutputStream(connection.getOutputStream());
        this.objects = new ConcurrentLinkedQueue<>();
        this.shouldStopWork = false;
    }

    @Override
    public void run() {
        synchronized (objects) {
            while (!shouldStopWork) {
                try {
                    if (objects.isEmpty()) objects.wait();
                    if (shouldStopWork) outStream.close();
                    for (Object object : objects) sendObject(object);
                    objects.clear();
                } catch (InterruptedException | IOException e) {
                    Log.e("MessageSender thread", e.getMessage());
                }
            }
        }
        Log.i("MessageSender", "Stopping work");
    }

    void cancel() {
        synchronized (objects) {
            this.shouldStopWork = true;
            objects.notify();
        }
    }

    synchronized void send(Object object) {
        synchronized (objects) {
            objects.add(object);
            objects.notify();
        }
    }

    private void sendObject(Object object) {
        try {
            Log.i("MessageSender", "Sending objects");
            outStream.writeObject(object);
        } catch (IOException e) {
            Log.e("MessageSender thread", e.getMessage());
        }
    }
}
