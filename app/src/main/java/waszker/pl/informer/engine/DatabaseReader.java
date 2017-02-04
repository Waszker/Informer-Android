package waszker.pl.informer.engine;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import informer_api.conversation.Conversation;
import informer_api.conversation.Message;
import informer_api.conversation.Person;

/**
 * <p>
 * Class reading SMS database. It asynchronously reads SMS and people data and stores it
 * for future use.
 * </p>
 * Created by Piotr Waszkiewicz on 30.01.17.
 */

class DatabaseReader {
    private Context context;
    private Map<String, Person> people;
    private Map<Person, Conversation> conversations;

    DatabaseReader(Context context) {
        this.context = context;
        people = new HashMap<>();
        conversations = new HashMap<>();
        readDatabase();
    }

    Map<Person, Conversation> getConversations() {
        return conversations;
    }

    Conversation getConversationForReceivedMessage(String senderNumber, Message message) {
        Person person = getPersonByNumber(normalizePhoneNumber(senderNumber));
        Conversation conversation = new Conversation(person);
        conversation.addMessage(message);
        return conversation;
    }

    private void readDatabase() {
        ContentResolver resolver = context.getContentResolver();
        try {
            Cursor cursor = resolver.query(Uri.parse("content://sms"), new String[]{"*"},
                    null, null, "date ASC");

            while (cursor.moveToNext()) {
                addMessageToConversation(cursor);
            }

            cursor.close();
        } catch (NullPointerException e) {
            Log.e("Exception", e.getMessage());
        }
    }

    private void addMessageToConversation(Cursor cursor) {
        //if it's a received message :
        boolean isMe = cursor.getString(cursor.getColumnIndexOrThrow("person")) == null;
        String number = normalizePhoneNumber(cursor.getString(cursor.getColumnIndexOrThrow("address")));
        String date = normalizeDateString(cursor.getLong(cursor.getColumnIndexOrThrow("date")));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));

        Person person = getAndUpdatePersonIfNecessary(number);
        if (!conversations.containsKey(person)) {
            conversations.put(person, new Conversation(person));
        }
        Conversation conversation = conversations.get(person);
        conversation.addMessage(new Message(isMe, date, body));
    }

    private Person getAndUpdatePersonIfNecessary(String number) {
        if (!people.containsKey(number)) {
            people.put(number, getPersonByNumber(number));
        }

        return people.get(number);
    }

    private Person getPersonByNumber(String number) {
        String name = "?";
        byte[] photo = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        ContentResolver contentResolver = context.getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

        if (contactLookup != null && contactLookup.getCount() > 0) {
            try {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                long contactId = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                photo = openPhoto(contactId);
            } finally {
                contactLookup.close();
            }
        }

        return new Person(name, number, photo);
    }

    private byte[] openPhoto(long contactId) {
        byte[] data = null;
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[]{ContactsContract.Contacts.Photo.PHOTO}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                data = cursor.getBlob(0);
            } finally {
                cursor.close();
            }
        }

        return data;
    }

    private String normalizePhoneNumber(String number) {
        // IMPORTANT! Phone number normalizer removes all spaces and locale numbers (like +48).
        // This could potentially lead to misleading clashes between different conversations
        // as different people could be identified as one.
        number = number.replaceAll(" ", "");
        if (number.charAt(0) == '+') number = number.substring(3);
        return number;
    }

    private String normalizeDateString(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(calendar.getTime());
    }
}
