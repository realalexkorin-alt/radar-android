package dev.raf.radar;

import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
import org.json.*;import java.net.*;import java.io.*;import java.util.*;import java.text.*;

/** Experimental, read-only chart. Never changes monitoring or observation capture. */
final class StatisticsPanel extends LinearLayout {
 interface Transport { JSONObject get(String query,String token)throws Exception; }
 static Transport transport=StatisticsPanel::request;
 final MainActivity a;String mode="day",region="all";Calendar date=Calendar.getInstance();
 JSONObject data;TextView status,title,detail;LinearLayout content;Chart chart;int generation;long loadedAt;boolean busy;
 static final int PURPLE=0xFFBC87FF,SCARLET=0xFFFF6576;
 StatisticsPanel(MainActivity activity){super(activity);a=activity;setOrientation(VERTICAL);render();}
 String key(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date.getTime());}
 void render(){
  removeAllViews();TextView heading=a.heading("Статистика",24);heading.setPadding(a.dp(20),a.dp(20),a.dp(20),a.dp(8));addView(heading);
  ScrollView sc=new ScrollView(a);sc.setFillViewport(true);content=a.column();content.setPadding(a.dp(16),0,a.dp(16),a.dp(20));sc.addView(content);addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  TextView experimental=a.text("Эксперимент · надбавка Эконом, ₽",12,a.muted);content.addView(experimental);
  LinearLayout modes=a.row();String[] ids={"day","week","month"},labels={"День","Неделя","Месяц"};
  for(int i=0;i<3;i++){final String id=ids[i];TextView b=a.primary(labels[i],mode.equals(id),()->{if(!mode.equals(id)){mode=id;data=null;render();load(true);}});b.setContentDescription("Статистика: "+labels[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(48),1);lp.setMargins(a.dp(2),a.dp(10),a.dp(2),a.dp(10));modes.addView(b,lp);}content.addView(modes);
  LinearLayout dates=a.row();dates.addView(a.primary("‹",false,()->move(-1)),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));title=a.text(periodTitle(),15,a.fg);title.setGravity(Gravity.CENTER);dates.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView next=a.primary("›",false,()->move(1));next.setEnabled(!currentPeriod());next.setAlpha(currentPeriod()?.35f:1);dates.addView(next,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));content.addView(dates);
  LinearLayout filters=a.row();TextView zone=a.primary(region.equals("all")?"Все зоны":"Зона #"+region,false,this::zones);filters.addView(zone,new LinearLayout.LayoutParams(0,a.dp(48),1));filters.addView(a.primary("↻",false,()->load(true)),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));content.addView(filters);
  TextView legend=a.text("● Сегодня / выбранный период",12,PURPLE);legend.setPadding(0,a.dp(12),0,a.dp(4));content.addView(legend);content.addView(a.text("● Историческое среднее",12,SCARLET));
  chart=new Chart();chart.setContentDescription("График надбавки. Коснитесь для значения, смахните для смены периода.");Appearance.panel(chart,a.surface,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,a.dp(250));cp.topMargin=a.dp(12);content.addView(chart,cp);
  detail=a.text("Коснитесь графика для значений",12,a.fg);detail.setMinHeight(a.dp(48));detail.setPadding(0,a.dp(10),0,a.dp(8));content.addView(detail);
  status=a.text("",12,a.muted);content.addView(status);
  TextView explanation=a.text("Пробелы — нет наблюдений, а не нулевая надбавка. Алая линия сравнивает одинаковые дни недели; в месяце — одинаковые числа. Это история надбавок, не прогноз заработка.",12,a.muted);explanation.setPadding(0,a.dp(14),0,0);content.addView(explanation);update();
 }
 Calendar start(Calendar source){Calendar c=(Calendar)source.clone();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);if(mode.equals("week"))c.add(Calendar.DATE,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));if(mode.equals("month"))c.set(Calendar.DAY_OF_MONTH,1);return c;}
 boolean currentPeriod(){return start(date).getTimeInMillis()>=start(Calendar.getInstance()).getTimeInMillis();}
 String periodTitle(){Calendar s=start(date);SimpleDateFormat f=new SimpleDateFormat(mode.equals("month")?"LLLL yyyy":"d MMM yyyy",new Locale("ru"));if(!mode.equals("week"))return f.format(s.getTime());Calendar end=(Calendar)s.clone();end.add(Calendar.DATE,6);return new SimpleDateFormat("d MMM",new Locale("ru")).format(s.getTime())+" – "+f.format(end.getTime());}
 void move(int direction){if(direction>0&&currentPeriod())return;date=start(date);date.add(mode.equals("month")?Calendar.MONTH:Calendar.DATE,direction*(mode.equals("week")?7:1));data=null;render();load(true);}
 void zones(){JSONArray list=data==null?new JSONArray():data.optJSONArray("areas");ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();ids.add("all");labels.add("Все зоны");if(list!=null)for(int i=0;i<list.length();i++){ids.add(list.optString(i));labels.add("Зона #"+list.optString(i));}new android.app.AlertDialog.Builder(a).setTitle("Область статистики").setItems(labels.toArray(new String[0]),(d,i)->{region=ids.get(i);data=null;render();load(true);}).setNegativeButton("Отмена",null).show();}
 void visible(){if(loadedAt==0||SystemClock.elapsedRealtime()-loadedAt>300000)load(false);}
 void load(boolean force){
  if(busy&&!force)return;final int ticket=++generation;busy=true;status.setText("Загружаем историю…");
  final String token=AccountClient.session(a.store),query="mode="+mode+"&date="+key()+"&region="+region+"&timezone="+UriEncode(TimeZone.getDefault().getID());
  if(token==null){busy=false;status.setText("Войдите в аккаунт, чтобы увидеть статистику");return;}
  new Thread(()->{JSONObject result=null;try{result=transport.get(query,token);}catch(Exception ignored){}final JSONObject received=result;a.runOnUiThread(()->{if(a.isDestroyed()||ticket!=generation)return;busy=false;data=received;loadedAt=SystemClock.elapsedRealtime();update();});},"radar-chart").start();
 }
 static String UriEncode(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "UTC";}}
 void update(){if(status==null)return;if(data==null){status.setText(busy?"Загружаем историю…":"История недоступна. Нажмите ↻, чтобы повторить.");chart.invalidate();return;}
  JSONArray bins=data.optJSONArray("slots");int count=0,n=0,h=0;double sum=0;boolean historical=false;
  if(bins!=null)for(int i=0;i<bins.length();i++){JSONObject b=bins.optJSONObject(i);if(b==null)continue;n+=b.optInt("samples");if(!b.isNull("actual")){sum+=b.optDouble("actual");count++;}if(!b.isNull("historical"))historical=true;h=Math.max(h,b.optInt("historyDays"));}
  status.setText((n==0?"За выбранный период данных нет.":"Наблюдений: "+n+" · средняя надбавка "+Math.round(sum/Math.max(1,count))+" ₽")+"\n"+(historical?"Историческая база: до "+h+" "+(mode.equals("day")?"предыдущих таких дней недели":"предыдущих дат")+" на интервал.":"Исторической базы для сравнения пока нет.")+"\nЧасовой пояс: "+data.optString("timezone",TimeZone.getDefault().getID()));chart.invalidate();}
 static JSONObject request(String query,String token)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://radar-presence.vercel.app/api/chart?"+query).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(20000);c.setRequestProperty("Authorization","Bearer "+token);
  try{if(c.getResponseCode()!=200)throw new IOException("chart unavailable");try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>150000)throw new IOException("size");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}
 }
 final class Chart extends View{
  final Paint p=new Paint(3);float downX,downY;int picked=-1;Chart(){super(a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  float dp(float v){return a.dp(v);}JSONArray bins(){return data==null?new JSONArray():data.optJSONArray("slots");}
  int total(){JSONArray b=bins();return b==null||b.length()==0?(mode.equals("day")?24:mode.equals("week")?7:date.getActualMaximum(Calendar.DAY_OF_MONTH)):b.length();}
  float x(int i){return dp(44)+(getWidth()-dp(60))*i/Math.max(1,total()-1);}
  String label(int i){return mode.equals("day")?String.format(Locale.US,"%02d:00",i):mode.equals("week")?new String[]{"Пн","Вт","Ср","Чт","Пт","Сб","Вс"}[i]:String.valueOf(i+1);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);JSONArray b=bins();double max=100;if(b!=null)for(int i=0;i<b.length();i++){JSONObject v=b.optJSONObject(i);if(v!=null){max=Math.max(max,v.optDouble("actual",0));max=Math.max(max,v.optDouble("historical",0));}}max=Math.ceil(max/50)*50;float top=dp(24),bottom=getHeight()-dp(34);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(dp(10));p.setStrokeWidth(dp(1));
   for(int i=0;i<=4;i++){float y=bottom-(bottom-top)*i/4;p.setColor(a.muted);p.setAlpha(45);c.drawLine(x(0),y,x(total()-1),y,p);p.setAlpha(255);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.valueOf(Math.round(max*i/4)),dp(36),y+dp(3),p);}
   p.setTextAlign(Paint.Align.LEFT);p.setColor(a.muted);c.drawText("₽",dp(12),dp(14),p);
   for(int i=0;i<total();i++){if(i!=0&&i!=total()-1&&i%(mode.equals("day")?6:mode.equals("week")?1:7)!=0)continue;p.setTextAlign(i==0?Paint.Align.LEFT:i==total()-1?Paint.Align.RIGHT:Paint.Align.CENTER);c.drawText(label(i),x(i),getHeight()-dp(12),p);}
   if(b!=null){drawSeries(c,b,"historical",SCARLET,max,top,bottom);drawSeries(c,b,"actual",PURPLE,max,top,bottom);}
   if(picked>=0&&picked<total()){p.setColor(a.fg);p.setAlpha(80);c.drawLine(x(picked),top,x(picked),bottom,p);p.setAlpha(255);}
  }
  void drawSeries(Canvas c,JSONArray b,String field,int color,double max,float top,float bottom){p.setColor(color);p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);boolean previous=false;float lastX=0,lastY=0;
   for(int i=0;i<b.length();i++){JSONObject v=b.optJSONObject(i);if(v==null||v.isNull(field)){previous=false;continue;}float xx=x(i),yy=bottom-(float)(v.optDouble(field)/max)*(bottom-top);if(previous)c.drawLine(lastX,lastY,xx,yy,p);c.drawCircle(xx,yy,dp(2.7f),p);lastX=xx;lastY=yy;previous=true;}}
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getY()-downY)>dp(24)&&Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX))getParent().requestDisallowInterceptTouchEvent(false);return true;}if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);float dx=e.getX()-downX;if(Math.abs(dx)>dp(64)&&Math.abs(dx)>Math.abs(e.getY()-downY)){move(dx>0?-1:1);return true;}picked=Math.max(0,Math.min(total()-1,Math.round((e.getX()-dp(44))/(getWidth()-dp(60))*(total()-1))));JSONArray b=bins();JSONObject v=b==null?null:b.optJSONObject(picked);detail.setText(label(picked)+" · сейчас: "+value(v,"actual")+" · история: "+value(v,"historical")+"\nНаблюдений: "+(v==null?0:v.optInt("samples")));performClick();invalidate();return true;}return true;}
  String value(JSONObject b,String field){return b==null||b.isNull(field)?"нет данных":b.optInt(field)+" ₽";}
  @Override public boolean performClick(){super.performClick();return true;}
 }
}
