package waszker.pl.informer.engine;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import informer_api.conversation.Conversation;
import informer_api.conversation.Message;
import waszker.pl.informer.R;
import waszker.pl.informer.net.MessageService;

/**
 * <p>
 * BackgroundService runs communication with server and detects changes in text messages (received, sent).
 * </p>
 * Created by Piotr Waszkiewicz on 01.02.17.
 */
public class BackgroundService extends IntentService {
    public static final String SERVER_ADDRESS = "ServerAddress";
    public static final String SERVER_PORT = "ServerPort";
    public static final String SERVICE_NAME = "Informer_background_service";
    public static String errorMessage = "";
    private static final Object lock = new Object();
    private static boolean shouldWork = true;
    private boolean isCleanShutdown = false;
    private String serverAddress;
    private int serverPort;
    private DatabaseReader databaseReader;
    private MessageService messageService;
    private SmsObserver incomingSmsObserver;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    public BackgroundService() {
        super(SERVICE_NAME);
        errorMessage = "";
        shouldWork = true;
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        initializeFieldsFromIntent(intent);
        showNotification();
        if (startWork()) waitForStopSituation();
        if(!isCleanShutdown) stopWork();
    }

    @Override
    public void onDestroy() {
        isCleanShutdown = true;
        stopWork();
    }


    public void sendReceivedMessage(String senderNumber, Message message) {
        Conversation conversation = databaseReader.getConversationForReceivedMessage(senderNumber, message);
        messageService.synchronizeAllMessages(conversation);
    }

    public static void stopBackgroundService() {
        synchronized (lock) {
            shouldWork = false;
            lock.notify();
        }
    }

    private void initializeFieldsFromIntent(Intent intent) {
        this.serverAddress = intent.getStringExtra(SERVER_ADDRESS);
        this.serverPort = intent.getIntExtra(SERVER_PORT, 8888);
        this.databaseReader = new DatabaseReader(this);
        this.incomingSmsObserver = new SmsObserver(this);
    }

    private void waitForStopSituation() {
        synchronized (lock) {
            while (shouldWork) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private boolean startWork() {
        registerReceiver(incomingSmsObserver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
        messageService = new MessageService(serverAddress, serverPort);
        messageService.start();
        return messageService.synchronizeAllMessages(databaseReader.getConversations());
    }

    private void stopWork() {
        if (notificationManager != null) updateNotification();
        if (messageService != null) messageService.cancel();
        try {
            unregisterReceiver(incomingSmsObserver);
        } catch (IllegalArgumentException e) {
        }
        Log.d("BackgroundService", "Stopping work");
    }

    private void updateNotification() {
        notificationBuilder.setContentTitle("Informer stopped");
        if (BackgroundService.errorMessage.length() == 0)
            notificationBuilder.setContentText("No errors encountered");
        else notificationBuilder.setContentText("Error: " + BackgroundService.errorMessage);
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void showNotification() {
        Intent myIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Informer")
                        .setContentText("Informer service is running")
                        .setContentIntent(pendingIntent);
        // mBuilder.setOngoing(true);
        notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, notificationBuilder.build());
    }
}
