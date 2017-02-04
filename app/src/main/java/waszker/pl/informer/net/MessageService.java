package waszker.pl.informer.net;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import waszker.pl.informer.engine.BackgroundService;

/**
 * <p>
 * Service used for synchronizing data with remote server.
 * </p>
 * Created by Piotr Waszkiewicz on 30.01.17.
 */

public class MessageService extends Thread {
    private String ipAddress;
    private int port;
    private Socket connection;
    private MessageSender sender;
    private MessageReceiver receiver;
    private boolean shouldStop;
    private final Object lock;
    private final Semaphore afterInitialization;

    public MessageService(String ip, int port) {
        this.ipAddress = ip;
        this.port = port;
        this.connection = null;
        this.shouldStop = false;
        this.lock = new Object();
        this.afterInitialization = new Semaphore(1);

        try {
            afterInitialization.acquire();
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void run() {
        synchronized (lock) {
            connect();
            afterInitialization.release();

            while (!shouldStop) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
        }

        stopWork();
    }

    /**
     * <p>
     * Cancels current thread and stops its work.
     * </p>
     */
    public void cancel() {
        synchronized (lock) {
            shouldStop = true;
            lock.notify();
        }
    }

    /**
     * <p>
     * Sends certain object through connection.
     * </p>
     *
     * @param object - some Java object
     * @return boolean has procedure been successful
     */
    public boolean synchronizeAllMessages(Object object) {
        boolean isSuccess = false;

        try {
            afterInitialization.acquire();
            if (connection != null && connection.isConnected()) {
                sender.send(object);
                isSuccess = true;
            }
            afterInitialization.release();
        } catch (InterruptedException e) {
            Log.e("synchronizeAllMessages", e.getMessage());
        }

        return isSuccess;
    }

    private void connect() {
        try {
            if (connection != null) connection.close();
            connection = new Socket(ipAddress, port);
            sender = new MessageSender(connection);
            receiver = new MessageReceiver(connection);
            sender.start();
            receiver.start();
        } catch (IOException e) {
            Log.e("Connection error", e.getMessage());
            BackgroundService.errorMessage = e.getMessage();
            BackgroundService.stopBackgroundService();
        }
    }

    private void stopWork() {
        Log.i("MessageService", "Stopping work and exiting");
        try {
            if (sender != null) sender.cancel();
            if (receiver != null) receiver.cancel();
            if (connection != null) connection.close();
        } catch (IOException e) {
        }
    }
}
