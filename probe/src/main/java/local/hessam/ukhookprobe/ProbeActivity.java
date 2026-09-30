package local.hessam.ukhookprobe;
import android.app.Activity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.TextView;
public class ProbeActivity extends Activity {
    public void onCreate(Bundle b){super.onCreate(b);TextView t=new TextView(this);t.setPadding(40,160,40,40);t.setTextSize(22);setContentView(t);
        TelephonyManager m=getSystemService(TelephonyManager.class);
        try{t.setText("SIM country: "+m.getSimCountryIso()+"\nNetwork country: "+m.getNetworkCountryIso()+"\nSIM operator: "+m.getSimOperator()+"\nNetwork operator: "+m.getNetworkOperator());}
        catch(Exception e){t.setText(e.toString());}
        try(java.io.FileOutputStream f=openFileOutput("result.txt",0)){f.write(t.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
}
