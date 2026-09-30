package local.hessam.ukhooks;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final String B="/data/adb/uk-hook-switch";
    static final String SHM="com.samsung.android.shealthmonitor", STORE="com.sec.android.app.samsungapps";
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final LinkedHashSet<String> watched=new LinkedHashSet<>(), selected=new LinkedHashSet<>();
    final Map<String,TextView> statuses=new HashMap<>();
    final Map<String,Switch> switches=new HashMap<>();
    final Map<String,TextView> countryButtons=new HashMap<>();
    final LinkedHashMap<String,CountryProfile> countries=new LinkedHashMap<>();
    JSONObject lastState;
    final int ink=Color.rgb(238,242,248), muted=Color.rgb(147,159,179), accent=Color.rgb(142,228,187);
    final int background=Color.rgb(12,16,24),surface=Color.rgb(23,29,41);
    LinearLayout rows; TextView summary,note,start,add,refresh,checked,session;
    boolean busy=false,active=false,rendering=false;
    SharedPreferences prefs;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("apps",MODE_PRIVATE);
        loadCountries();
        load("watched",watched);load("selected",selected);
        if(!prefs.contains("watched")){watched.add(SHM);watched.add(STORE);selected.addAll(watched);save();}
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setBackgroundColor(background);
        outer.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);outer.addView(scroll);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(24),dp(22),dp(24),dp(24));scroll.addView(page);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);page.addView(toolbar);
        TextView eyebrow=text("REGION CONTROL",11,accent,true);eyebrow.setLetterSpacing(.16f);toolbar.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));
        refresh=button("↻",false);refresh.setTextSize(25);refresh.setContentDescription("Refresh status");toolbar.addView(refresh,new LinearLayout.LayoutParams(dp(44),dp(44)));refresh.setOnClickListener(v->refresh());
        TextView title=text("Region Switch",29,ink,true);title.setPadding(0,dp(5),0,0);page.addView(title);
        TextView intro=text("Your apps. Your region.",15,muted,false);intro.setPadding(0,dp(7),0,dp(25));page.addView(intro);
        LinearLayout card=card();card.setBackground(shape(Color.rgb(23,43,37),22));page.addView(card);
        LinearLayout country=new LinearLayout(this);country.setGravity(Gravity.CENTER_VERTICAL);card.addView(country);
        TextView badge=text("◎",28,accent,true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(Color.rgb(36,65,51),14));country.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout region=new LinearLayout(this);region.setOrientation(1);region.setPadding(dp(14),0,0,0);country.addView(region,new LinearLayout.LayoutParams(0,-2,1));
        region.addView(text("A region for every app",18,ink,true));TextView sub=text(countries.size()+" countries & territories",12,Color.rgb(159,185,171),false);sub.setPadding(0,dp(4),0,0);region.addView(sub);
        session=text("CHECKING",10,accent,true);session.setLetterSpacing(.1f);session.setPadding(0,dp(23),0,dp(7));card.addView(session);
        summary=text("Checking your session",21,ink,true);card.addView(summary);
        note=text("",13,Color.rgb(168,189,179),false);note.setLineSpacing(dp(3),1);note.setPadding(0,dp(7),0,dp(19));card.addView(note);
        start=button("Enable hooks",true);card.addView(start,new LinearLayout.LayoutParams(-1,dp(48)));start.setOnClickListener(v->{if(active)stopAll();else apply(null,true);});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(23),0,dp(14));page.addView(header);
        header.addView(text("Apps",21,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        add=button("+  Add app",false);header.addView(add,new LinearLayout.LayoutParams(dp(108),dp(42)));add.setOnClickListener(v->picker());
        rows=new LinearLayout(this);rows.setOrientation(1);page.addView(rows);
        TextView hint=text("Switching closes that app. Reopen it to apply.",12,muted,false);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(8),0,dp(14));page.addView(hint);
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);page.addView(footer);
        checked=text("Status checked once on opening",11,muted,false);footer.addView(checked,new LinearLayout.LayoutParams(0,-2,1));
        TextView help=text("How it works",12,accent,true);help.setPadding(dp(8),dp(14),0,dp(14));footer.addView(help);help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Your region, per app").setMessage("Choose a country beneath each app. Enabled apps see that SIM country and a matching mobile-network code. Galaxy Store also uses its own region profile. Your real SIM, IP address and GPS location stay unchanged, so account or location checks may still apply.\n\nExisting and new apps default to the UK. Changing an enabled app's country closes it; reopen it to apply. Hooks continue when this app is closed, until the next reboot.\n\nStatus is checked once when opened, or when you tap refresh. Long-press an app to remove it.").setPositiveButton("Got it",null).show());
        setContentView(outer);renderRows();refresh();
    }
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
    int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView button(String s,boolean primary){TextView b=text(s,14,primary?Color.rgb(12,35,26):accent,true);b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ee4bb),shape(primary?accent:surface,14),null));return b;}
    LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(18),dp(18),dp(18),dp(18));l.setBackground(shape(surface,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);l.setLayoutParams(p);return l;}
    String label(String p){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){return p;}}
    static boolean valid(String p){return p.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")&&p.length()<=180&&!p.equals("com.android.systemui")&&!p.equals("com.android.phone")&&!p.equals("local.hessam.ukhooks");}
    void load(String key,Set<String> into){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)if(valid(a.getString(i)))into.add(a.getString(i));}catch(Exception ignored){}}
    void save(){prefs.edit().putString("watched",new JSONArray(watched).toString()).putString("selected",new JSONArray(selected).toString()).apply();}
    void loadCountries(){
        countries.put("gb",new CountryProfile("gb","23415"));
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open("countries.tsv"),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){String[] fields=line.split("\t");if(fields.length==2)try{CountryProfile c=new CountryProfile(fields[0],fields[1]);countries.put(c.iso,c);}catch(RuntimeException ignored){}}}catch(IOException e){toast("Country catalog unavailable; UK profile retained.");}
    }
    CountryProfile countryFor(String p){CountryProfile c=countries.get(prefs.getString("country_"+p,"gb"));return c==null?countries.get("gb"):c;}
    void renderRows(){
        rendering=true;rows.removeAllViews();statuses.clear();switches.clear();countryButtons.clear();
        for(String p:watched){
            LinearLayout c=card(),top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);c.addView(top);
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(p));}catch(Exception ignored){}top.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout names=new LinearLayout(this);names.setOrientation(1);names.setPadding(dp(12),0,dp(8),0);names.addView(text(label(p),15,ink,true));TextView profile=text(p.equals(STORE)?"SIM + Store region":"SIM & network region",11,muted,false);profile.setPadding(0,dp(4),0,0);names.addView(profile);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            Switch sw=new Switch(this);sw.setContentDescription("Region hook for "+label(p));sw.setChecked(selected.contains(p));sw.setEnabled(!busy);sw.setButtonTintList(ColorStateList.valueOf(accent));sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff3f6f59,0xff3a4251}));top.addView(sw);switches.put(p,sw);
            CountryProfile chosen=countryFor(p);TextView selectCountry=button(chosen.name+"  ▾",false);selectCountry.setContentDescription("Country for "+label(p));selectCountry.setGravity(Gravity.CENTER_VERTICAL);selectCountry.setPadding(dp(13),0,dp(13),0);selectCountry.setTextSize(14);selectCountry.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ee4bb),shape(0xff222f35,12),null));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(46));cp.topMargin=dp(16);c.addView(selectCountry,cp);countryButtons.put(p,selectCountry);selectCountry.setEnabled(!busy);selectCountry.setOnClickListener(v->countryPicker(p));
            LinearLayout bottom=new LinearLayout(this);bottom.setPadding(0,dp(14),0,0);bottom.setGravity(Gravity.CENTER_VERTICAL);c.addView(bottom);
            TextView st=text(selected.contains(p)?"Selected":"Off",13,muted,false);statuses.put(p,st);bottom.addView(st,new LinearLayout.LayoutParams(0,-2,1));
            TextView open=button("Open  ↗",false);open.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ee4bb),shape(0xff252f40,11),null));bottom.addView(open,new LinearLayout.LayoutParams(dp(82),dp(38)));open.setOnClickListener(v->{Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null)startActivity(i);else toast("This app has no launch screen.");});
            sw.setOnCheckedChangeListener((v,on)->{if(rendering)return;if(on)selected.add(p);else selected.remove(p);save();apply(p,true);});
            c.setOnLongClickListener(v->{if(busy)return true;new AlertDialog.Builder(this).setTitle("Remove "+label(p)+"?").setMessage("Its hook will be disabled.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{selected.remove(p);watched.remove(p);save();renderRows();apply(p,active);}).show();return true;});
            rows.addView(c);
        }rendering=false;if(lastState!=null)showStatus(lastState,false);
    }
    static String quote(String x){return "'"+x.replace("'","'\\''")+"'";}
    String root(String command)throws Exception{
        java.lang.Process p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread drain=new Thread(()->{try{byte[] b=new byte[4096];int n;while((n=p.getInputStream().read(b))!=-1){if(out.size()<65536)out.write(b,0,n);}}catch(Exception ignored){}});drain.start();
        if(!p.waitFor(35,TimeUnit.SECONDS)){p.destroy();throw new IOException("Root command timed out. Refresh status before trying again.");}
        drain.join(1000);String s=out.toString("UTF-8").trim();
        if(p.exitValue()!=0)throw new IOException(s.isEmpty()?"Allow Region Switch in KernelSU → Superuser, then refresh.":s);
        return s;
    }
    void task(String message,Callable<JSONObject> fn){
        if(busy)return;setBusy(true);summary.setText(message);
        worker.execute(()->{try{JSONObject result=fn.call();runOnUiThread(()->{setBusy(false);showStatus(result);});}catch(Exception e){runOnUiThread(()->{setBusy(false);summary.setText("Needs attention");note.setText(e.getMessage());for(String p:statuses.keySet())statuses.get(p).setText(selected.contains(p)?"Selected • not verified":"Off");});}});
    }
    void setBusy(boolean b){busy=b;for(TextView v:new TextView[]{start,add,refresh}){v.setEnabled(!b);v.setAlpha(b?.5f:1f);}for(Switch s:switches.values())s.setEnabled(!b);for(TextView v:countryButtons.values())v.setEnabled(!b);}
    JSONObject readStatus()throws Exception{return new JSONObject(root("test -x "+B+"/ctl.sh || { echo 'Hook engine is not installed.'; exit 1; }; "+B+"/ctl.sh status"));}
    void refresh(){task("Checking…",this::readStatus);}
    void apply(String changed,boolean run){
        List<String> enabled=new ArrayList<>(selected);
        List<String> profiles=new ArrayList<>();for(String p:enabled)profiles.add(countryFor(p).configLine(p));
        String config=String.join("\n",profiles)+"\n";
        String encoded=Base64.encodeToString(config.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        task("Applying hooks…",()->{
            JSONObject before=readStatus();boolean wasRunning=before.optString("phase").equals("listening");
            root("umask 077; printf %s "+quote(encoded)+" | base64 -d > "+B+"/targets.tmp && mv "+B+"/targets.tmp "+B+"/targets.conf");
            if(run&&!enabled.isEmpty())root(B+"/ctl.sh start");
            else if(enabled.isEmpty())root(B+"/ctl.sh stop");
            Thread.sleep(400);
            if(changed!=null)root("am force-stop "+quote(changed)+" < /dev/null");
            if(run&&!wasRunning)for(String p:enabled)if(!p.equals(changed))root("am force-stop "+quote(p)+" < /dev/null");
            return readStatus();
        });
    }
    void stopAll(){task("Stopping hooks…",()->{root(B+"/ctl.sh stop");return readStatus();});}
    void showStatus(JSONObject state){showStatus(state,true);}
    void showStatus(JSONObject state,boolean fresh){
        lastState=state;
        active=state.optString("phase").equals("listening");summary.setText(active?"Hooks are running":"Ready when you are");
        session.setText(active?"●  ENABLED":"○  PAUSED");start.setText(active?"Pause all hooks":"Enable selected apps");
        note.setText(active?selected.size()+" apps selected · stays on when you leave":"Your app selections are saved.");
        if(fresh)checked.setText("Checked "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date()));
        Map<String,JSONObject> live=new HashMap<>();JSONArray a=state.optJSONArray("targets");if(a!=null)for(int i=0;i<a.length();i++){JSONObject t=a.optJSONObject(i);if(t!=null)live.put(t.optString("package"),t);}
        for(String p:statuses.keySet()){
            JSONObject t=live.get(p);String s;
            if(!selected.contains(p))s="Off";
            else if(!active)s="Selected • stopped";
            else if(t==null)s="Applying…";
            else if(!t.optString("country","gb").equals(countryFor(p).iso))s="Country change pending";
            else if(t.optBoolean("error"))s="Hook failed • reopen app";
            else if(t.optBoolean("ready"))s="●  Active";
            else s="Ready on next open";
            statuses.get(p).setText(s);statuses.get(p).setTextColor(t!=null&&t.optBoolean("ready")?accent:muted);
        }
    }
    void countryPicker(String p){
        if(busy)return;
        final CountryProfile current=countryFor(p);
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(1);sheet.setPadding(dp(20),dp(10),dp(20),dp(18));
        GradientDrawable panel=shape(0xff111821,28);panel.setCornerRadii(new float[]{dp(28),dp(28),dp(28),dp(28),0,0,0,0});sheet.setBackground(panel);
        sheet.setOnApplyWindowInsetsListener((v,i)->{int bottom=Build.VERSION.SDK_INT>=30?i.getInsets(WindowInsets.Type.systemBars()).bottom:i.getSystemWindowInsetBottom();v.setPadding(dp(20),dp(10),dp(20),Math.max(dp(18),bottom));return i;});
        View handle=new View(this);handle.setBackground(shape(0xff3a4653,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(34),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(22);sheet.addView(handle,hp);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);sheet.addView(heading);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);titles.addView(text("Choose a country",24,ink,true));TextView subtitle=text(label(p),13,muted,false);subtitle.setPadding(0,dp(5),0,0);titles.addView(subtitle);heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=button("×",false);close.setTextSize(26);close.setTextColor(muted);close.setContentDescription("Close country picker");heading.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));close.setOnClickListener(v->dialog.dismiss());
        LinearLayout chosen=new LinearLayout(this);chosen.setGravity(Gravity.CENTER_VERTICAL);chosen.setPadding(dp(12),dp(11),dp(12),dp(11));chosen.setBackground(shape(0xff1b352c,16));LinearLayout.LayoutParams chosenParams=new LinearLayout.LayoutParams(-1,-2);chosenParams.topMargin=dp(22);chosenParams.bottomMargin=dp(16);sheet.addView(chosen,chosenParams);
        TextView currentFlag=text(flag(current.iso),29,ink,false);currentFlag.setGravity(Gravity.CENTER);chosen.addView(currentFlag,new LinearLayout.LayoutParams(dp(44),dp(42)));
        LinearLayout chosenNames=new LinearLayout(this);chosenNames.setOrientation(1);chosenNames.setPadding(dp(10),0,0,0);TextView caption=text("CURRENT COUNTRY",9,accent,true);caption.setLetterSpacing(.12f);chosenNames.addView(caption);TextView countryName=text(current.name,15,ink,true);countryName.setPadding(0,dp(4),0,0);chosenNames.addView(countryName);chosen.addView(chosenNames,new LinearLayout.LayoutParams(0,-2,1));TextView currentCheck=text("✓",17,accent,true);chosen.addView(currentCheck);
        LinearLayout searchBox=new LinearLayout(this);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(14),0,dp(6),0);searchBox.setBackground(shape(0xff202a36,15));sheet.addView(searchBox,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView searchIcon=new ImageView(this);searchIcon.setImageResource(android.R.drawable.ic_menu_search);searchIcon.setColorFilter(muted);searchBox.addView(searchIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));
        EditText search=new EditText(this);search.setHint("Search country or code");search.setTextColor(ink);search.setHintTextColor(muted);search.setTextSize(15);search.setSingleLine();search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(10),0,dp(4),0);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);searchBox.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        TextView clear=button("×",false);clear.setTextSize(22);clear.setBackgroundColor(Color.TRANSPARENT);clear.setContentDescription("Clear country search");clear.setVisibility(View.INVISIBLE);searchBox.addView(clear,new LinearLayout.LayoutParams(dp(42),dp(44)));clear.setOnClickListener(v->search.setText(""));
        TextView count=text("",11,muted,true);count.setLetterSpacing(.08f);count.setPadding(dp(2),dp(21),0,dp(9));sheet.addView(count);
        FrameLayout results=new FrameLayout(this);sheet.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDivider(null);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(0,0,0,dp(10));list.setKeyboardNavigationCluster(true);results.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=text("No countries found\nTry a name or two-letter code.",15,muted,false);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(8),1);results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        List<CountryProfile> all=new ArrayList<>(countries.values());all.sort((a,b)->a.name.compareToIgnoreCase(b.name));List<Object> entries=new ArrayList<>();
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return entries.size();}public Object getItem(int i){return entries.get(i);}public long getItemId(int i){return i;}
            public int getViewTypeCount(){return 2;}public int getItemViewType(int i){return entries.get(i) instanceof String?0:1;}public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int i){return entries.get(i) instanceof CountryProfile;}
            public View getView(int i,View recycled,ViewGroup parent){
                Object item=entries.get(i);
                if(item instanceof String){TextView letter=text((String)item,12,accent,true);letter.setPadding(dp(12),dp(17),0,dp(9));return letter;}
                CountryProfile c=(CountryProfile)item;boolean selected=c.iso.equals(current.iso);
                LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(66));row.setPadding(dp(10),dp(9),dp(12),dp(9));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ee4bb),shape(selected?0xff203b30:0xff111821,14),null));
                TextView f=text(flag(c.iso),28,ink,false);f.setGravity(Gravity.CENTER);f.setBackground(shape(selected?0xff294d3e:0xff202a36,13));row.addView(f,new LinearLayout.LayoutParams(dp(46),dp(44)));
                TextView name=text(c.name,15,selected?accent:ink,selected);name.setPadding(dp(14),0,dp(8),0);name.setMaxLines(2);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                TextView code=text(selected?"✓":c.iso.toUpperCase(Locale.ROOT),selected?19:11,selected?accent:muted,selected);code.setGravity(Gravity.CENTER);row.addView(code,new LinearLayout.LayoutParams(dp(28),dp(32)));row.setContentDescription(c.name+(selected?", selected":""));return row;
            }
        };list.setAdapter(adapter);
        Runnable filter=()->{
            String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();entries.clear();String letter="";int matches=0;
            for(CountryProfile c:all)if((c.name+" "+c.iso+" "+c.iso3+(c.iso.equals("gb")?" uk britain":"")).toLowerCase(Locale.ROOT).contains(query)){
                String group=c.name.substring(0,1).toUpperCase(Locale.ROOT);if(query.isEmpty()&&!group.equals(letter)){entries.add(group);letter=group;}entries.add(c);matches++;
            }
            clear.setVisibility(query.isEmpty()?View.INVISIBLE:View.VISIBLE);count.setText(query.isEmpty()?"ALL COUNTRIES  ·  "+matches:matches+(matches==1?" RESULT":" RESULTS"));adapter.notifyDataSetChanged();list.setSelection(0);
        };filter.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;});
        list.setOnItemClickListener((a,v,pos,id)->{
            Object item=entries.get(pos);if(!(item instanceof CountryProfile))return;CountryProfile next=(CountryProfile)item;boolean changed=!next.iso.equals(current.iso);dialog.dismiss();if(!changed)return;
            prefs.edit().putString("country_"+p,next.iso).apply();renderRows();
            // A country selection never turns on an app the user has switched off.
            if(selected.contains(p))apply(p,active);else toast("Country saved. Enable the app when ready.");
        });
        sheet.setFocusableInTouchMode(true);sheet.requestFocus();dialog.setContentView(sheet);dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();window.setGravity(Gravity.BOTTOM);window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(560)),(int)(getResources().getDisplayMetrics().heightPixels*.9f));
    }
    String flag(String iso){String code=iso.toUpperCase(Locale.ROOT);return new String(Character.toChars(0x1f1e6+code.charAt(0)-'A'))+new String(Character.toChars(0x1f1e6+code.charAt(1)-'A'));}
    void picker(){
        if(watched.size()>=64){toast("Up to 64 apps are supported.");return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),0,dp(20),0);
        EditText search=new EditText(this);search.setHint("Search apps");search.setTextColor(ink);search.setHintTextColor(muted);search.setSingleLine();box.addView(search);
        ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));
        List<ApplicationInfo> all=getPackageManager().getInstalledApplications(0),filtered=new ArrayList<>();
        all.removeIf(a->!valid(a.packageName)||watched.contains(a.packageName)||getPackageManager().getLaunchIntentForPackage(a.packageName)==null);
        all.sort((a,b)->label(a.packageName).compareToIgnoreCase(label(b.packageName)));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().toLowerCase(Locale.ROOT);filtered.clear();adapter.clear();for(ApplicationInfo a:all)if((label(a.packageName)+a.packageName).toLowerCase(Locale.ROOT).contains(q)){filtered.add(a);adapter.add(label(a.packageName)+"\n"+a.packageName);}};filter.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Add an app").setView(box).setNegativeButton("Cancel",null).create();
        list.setOnItemClickListener((a,v,pos,id)->{watched.add(filtered.get(pos).packageName);save();renderRows();dialog.dismiss();});dialog.show();
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
