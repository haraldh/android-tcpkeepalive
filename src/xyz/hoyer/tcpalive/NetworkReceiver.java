package xyz.hoyer.tcpalive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkReceiver extends BroadcastReceiver {   
    	int previousType = -1;
@Override
public void onReceive(Context context, Intent intent) {
	    ConnectivityManager conn =  (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = conn.getActiveNetworkInfo();
	    
		if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
	        Log.i("TCPKeepAlive", "NetworkReceiver connected");
			context.startService(PushService.startIntent(context.getApplicationContext()));
	    } 
	    else if(networkInfo != null){
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				context.startService(PushService.pingIntent(context.getApplicationContext()));
				Log.i("TCPKeepAlive", "NetworkReceiver ping");
			}
	    }
	    else {
	        Log.i("TCPKeepAlive", "NetworkReceiver lost connection");
	        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
			PendingIntent operation = PendingIntent.getService(context, 0, PushService.pingIntent(context), PendingIntent.FLAG_NO_CREATE);  
	        if(operation != null){
	        	am.cancel(operation);
	        	operation.cancel();
	        }
	        context.startService(PushService.closeIntent(context));
	    }
	}
}
