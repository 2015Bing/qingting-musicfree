package app.qingting.music;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** One successful activation persists until app data is removed. No periodic revocation. */
public final class LicenseStore {
    private LicenseStore(){}
    public static boolean activated(Context context){return context.getSharedPreferences("activation",Context.MODE_PRIVATE).getString("hash","").matches("[0-9a-f]{64}");}
    static boolean save(Context context,String code){
        android.content.SharedPreferences prefs=context.getSharedPreferences("activation",Context.MODE_PRIVATE);
        if(prefs.edit().putString("hash",LicensePolicy.hash(code)).putLong("activatedAt",System.currentTimeMillis()).commit())return true;
        // commit(false) can still update the process-local map; do not unlock on a failed save.
        prefs.edit().remove("hash").remove("activatedAt").apply();return false;
    }
    public static boolean require(Activity activity){
        if(activated(activity))return true;
        activity.startActivity(new Intent(activity,ActivationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();return false;
    }
}
