package waszker.pl.informer.net;

import android.telephony.SmsManager;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.ArrayList;

import informer_api.conversation.Message;
import waszker.pl.informer.engine.BackgroundService;

/**
 * <p>
 * Used in receiving messages that should be sent.
 * </p>
 * Created by Piotr Waszkiewicz on 30.01.17.
 */

class MessageReceiver extends Thread {
    private boolean shouldStopWork;
    private ObjectInputStream inStream;

    MessageReceiver(Socket connection) throws IOException {
        this.shouldStopWork = false;
        this.inStream = new ObjectInputStream(connection.getInputStream());
    }

    @Override
    public void run() {
        while (!shouldStopWork) {
            try {
                parseAndSendMessage(inStream.readObject());
            } catch (IOException e) {
                if (e.getMessage() != null) BackgroundService.errorMessage = e.getMessage();
                else BackgroundService.errorMessage = "Connection dropped by server";
                BackgroundService.stopBackgroundService();
            } catch (ClassNotFoundException | ClassCastException e) {
                if (e.getMessage() != null) Log.e("MessageReceiver", e.getMessage());
            }
        }
        Log.i("MessageReceiver", "Stopping work");
    }

    void cancel() {
        try {
            shouldStopWork = true;
            inStream.close();
        } catch (IOException e) {
        }
    }

    private void parseAndSendMessage(Object object) throws ClassCastException {
        if (!(object instanceof Message))
            throw new ClassCastException("Expected Message class object, got " + object.getClass().toString() + " instead");

        Message message = (Message) object;
        Log.d("MessageReceiver", "Got message " + message.getText());
        if (message.getDestinationNumber() != null && message.getText() != null) {
            SmsManager smsManager = SmsManager.getDefault();

            ArrayList<String> parts = smsManager.divideMessage(message.getText());
            smsManager.sendMultipartTextMessage(message.getDestinationNumber(), null, parts, null, null);
        }
    }
}
