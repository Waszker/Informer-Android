package waszker.pl.informer.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import informer_api.conversation.Message;

/**
 * <p>
 * Waits for incoming text messages.
 * The code for retrieving multi-part text messages was taken from:
 * https://github.com/vovs/gtalksms/blob/master/src/com/googlecode/gtalksms/receivers/SmsReceiver.java
 * </p>
 * Created by Piotr Waszkiewicz on 01.02.17.
 */
public class SmsObserver extends BroadcastReceiver {
    private BackgroundService backgroundService;

    public SmsObserver(BackgroundService service) {
        this.backgroundService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Retrieves a map of extended data from the intent.
        Map<String, String> msg = RetrieveMessages(intent);

        if (msg != null) {
            for (String sender : msg.keySet()) {
                String messageBody = msg.get(sender);
                Log.i("SmsReceiver", "senderNum: " + sender + "; message: " + messageBody);
                backgroundService.sendReceivedMessage(sender, new Message(false, getCurrentDateTime(), messageBody));
            }
        }
    }

    private static Map<String, String> RetrieveMessages(Intent intent) {
        Map<String, String> msg = null;
        SmsMessage[] msgs = null;
        Bundle bundle = intent.getExtras();

        if (bundle != null && bundle.containsKey("pdus")) {
            Object[] pdus = (Object[]) bundle.get("pdus");

            if (pdus != null) {
                int nbrOfpdus = pdus.length;
                msg = new HashMap<>(nbrOfpdus);
                msgs = new SmsMessage[nbrOfpdus];

                // There can be multiple SMS from multiple senders, there can be a maximum of nbrOfpdus different senders
                // However, send long SMS of same sender in one message
                for (int i = 0; i < nbrOfpdus; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);

                    String originatinAddress = msgs[i].getOriginatingAddress();

                    // Check if index with number exists
                    if (!msg.containsKey(originatinAddress)) {
                        // Index with number doesn't exist
                        // Save string into associative array with sender number as index
                        msg.put(msgs[i].getOriginatingAddress(), msgs[i].getMessageBody());

                    } else {
                        // Number has been there, add content but consider that
                        // msg.get(originatinAddress) already contains sms:sndrNbr:previousparts of SMS,
                        // so just add the part of the current PDU
                        String previousparts = msg.get(originatinAddress);
                        String msgString = previousparts + msgs[i].getMessageBody();
                        msg.put(originatinAddress, msgString);
                    }
                }
            }
        }

        return msg;
    }

    private String getCurrentDateTime() {
        return (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
    }
}
