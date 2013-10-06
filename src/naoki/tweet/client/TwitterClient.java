package naoki.tweet.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xmlpull.v1.XmlPullParser;

import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Xml;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class TwitterClient extends Activity {
	private static String TAG="TwitterClient";
	// const value
	private final static int WC=LinearLayout.LayoutParams.WRAP_CONTENT;
	private final static int MP=LinearLayout.LayoutParams.MATCH_PARENT;
	private final static String BR=System.getProperty("line.separator");
	private static final int MENU_SETUP=0;
	private static final int MENU_UPDATE=1;
	
	// value for twitter
	private final static String CONSUMER_KEY="XXX";
	private final static String CONSUMER_SECRET="YYY";
//	private final String CALLBACKURL="myapp://mainactivity";
	private final String CALLBACKURL="http://twitter.com/YOUR_SITE";
	// Authentication
	private CommonsHttpOAuthConsumer consumer;
	private OAuthProvider provider;
	private String error;
	
	// UI
	private ProgressDialog progressDlg;
	private ListView listView;
	private ArrayList<Status> timeline=new ArrayList<Status>();
	private HashMap<String, Drawable> icons=new HashMap<String, Drawable>();
	private Handler handler=new Handler();
	private float dpi;
	
	//Initialization
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		Log.v(TAG, ">> onCreate");
		if(isDebuggable(this.getApplicationContext())) {
//			android.os.Debug.waitForDebugger();
		}
		
		//layout
		LinearLayout layout=new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		setContentView(layout);
		
		//DPI
		DisplayMetrics metrics=new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		dpi=metrics.density;
		
		//listview
		listView=new ListView(this);
		listView.setLayoutParams(new LinearLayout.LayoutParams(MP, WC));
		listView.setAdapter(new TwitterAdapter());
		layout.addView(listView);
		
		//Progress dialog
		progressDlg=new ProgressDialog(this);
		progressDlg.setCancelable(false); // if dialog can cancel by "KEYCODE_BACK"
		progressDlg.setMessage("Connecting..."); // message to be displayed
		progressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER); // style like "ProgressDialog.STYLE_HORIZONTAL" or "ProgressDialog.STYLE_SPINNER"
		
		//OAuth Authentication
		doOauth(false);
		Log.v(TAG, "<< onCreate");
	}
	
	//Option Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		Log.v(TAG, ">> onCreateOptionsMenu");
		MenuItem item0=menu.add(0, MENU_SETUP, 0, "setting");
		item0.setIcon(android.R.drawable.ic_menu_preferences);
		MenuItem item1=menu.add(0, MENU_UPDATE, 0, "update");
		item1.setIcon(android.R.drawable.ic_menu_upload);
		Log.v(TAG, "<< onCreateOptionsMenu");
		return true;
	}
	
	//operation for menu item selection
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.v(TAG, ">> onOptionsItemSelected");
		int itemId=item.getItemId();
		
		//setting
		if(itemId==MENU_SETUP) {
			doOauth(true);
		}
		//update
		else if(itemId==MENU_UPDATE) {
			if(timeline.size()==0) {
				doOauth(false);
			} else {
				updateTimeline();
			}
		}
		Log.v(TAG, "<< onOptionsItemSelected");
		return true;
	}
	
	//OAuth
	private void doOauth(boolean setup) {
		Log.v(TAG, ">> doOauth");
		try {
			// create consumer and provider
			//// object to handle consumer which receives user data through OAuth
			//// consumer key and consumer secret are provided by Twitter site when you register an application
			consumer=new CommonsHttpOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
			//// object to handle service provider which provides user data through OAuth
			provider=new DefaultOAuthProvider("https://api.twitter.com/oauth/request_token",
					"https://api.twitter.com/oauth/access_token",
					"https://api.twitter.com/oauth/authorize");
			
			//read token
			SharedPreferences pref=getSharedPreferences("token", MODE_PRIVATE);
			String token=pref.getString("token", "");
			String tokenSecret=pref.getString("tokenSecret", "");
			
			//setting or authorized
			if(!setup && token.length()>0 && tokenSecret.length()>0) {
				//consumer token
				consumer.setTokenWithSecret(token, tokenSecret);
				//update timeline
				updateTimeline();
			}
			//not authrized yet
			else {
				//open authentication site
				openOauthSite();
			}
		} catch (Exception e) {
			toast(e.getMessage());
		}
		Log.v(TAG, "<< doOauth");
	}
	
	//open authentication site by browser
	private void openOauthSite() {
		Log.v(TAG, ">> openOauthSite");
		//create thread
		Thread thread=new Thread() { public void run() {
			try {
				//get URL of authentication site
				Log.v(TAG, "call : retrieveRequestToken - " + "consumer : " + consumer + " CALLBACKURL : " + CALLBACKURL);
				//// retrieve URL of authentication site 
				final String url=provider.retrieveRequestToken(consumer, CALLBACKURL);
				Log.v(TAG, "return : retrieveRequestToken - " + "url : " + url);
				//create handler
				//// will use http connection so should be done on another thread
				handler.post(new Runnable() { public void run() {
					Log.e(TAG, "handler.post : " + Thread.currentThread().getName());
					//open authentication site
					//// open authentication site by browser
					//// once authentication is done on the site, onNewIntent() will be called from handler
					TwitterClient.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
					Log.v(TAG, "return : startActivity");
				}});
			} catch (Exception e) {
				Log.e(TAG, "retrieveRequestToken exception : " + e.getMessage());
//				toast(e.getMessage());
			}
		}};
		thread.start();
		Log.v(TAG, "<< openOauthSite");
	}
	
	//intent handling
	//// When the activity is re-launched while at the top of the activity stack instead of a new instance of the activity being started,
	////   onNewIntent() will be called on the existing instance with the Intent that was used to re-launch it.
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.v(TAG, ">> onNewIntent");
		//complete authentication
		final Uri uri=intent.getData();
		if(uri!=null && uri.toString().startsWith(CALLBACKURL)) { // If the data of Intent is callback, then continue to do authentication
			//create thread
			Thread thread=new Thread(new Runnable() { public void run() {
				try {
					//get auth info
					String verifier=uri.getQueryParameter(oauth.signpost.OAuth.OAUTH_VERIFIER);
					// complete authentication
					provider.retrieveAccessToken(consumer,  verifier);
					//write token
					//// then no need to login next time
					SharedPreferences pref=getSharedPreferences("token", MODE_PRIVATE);
					SharedPreferences.Editor editor=pref.edit();
					editor.putString("token", consumer.getToken());
					editor.putString("tokenSecret", consumer.getTokenSecret());
					editor.commit();
					//create handler
					handler.post(new Runnable() { public void run() {
						//update timeline
						updateTimeline();
					}});
				} catch (Exception e) {
					toast(e.getMessage());
				}
			}});
			thread.start();
			Log.v(TAG, "<< onNewIntent");
		}
	}
	
	//update timeline
	//// get the XML of timeline through HTTP with certificate, then parse it and convert it to Status
	////  then update listview
	private void updateTimeline() {
		Log.v(TAG, ">> updateTimeline");
		//progress dialog
		progressDlg.show();
		//create thread
		Thread thread=new Thread(new Runnable() { public void run() {
			error=null;
			final ArrayList<Status> timelineBuf=new ArrayList<Status>();
			Status status=null;
			String tagName=null;
			InputStream in=null;
			try {
				//http connection with certificate
				DefaultHttpClient http=new DefaultHttpClient();
				//// input URL is location to get timeline
				HttpGet httpGet=new HttpGet("http://twitter.com/statuses/friends_timeline.xml");
				//// add certificate
				consumer.sign(httpGet);
				//// execute transaction
				HttpResponse execute=http.execute(httpGet);
				in=execute.getEntity().getContent();
				//XML parse
				//// prepare XML parser
				XmlPullParser parser=Xml.newPullParser();
				//// set input stream
				parser.setInput(new InputStreamReader(in));
				while(true) {
					//// get next XML tag<event tag>
					int type=parser.next();
					//start
					if(type==XmlPullParser.START_DOCUMENT) {
					}
					//tag
					else if(type==XmlPullParser.START_TAG) {
						//// get name of tag
						tagName=parser.getName();
						if(tagName.equals("status")) {
							status=new Status();
							timelineBuf.add(status);
						}
					}
					//text
					else if(type==XmlPullParser.TEXT) {
						//// get text(content)
						if(parser.getText().trim().length()==0) {
						} else if(tagName.equals("screen_name")) {
							status.name=parser.getText();
						} else if(tagName.equals("text")) {
							status.text=parser.getText();
						} else if(tagName.equals("profile_image_url")) {
							status.iconURL=parser.getText();
						}
					}
					in.close();
					//icon
					for(int i=0;i<timelineBuf.size();i++) {
						timelineBuf.get(i).icon=readIcon(timelineBuf.get(i).iconURL);
					}
				}
			} catch (Exception e) {
					error=e.getMessage();
			}
			//handler
			handler.post(new Runnable() { public void run() {
				//remove progress dialog
				progressDlg.dismiss();
				//update listview
				if(error==null) {
					timeline=timelineBuf;
					//// to update listview, BaseAdapter#notifyDataSetChanged() shall be called
					((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
				}
				else {
					toast(error);
				}
			}});
	}});
	thread.start();
	Log.v(TAG, "<< updateTimeline");
	}
	
	//read icons
	private Drawable readIcon(String url) throws Exception {
		Log.v(TAG, ">> readIcon");
		Drawable drawable=icons.get(url);
		if(drawable!=null) return drawable;
		byte[] data=http2data(url);
		Bitmap bmp=BitmapFactory.decodeByteArray(data, 0, data.length);
		drawable=new BitmapDrawable(bmp);
		icons.put(url, drawable);
		Log.v(TAG, "<< readIcon");
		return drawable;
	}
	
	//HTTP
	private byte[] http2data(String path) throws Exception {
		Log.v(TAG, ">> http2data");
		int size;
		byte[] w=new byte[1024];
		HttpURLConnection c=null;
		InputStream in=null;
		ByteArrayOutputStream out=null;
		try {
			//connection open
			URL url=new URL(path);
			c=(HttpURLConnection)url.openConnection();
			c.setRequestMethod("GET");
			in=c.getInputStream();
			//read byte array
			out=new ByteArrayOutputStream();
			while(true) {
				size=in.read(w);
				if(size<0) break;
				out.write(w, 0, size);
			}
			out.close();
			//connection close
			in.close();
			c.disconnect();
			Log.v(TAG, "<< http2data");
			return out.toByteArray();
		} catch (Exception e) {
			try {
				if(c!=null) c.disconnect();
				if(in!=null) in.close();
				if(out!=null) out.close();
			} catch (Exception e2) {
			}
			throw e;
		}
	}
	
	//display toast
	private void toast(String str) {
		Log.v(TAG, ">> toast");
		Toast.makeText(TwitterClient.this, str, Toast.LENGTH_LONG).show();
		Log.v(TAG, "<< toast");
	}
	
	//Twitter Adapter
	public class TwitterAdapter extends BaseAdapter {
		//get item count
		@Override
		public int getCount() {
			Log.v(TAG, ">> getCount");
			return timeline.size();
		}
		
		//get item
		@Override
		public Object getItem(int pos) {
			Log.v(TAG, ">> getItem");
			return timeline.get(pos);
		}
		
		//get item id
		public long getItemId(int pos) {
			Log.v(TAG, ">> getItemId");
			return pos;
		}
		
		//get view
		public View getView(int pos,
				View convertView, ViewGroup parent) {
			Log.v(TAG, ">> getView");
			Context context=TwitterClient.this;
			Status status=timeline.get(pos);
			//create layout
			if(convertView==null) {
				int padding=(int)(6*dpi);
				LinearLayout layout=new LinearLayout(context);
				layout.setPadding(padding, padding, padding, padding);
				layout.setGravity(Gravity.TOP);
				convertView=layout;
				//icons
				int size=(int)(48*dpi);
				ImageView imageView=new ImageView(context);
				imageView.setTag("icon");
				imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
				layout.addView(imageView);
				//set text
				TextView textView=new TextView(context);
				textView.setTag("text");
				textView.setPadding(padding, 0, padding, 0);
				layout.addView(textView);
			}
			//set value
			ImageView imageView=(ImageView)convertView.findViewWithTag("icon");
			imageView.setImageDrawable(status.icon);
			TextView textView=(TextView)convertView.findViewWithTag("text");
			textView.setText("["+status.name+"]"+BR+status.text);
			Log.v(TAG, "<< getView");
			return convertView;
		}
	}
	
	// for debug
	private static boolean isDebuggable(Context ctx) {
		PackageManager pm=ctx.getPackageManager();
		ApplicationInfo appInfo=null;
		try {
			appInfo=pm.getApplicationInfo(ctx.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			return false;
		}
		if((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == ApplicationInfo.FLAG_DEBUGGABLE) {
			return true;
		}
		return false;
	}
}
