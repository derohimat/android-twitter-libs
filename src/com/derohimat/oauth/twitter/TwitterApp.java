package com.derohimat.oauth.twitter;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.http.AccessToken;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

/**
 * 
 * @author Deni Rohimat <derohimat@gmail.com / deni@gits.co.id>
 * @author Thiago Locatelli <thiago.locatelli@gmail.com>
 * @author Lorensius W. L T <lorenz@londatiga.net>
 * 
 */
public class TwitterApp {

	private Twitter mTwitter;
	private TwitterSession mSession;
	private AccessToken mAccessToken;
	private CommonsHttpOAuthConsumer mHttpOauthConsumer;
	private OAuthProvider mHttpOauthprovider;
	private String mConsumerKey;
	private String mSecretKey;
	private ProgressDialog mProgressDlg;
	private TwDialogListener mListener;
	private Context context;

	public static final String CALLBACK_URL = "twitterapp://connect";
	private static final String TAG = "TwitterApp";

	public TwitterApp(Context context, String consumerKey, String secretKey) {
		this.context	= context;

		mTwitter 		= new TwitterFactory().getInstance();
		mSession		= new TwitterSession(context);
		mProgressDlg	= new ProgressDialog(context);

		mProgressDlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

		mConsumerKey 	= consumerKey;
		mSecretKey	 	= secretKey;

		mHttpOauthConsumer = new CommonsHttpOAuthConsumer(mConsumerKey, mSecretKey);
		mHttpOauthprovider = new DefaultOAuthProvider("http://api.twitter.com/oauth/request_token",
				"http://api.twitter.com/oauth/access_token",
				"http://api.twitter.com/oauth/authorize");

		mAccessToken	= mSession.getAccessToken();

		configureToken();
	}

	public void setListener(TwDialogListener listener) {
		mListener = listener;
	}

	@SuppressWarnings("deprecation")
	private void configureToken() {
		if (mAccessToken != null) {
			mTwitter.setOAuthConsumer(mConsumerKey, mSecretKey);

			mTwitter.setOAuthAccessToken(mAccessToken);
		}
	}

	public boolean hasAccessToken() {
		return (mAccessToken == null) ? false : true;
	}

	public void resetAccessToken(Context context) {
		if (mAccessToken != null) {
			mSession.resetAccessToken(); //clear webview cookies (in case user saved credentials on Twitter webview)
			
			CookieSyncManager.createInstance(context);
			CookieManager cookieManager = CookieManager.getInstance();
			cookieManager.removeAllCookie(); mAccessToken = null;
		}
	}

	public void updateStatus(String status) throws Exception {
		try {
			mTwitter.updateStatus(status);
		} catch (TwitterException e) {
			throw e;
		}
	}

	public void replyStatus(String status, long statusId) throws Exception {
		try {
			mTwitter.updateStatus(new StatusUpdate(status).inReplyToStatusId(statusId));
		} catch (TwitterException e) {
			throw e;
		}
	}
	
	public void retweetStatus(long statusId) throws Exception {
		try {
			//Twitter twitter = new TwitterFactory().getInstance();
			mTwitter.retweetStatus(statusId);
		} catch (TwitterException e) {
			throw e;
		}
	}
	
	public void logoutTwitter() {
		
	}
	
	public void followUser(String screenName) {
	     try {
			mTwitter.createFriendship(screenName);
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void authorize() {
		mProgressDlg.setMessage("Initializing ...");
		mProgressDlg.show();

		new Thread() {
			@Override
			public void run() {
				String authUrl = "";
				int what = 1;

				try {
					authUrl = mHttpOauthprovider.retrieveRequestToken(mHttpOauthConsumer, CALLBACK_URL);	

					what = 0;
					Log.d(TAG, "Request token url " + authUrl);
				} catch (Exception e) {
					Log.d(TAG, "Failed to get request token");

					e.printStackTrace();
				}

				mHandler.sendMessage(mHandler.obtainMessage(what, 1, 0, authUrl));
			}
		}.start();
	}

	public void processToken(String callbackUrl)  {
		mProgressDlg.setMessage("Finalizing ...");
		mProgressDlg.show();

		final String verifier = getVerifier(callbackUrl);

		new Thread() {
			@Override
			public void run() {
				int what = 1;

				try {
					mHttpOauthprovider.retrieveAccessToken(mHttpOauthConsumer, verifier);

					mAccessToken = new AccessToken(mHttpOauthConsumer.getToken(), mHttpOauthConsumer.getTokenSecret());

					configureToken();

					User user = mTwitter.verifyCredentials();

					mSession.storeAccessToken(mAccessToken, user.getName());

					what = 0;
				} catch (Exception e){
					Log.d(TAG, "Error getting access token");

					e.printStackTrace();
				}

				mHandler.sendMessage(mHandler.obtainMessage(what, 2, 0));
			}
		}.start();
	}

	private String getVerifier(String callbackUrl) {
		String verifier	 = "";

		try {
			callbackUrl = callbackUrl.replace("twitterapp", "http");

			URL url 		= new URL(callbackUrl);
			String query 	= url.getQuery();

			String array[]	= query.split("&");

			for (String parameter : array) {
				String v[] = parameter.split("=");

				if (URLDecoder.decode(v[0]).equals(oauth.signpost.OAuth.OAUTH_VERIFIER)) {
					verifier = URLDecoder.decode(v[1]);
					break;
				}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return verifier;
	}

	private void showLoginDialog(String url) {
		final TwDialogListener listener = new TwDialogListener() {
			@Override
			public void onComplete(String value) {
				processToken(value);
			}

			@Override
			public void onError(String value) {
				mListener.onError("Failed opening authorization page");
			}
		};

		new TwitterDialog(context, url, listener).show();
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			mProgressDlg.dismiss();

			if (msg.what == 1) {
				if (msg.arg1 == 1)
					mListener.onError("Error getting request token");
				else
					mListener.onError("Error getting access token");
			} else {
				if (msg.arg1 == 1)
					showLoginDialog((String) msg.obj);
				else
					mListener.onComplete("");
			}
		}
	};
	
	public String getUsername() {
		return mSession.getUsername();
	}
	
	public int getId() {
		try {
			return mTwitter.getId();
		} catch (IllegalStateException e) {
		} catch (TwitterException e) {
		}
		return 0;
	}
	public AccessToken getAccessToken() {
		return mSession.getAccessToken();
	}

	public interface TwDialogListener {
		public void onComplete(String value);		

		public void onError(String value);
	}
}