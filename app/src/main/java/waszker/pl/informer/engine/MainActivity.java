package waszker.pl.informer.engine;

import android.Manifest;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import waszker.pl.informer.R;

public class MainActivity extends AppCompatActivity {
    private static final int REQUESTED_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        changeStartServiceButtonText();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == REQUESTED_PERMISSIONS) {
            // If request is cancelled, the result arrays are empty.
            boolean isSuccess = (grantResults.length > 0);
            String errorTitle = getResources().getString(R.string.permission_not_granted_title);
            String errorMessage = getResources().getString(R.string.permission_not_granted_message);

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isSuccess = false;
                    break;
                }
            }

            if (!isSuccess) showErrorDialog(errorTitle, errorMessage);
        }
    }

    public void startConnectionProcedure(View v) {
        if (((Button) v).getText().toString().contentEquals(getResources().getString(R.string.start))) {
            try {
                String serverAddress = ((EditText) findViewById(R.id.server_address)).getText().toString();
                int serverPort = Integer.valueOf(((EditText) findViewById(R.id.server_port)).getText().toString());
                Intent intent = new Intent(this, BackgroundService.class);
                intent.putExtra(BackgroundService.SERVER_ADDRESS, serverAddress);
                intent.putExtra(BackgroundService.SERVER_PORT, serverPort);
                startService(intent);
            } catch (NumberFormatException e) {
            }
        } else {
            stopService(new Intent(this, BackgroundService.class));
        }
        changeStartServiceButtonText();
        // this.finish();
    }

    private void changeStartServiceButtonText() {
        Resources r = getResources();
        Button button = (Button) findViewById(R.id.start_service);
        button.setText(isServiceRunning() ? r.getString(R.string.stop) : r.getString(R.string.start));
    }

    private boolean isServiceRunning() {
        boolean isRunning = false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("waszker.pl.informer.engine.BackgroundService".equals(service.service.getClassName())) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }

    private void requestPermissions() {
        int smsReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS);
        int smsSendPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        int smsReceivePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS);
        int readContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS);

        boolean hasAllPermissions = (smsReadPermission == PackageManager.PERMISSION_GRANTED) &&
                (smsSendPermission == PackageManager.PERMISSION_GRANTED) &&
                (smsReceivePermission == PackageManager.PERMISSION_GRANTED) &&
                (readContacts == PackageManager.PERMISSION_GRANTED);

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_CONTACTS}, REQUESTED_PERMISSIONS);
        }
    }

    private void showErrorDialog(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.finish();
            }
        });
        builder.show();
    }
}
