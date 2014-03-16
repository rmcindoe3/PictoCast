package com.mcindoe.pictocast.views;

import java.io.IOException;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.ProviderInfo;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.cast.RemoteMediaPlayer.MediaChannelResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.mcindoe.pictocast.R;

public class MainActivity extends ActionBarActivity {
	
	private final static String TAG = "PictoCast";
	private final static String APP_ID = "D47FEE1F";
	
	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MyMediaRouterCallback mMediaRouterCallback;
	private CastDevice mSelectedDevice;
	private GoogleApiClient mApiClient;
	private Context mContext;
	private Cast.Listener mCastListener;
	private ConnectionCallbacks mConnectionCallbacks;
	private ConnectionFailedListener mConnectionFailedListener;
	private RemoteMediaPlayer mRemoteMediaPlayer;

	private Boolean mWaitingForReconnect;
	private Boolean mApplicationStarted;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		
		//ActionBar actionBar = getSupportActionBar();
		//actionBar.setBackgroundDrawable(new ColorDrawable(android.R.color.transparent));
		
		mWaitingForReconnect = false;
		mApplicationStarted = false;
		mContext = this;
		
		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = new MediaRouteSelector.Builder()
			.addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
			.build();
		
		mMediaRouterCallback = new MyMediaRouterCallback();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			mMediaRouter.removeCallback(mMediaRouterCallback);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		teardown();
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		
		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider = 
				(MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		
		return true;
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			return rootView;
		}
	}
	
	private class MyMediaRouterCallback extends MediaRouter.Callback {

		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			
			mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
			
			launchReceiver();
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo route) {

			teardown();
			mSelectedDevice = null;
		}
		
	}
	
	private void launchReceiver() {
		
		try {

			mCastListener = new Cast.Listener() {

				@Override
				public void onApplicationDisconnected(int statusCode) {
					teardown();
					super.onApplicationDisconnected(statusCode);
				}

				@Override
				public void onApplicationStatusChanged() {
					if(mApiClient != null) {
						Log.d(TAG, "onApplicationStatusChanged: " + Cast.CastApi.getApplicationStatus(mApiClient));
					}
					super.onApplicationStatusChanged();
				}

				@Override
				public void onVolumeChanged() {
					if(mApiClient != null) {
						Log.d(TAG, "onVolumeChanged; " + Cast.CastApi.getVolume(mApiClient));
					}
					super.onVolumeChanged();
				}
			};

			mConnectionCallbacks = new ConnectionCallbacks();
			mConnectionFailedListener = new ConnectionFailedListener();

			Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
					.builder(mSelectedDevice, mCastListener);

			mApiClient = new GoogleApiClient.Builder(mContext)
				.addApi(Cast.API, apiOptionsBuilder.build())
				.addConnectionCallbacks(mConnectionCallbacks)
				.addOnConnectionFailedListener(mConnectionFailedListener)
				.build();

			mApiClient.connect();

		}
		catch (Exception e) {
			Log.e(TAG, "Failed launchReceiver", e);
		}
	}
	
	public void setupMediaChannel() {

		mRemoteMediaPlayer = new RemoteMediaPlayer();
		mRemoteMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
			
			@Override
			public void onStatusUpdated() {
				MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
				//boolean isPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
			}
		});
		mRemoteMediaPlayer.setOnMetadataUpdatedListener(new RemoteMediaPlayer.OnMetadataUpdatedListener() {
			
			@Override
			public void onMetadataUpdated() {
				MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
				//MediaMetadata mediaMetadata = mediaInfo.getMetadata();
			}
		});

		try {
			Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
		}
		catch (IOException e) {
			Log.e(TAG, "Exception while creating media channel", e);
		}
		
		mRemoteMediaPlayer
			.requestStatus(mApiClient)
			.setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

				@Override
				public void onResult(MediaChannelResult result) {
					if(!result.getStatus().isSuccess()) {
						Log.e(TAG, "Failed to request status.");
					}
					else {
						castMyVideo();
					}
				}
				
			});
	}
	
	public void castMyVideo() {

		MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
		mediaMetadata.putString(MediaMetadata.KEY_TITLE, "Test Video");
		MediaInfo mediaInfo = new MediaInfo.Builder("http://commondatastorage.googleapis.com/gtv-videos-bucket/big_buck_bunny_1080p.mp4")
			.setContentType("video/mp4")
			.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
			.setMetadata(mediaMetadata)
			.build();

		try {
			mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
			.setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
				@Override
				public void onResult(MediaChannelResult result) {
					if (result.getStatus().isSuccess()) {
						Log.d(TAG, "Media loaded successfully");
					}
				}
			});
		} catch (IllegalStateException e) {
			Log.e(TAG, "Problem occurred with media during loading", e);
		} catch (Exception e) {
			Log.e(TAG, "Problem opening media during loading", e);
		}	
	}
	
	private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

		@Override
		public void onConnected(Bundle connectionHint) {
			
			Log.d(TAG, "onConnected");
			
			if(mApiClient == null) {
				//Shouldn't be here usually.
				return;
			}

			try {
				if (mWaitingForReconnect) {
					mWaitingForReconnect = false;

					// Check if the receiver app is still running
					if ((connectionHint != null) && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
						Log.d(TAG, "App  is no longer running");
						teardown();
					} else {

						// Re-create the custom message channel
						try {
							Cast.CastApi.setMessageReceivedCallbacks(
									mApiClient,
									mRemoteMediaPlayer.getNamespace(),
									mRemoteMediaPlayer);
						} catch (IOException e) {
							Log.e(TAG, "Exception while creating channel", e);
						}
					}
				} else {

					Cast.CastApi.launchApplication(mApiClient, APP_ID, false)
						.setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
								@Override
								public void onResult(Cast.ApplicationConnectionResult result) {

									Status status = result.getStatus();
									Log.d(TAG, "ApplicationConnectionResultCallback.onResult: statusCode " + status.getStatusCode());
									if (status.isSuccess()) {

										//Grab this info
										ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
										String sessionId = result.getSessionId();
										String applicationStatus = result.getApplicationStatus();
										boolean wasLaunched = result.getWasLaunched();

										//Print the info to the log.
										Log.d(TAG, "application name: " + applicationMetadata.getName()
													+ ", status: " + applicationStatus
													+ ", sessionId: " + sessionId
													+ ", wasLaunched: " + wasLaunched);

										mApplicationStarted = true;

										setupMediaChannel();

									} else {
										Log.e(TAG, "application could not launch");
										teardown();
									}
								}
							});
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed to launch application", e);
			}

		}

		@Override
		public void onConnectionSuspended(int cause) {
			mWaitingForReconnect = true;
		}
		
	}
	
	private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

		@Override
		public void onConnectionFailed(ConnectionResult arg0) {
			teardown();
		}
	}

	/**
	 * Tears down the google cast connection.
	 */
	private void teardown() {
		Log.d(TAG, "teardown");

		if (mApiClient != null) {
			if (mApplicationStarted) {
				if (mApiClient.isConnected()) {
					try {
						Cast.CastApi.stopApplication(mApiClient);
						if (mRemoteMediaPlayer != null) {
							Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace());
							mRemoteMediaPlayer = null;
						}
					} catch (IOException e) {
						Log.e(TAG, "Exception while removing channel", e);
					}
					mApiClient.disconnect();
				}
				mApplicationStarted = false;
			}
			mApiClient = null;
		}
		mSelectedDevice = null;
		mWaitingForReconnect = false;
	}		

}

