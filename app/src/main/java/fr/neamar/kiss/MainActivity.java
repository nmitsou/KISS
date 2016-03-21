package fr.neamar.kiss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ListActivity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.util.ArrayList;

import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.searcher.ApplicationsSearcher;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.NullSearcher;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.searcher.Searcher;

public class MainActivity extends ListActivity implements QueryInterface {

    public static final String START_LOAD = "fr.neamar.summon.START_LOAD";
    public static final String LOAD_OVER = "fr.neamar.summon.LOAD_OVER";
    public static final String FULL_LOAD_OVER = "fr.neamar.summon.FULL_LOAD_OVER";
    private static final String KEY_WIDGET_IDS = "fr.neamar.kiss.KEY_WIDGET_IDS";

    /**
     * IDS for the favorites buttons
     */
    private final int[] favsIds = new int[] {R.id.favorite0, R.id.favorite1, R.id.favorite2, R.id.favorite3};
    /**
     * Number of favorites to retrieve.
     * We need to pad this number to account for removed items still in history
     */
    private final int tryToRetrieve = favsIds.length + 2;
    /**
     * InputType with spellecheck and swiping
     */
    private final int spellcheckEnabledType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
    /**
     * Adapter to display records
     */
    public RecordAdapter adapter;
    /**
     * Store user preferences
     */
    private SharedPreferences prefs;
    private BroadcastReceiver mReceiver;
    /**
     * View for the Search text
     */
    private EditText searchEditText;
    private final Runnable displayKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            showKeyboard();
        }
    };
    /**
     * Menu button
     */
    private View menuButton;
    /**
     * Kiss bar
     */
    private View kissBar;
    /**
     * Task launched on text change
     */
    private Searcher searcher;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Initialize UI
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String theme = prefs.getString("theme", "light");
        if(theme.equals("dark")) {
            setTheme(R.style.AppThemeDark);
        } else if(theme.equals("transparent")) {
            setTheme(R.style.AppThemeTransparent);
        } else if(theme.equals("semi-transparent")) {
            setTheme(R.style.AppThemeSemiTransparent);
        } else if(theme.equals("semi-transparent-dark")) {
            setTheme(R.style.AppThemeSemiTransparentDark);
        }

        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter(START_LOAD);
        IntentFilter intentFilterBis = new IntentFilter(LOAD_OVER);
        IntentFilter intentFilterTer = new IntentFilter(FULL_LOAD_OVER);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equalsIgnoreCase(LOAD_OVER)) {
                    updateRecords(searchEditText.getText().toString());
                } else if(intent.getAction().equalsIgnoreCase(FULL_LOAD_OVER)) {
                    displayLoader(false);

                } else if(intent.getAction().equalsIgnoreCase(START_LOAD)) {
                    displayLoader(true);
                }
            }
        };

        this.registerReceiver(mReceiver, intentFilter);
        this.registerReceiver(mReceiver, intentFilterBis);
        this.registerReceiver(mReceiver, intentFilterTer);
        KissApplication.initDataHandler(this);

        // Initialize preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Lock launcher into portrait mode
        // Do it here (before initializing the view) to make the transition as smooth as possible
        if(prefs.getBoolean("force-portrait", true)) {
            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        setContentView(R.layout.main);

        // Create adapter for records
        adapter = new RecordAdapter(this, this, R.layout.item_app, new ArrayList<Result>());
        setListAdapter(adapter);

        searchEditText = (EditText) findViewById(R.id.searchEditText);

        // Listen to changes
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if(s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateRecords(s.toString());
                displayClearOnInput();
            }
        });

        // On validate, launch first record
        searchEditText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                RecordAdapter adapter = ((RecordAdapter) getListView().getAdapter());

                adapter.onClick(adapter.getCount() - 1, null);

                return true;
            }
        });

        String miniUi = prefs.getString("mini-ui", "history");
        if(!miniUi.equals("history-intro")) {
            searchEditText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //show history only if no search text is added
                    if(((EditText) v).getText().toString().isEmpty()) {
                        searcher = new HistorySearcher(MainActivity.this);
                        searcher.execute();
                    }
                }
            });
        }

        kissBar = findViewById(R.id.main_kissbar);
        menuButton = findViewById(R.id.menuButton);

        getListView().setLongClickable(true);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id) {
                ((RecordAdapter) parent.getAdapter()).onLongClick(pos, v);
                return true;
            }
        });

        // Enable swiping
        if(prefs.getBoolean("enable-spellcheck", false)) {
            searchEditText.setInputType(spellcheckEnabledType);
        }

        // Hide the "X" after the text field, instead displaying the menu button
        displayClearOnInput();

        // Apply effects depending on current Android version
        applyDesignTweaks();

        // Initialize widgets management
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, R.id.appWidgetHostId);
        findViewById(R.id.widgets).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                pickWidget();
                return true;
            }
        });
    }

    /**
     * Apply some tweaks to the design, depending on the current SDK version
     */
    private void applyDesignTweaks() {
        final int[] tweakableIds = new int[] {
              R.id.menuButton,
              // Barely visible on the clearbutton, since it disappears instant. Can be seen on long click though
              R.id.clearButton,
              R.id.launcherButton,
              R.id.favorite0,
              R.id.favorite1,
              R.id.favorite2,
              R.id.favorite3,
        };

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

            for(int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }

        } else if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

            for(int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        adapter.onClick(position, v);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_widget, menu);
        ((WidgetContextMenuInfo) menuInfo).targetedView = v;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        WidgetContextMenuInfo info = (WidgetContextMenuInfo) item.getMenuInfo();
        AppWidgetHostView hostView = (AppWidgetHostView) info.targetedView;
        switch(item.getItemId()) {
            case R.id.remove_widget:
                removeWidget(hostView);
                return true;
            case R.id.configure_widget:
                configureWidget(hostView.getAppWidgetId());
                return true;
            default:
                return false;
        }
    }

    /**
     * Start widget updates listening
     */
    @Override
    protected void onStart() {
        super.onStart();
        appWidgetHost.startListening();
    }

    /**
     * Stop widget updates listening
     */
    @Override
    protected void onStop() {
        super.onStop();
        appWidgetHost.stopListening();
    }

    /**
     *
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        LinearLayout widgets = (LinearLayout) findViewById(R.id.widgets);
        int widgetsCount = widgets.getChildCount();
        int[] ids = new int[widgetsCount];
        for(int i=0; i<widgetsCount; ++i) {
            AppWidgetHostView widgetView = (AppWidgetHostView) widgets.getChildAt(i);
            ids[i] = widgetView.getAppWidgetId();
            Log.d("KISS", "saving widget: " + ids[i]);
        }
        outState.putIntArray(KEY_WIDGET_IDS, ids);
    }

    /**
     *
     */
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        int[] ids = state.getIntArray(KEY_WIDGET_IDS);
        for(int i=0; i<ids.length; ++i) {
            LinearLayout widgets = (LinearLayout) findViewById(R.id.widgets);
            createWidget(ids[i]);
        }
    }

    /**
     * Pick the widget to be added
     */
    private void pickWidget() {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        addEmptyData(pickIntent);
        startActivityForResult(pickIntent, R.id.requestPickAppWidget);
    }

    /**
     * This avoids a bug in the com.android.settings.AppWidgetPickActivity,
     * which is used to select widgets. This just adds empty extras to the
     * intent, avoiding the bug.
     * <p/>
     * See more: http://code.google.com/p/android/issues/detail?id=4272
     */
    private void addEmptyData(Intent pickIntent) {
        ArrayList<AppWidgetProviderInfo> customInfo = new ArrayList<AppWidgetProviderInfo>();
        pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo);
        ArrayList<Bundle> customExtras = new ArrayList<Bundle>();
        pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras);
    }

    /**
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            int id = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            switch(requestCode) {
                case R.id.requestPickAppWidget:
                    configureWidget(id);
                    break;
                case R.id.requestCreateAppWidget:
                    createWidget(id);
                    break;
                default:
                    break;
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
			int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
			if (appWidgetId != -1) {
				appWidgetHost.deleteAppWidgetId(appWidgetId);
			}
		}
    }

    /**
     * Launch widget configuration activity if needed
     */
    private void configureWidget(int id) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
        if(info.configure != null) {
            Intent confIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            confIntent.setComponent(info.configure);
            confIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            startActivityForResult(confIntent, R.id.requestCreateAppWidget);
        } else {
            createWidget(id);
        }
    }

    /**
     * Create the widget
     */
    private void createWidget(int id) {
        Log.d("KISS", "creating widget: " + id);
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(id);
        AppWidgetHostView hostView = appWidgetHost.createView(this, id, appWidgetInfo);

        ViewGroup widgets = (ViewGroup) findViewById(R.id.widgets);
        hostView.setAppWidget(id, appWidgetInfo);
        Log.d("KISS", "Widget holder height: " + widgets.getHeight());
        hostView.setMinimumHeight(widgets.getHeight());
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Log.d("KISS", "Widget minimum height:" + hostView.getMinimumHeight());
        }
//TODO: long clicks are ignored by AppWidgetHostView
//        registerForContextMenu(hostView);
        findViewById(R.id.intro).setVisibility(View.GONE);
        widgets.addView(hostView);

        Log.d("KISS", "Widget height:" + hostView.getHeight());
    }

	/**
	 * Removes the widget displayed by this AppWidgetHostView.
	 */
	public void removeWidget(AppWidgetHostView hostView) {
        appWidgetHost.deleteAppWidgetId(hostView.getAppWidgetId());
        ViewGroup widgetGroup = (ViewGroup) findViewById(R.id.widgets);
        widgetGroup.removeView(hostView);

        if(widgetGroup.getChildCount() == 0) {
            findViewById(R.id.intro).setVisibility(View.VISIBLE);
        }
	}

    /**
     * Empty text field on resume and show keyboard
     */
    @Override
    protected void onResume() {
        if(prefs.getBoolean("require-layout-update", false)) {
            // Restart current activity to refresh view, since some preferences
            // may require using a new UI
            prefs.edit().putBoolean("require-layout-update", false).commit();
            Intent i = new Intent(this, getClass());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                  | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            finish();
            overridePendingTransition(0, 0);
            startActivity(i);
            overridePendingTransition(0, 0);
        }

        if(kissBar.getVisibility() != View.VISIBLE) {
            updateRecords(searchEditText.getText().toString());
            displayClearOnInput();
        } else {
            displayKissBar(false);
        }

        // Activity manifest specifies stateAlwaysHidden as windowSoftInputMode
        // so the keyboard will be hidden by default
        // we may want to display it if the setting is set
        if(prefs.getBoolean("display-keyboard", false)) {
            // Display keyboard
            showKeyboard();

            new Handler().postDelayed(displayKeyboardRunnable, 10);
            // For some weird reasons, keyboard may be hidden by the system
            // So we have to run this multiple time at different time
            // See https://github.com/Neamar/KISS/issues/119
            new Handler().postDelayed(displayKeyboardRunnable, 100);
            new Handler().postDelayed(displayKeyboardRunnable, 500);
        } else {
            // Not used (thanks windowSoftInputMode)
            // unless coming back from KISS settings
            hideKeyboard();
        }

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unregister our receiver
        this.unregisterReceiver(this.mReceiver);
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Empty method,
        // This is called when the user press Home again while already browsing MainActivity
        // onResume() will be called right after, hiding the kissbar if any.
        // http://developer.android.com/reference/android/app/Activity.html#onNewIntent(android.content.Intent)
    }

    @Override
    public void onBackPressed() {
        // Is the kiss menu visible?
        if(menuButton.getVisibility() == View.VISIBLE) {
            displayKissBar(false);
        } else {
            // If no kissmenu, empty the search bar
            searchEditText.setText("");
        }

        // No call to super.onBackPressed, since this would quit the launcher.
    }

    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent e) {
        switch(keycode) {
            case KeyEvent.KEYCODE_MENU:
                // For user with a physical menu button, we still want to display *our* contextual menu
                menuButton.showContextMenu();
                return true;
        }

        return super.onKeyDown(keycode, e);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                return true;
            case R.id.wallpaper:
                hideKeyboard();
                Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                startActivity(Intent.createChooser(intent, getString(R.string.menu_wallpaper)));
                return true;
            case R.id.preferences:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.addWidget:
                pickWidget();
                return true;
            case R.id.remove_widget:
                ViewGroup widgets = (ViewGroup) findViewById(R.id.widgets);
                AppWidgetHostView widgetToRemove = (AppWidgetHostView) widgets.getChildAt(widgets.getChildCount() - 1);
                removeWidget(widgetToRemove);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.remove_widget).setEnabled(((ViewGroup) findViewById(R.id.widgets)).getChildCount() != 0);
        return true;
    }

    /**
     * Display menu, on short or long press.
     *
     * @param menuButton "kebab" menu (3 dots)
     */
    public void onMenuButtonClicked(View menuButton) {
        // When the kiss bar is displayed, the button can still be clicked in a few areas (due to favorite margin)
        // To fix this, we discard any click event occurring when the kissbar is displayed
        if(kissBar.getVisibility() != View.VISIBLE) {
//            menuButton.showContextMenu();
            openOptionsMenu();
        }
    }

    /**
     * Clear text content when touching the cross button
     */
    @SuppressWarnings("UnusedParameters")
    public void onClearButtonClicked(View clearButton) {
        searchEditText.setText("");
    }

    /**
     * Display KISS menu
     */
    public void onLauncherButtonClicked(View launcherButton) {
        // Display or hide the kiss bar, according to current view tag (showMenu / hideMenu).

        displayKissBar(launcherButton.getTag().equals("showMenu"));
    }

    public void onFavoriteButtonClicked(View favorite) {

        // Favorites handling
        Pojo pojo = KissApplication.getDataHandler(MainActivity.this).getFavorites(MainActivity.this, tryToRetrieve)
              .get(Integer.parseInt((String) favorite.getTag()));
        final Result result = Result.fromPojo(MainActivity.this, pojo);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                result.fastLaunch(MainActivity.this);
            }
        }, KissApplication.TOUCH_DELAY);
    }

    private void displayClearOnInput() {
        final View clearButton = findViewById(R.id.clearButton);
        if(searchEditText.getText().length() > 0) {
            clearButton.setVisibility(View.VISIBLE);
            menuButton.setVisibility(View.INVISIBLE);
        } else {
            clearButton.setVisibility(View.INVISIBLE);
            menuButton.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void displayLoader(Boolean display) {
        final View loaderBar = findViewById(R.id.loaderBar);
        final View launcherButton = findViewById(R.id.launcherButton);

        int animationDuration = getResources().getInteger(
              android.R.integer.config_longAnimTime);

        if(!display) {
            launcherButton.setVisibility(View.VISIBLE);

            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Animate transition from loader to launch button
                launcherButton.setAlpha(0);
                launcherButton.animate()
                      .alpha(1f)
                      .setDuration(animationDuration)
                      .setListener(null);
                loaderBar.animate()
                      .alpha(0f)
                      .setDuration(animationDuration)
                      .setListener(new AnimatorListenerAdapter() {
                          @Override
                          public void onAnimationEnd(Animator animation) {
                              loaderBar.setVisibility(View.GONE);
                              loaderBar.setAlpha(1);
                          }
                      });
            } else {
                loaderBar.setVisibility(View.GONE);
            }
        } else {
            launcherButton.setVisibility(View.INVISIBLE);
            loaderBar.setVisibility(View.VISIBLE);
        }
    }

    private void displayKissBar(Boolean display) {
        final ImageView launcherButton = (ImageView) findViewById(R.id.launcherButton);

        // get the center for the clipping circle
        int cx = (launcherButton.getLeft() + launcherButton.getRight()) / 2;
        int cy = (launcherButton.getTop() + launcherButton.getBottom()) / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(kissBar.getWidth(), kissBar.getHeight());

        if(display) {
            // Display the app list
            if(searcher != null) {
                searcher.cancel(true);
            }
            searcher = new ApplicationsSearcher(MainActivity.this);
            searcher.execute();

            // Reveal the bar
            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, 0, finalRadius);
                kissBar.setVisibility(View.VISIBLE);
                anim.start();
            } else {
                // No animation before Lollipop
                kissBar.setVisibility(View.VISIBLE);
            }

            // Retrieve favorites. Try to retrieve more, since some favorites can't be displayed (e.g. search queries)
            ArrayList<Pojo> favoritesPojo = KissApplication.getDataHandler(MainActivity.this)
                  .getFavorites(MainActivity.this, tryToRetrieve);

            if(favoritesPojo.size() == 0) {
                Toast toast = Toast.makeText(MainActivity.this, getString(R.string.no_favorites), Toast.LENGTH_SHORT);
                toast.show();
            }

            // Don't look for items after favIds length, we won't be able to display them
            for(int i = 0; i < Math.min(favsIds.length, favoritesPojo.size()); i++) {
                Pojo pojo = favoritesPojo.get(i);
                ImageView image = (ImageView) findViewById(favsIds[i]);

                Result result = Result.fromPojo(MainActivity.this, pojo);
                Drawable drawable = result.getDrawable(MainActivity.this);
                if(drawable != null)
                    image.setImageDrawable(drawable);
                image.setVisibility(View.VISIBLE);
                image.setContentDescription(pojo.displayName);
            }

            // Hide empty favorites (not enough favorites yet)
            for(int i = favoritesPojo.size(); i < favsIds.length; i++) {
                findViewById(favsIds[i]).setVisibility(View.GONE);
            }

            hideKeyboard();
        } else {
            // Hide the bar
            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, finalRadius, 0);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        kissBar.setVisibility(View.GONE);
                        super.onAnimationEnd(animation);
                    }
                });
                anim.start();
            } else {
                // No animation before Lollipop
                kissBar.setVisibility(View.GONE);
            }
            searchEditText.setText("");
        }
    }

    /**
     * This function gets called on changes. It will ask all the providers for
     * data
     *
     * @param query the query on which to search
     */
    private void updateRecords(String query) {
        if(searcher != null) {
            searcher.cancel(true);
        }

        if(query.length() == 0) {
            if(!prefs.getString("mini-ui", "history").equals("history")) {
                searcher = new NullSearcher(this);
                //Hide default scrollview
                findViewById(R.id.intro).setVisibility(View.GONE);

            } else {
                searcher = new HistorySearcher(this);
                //Show default scrollview
                findViewById(R.id.intro).setVisibility(View.VISIBLE);
            }
        } else {
            searcher = new QuerySearcher(this, query);
        }
        searcher.execute();
    }

    public void resetTask() {
        searcher = null;
    }

    /**
     * Call this function when we're leaving the activity We can't use
     * onPause(), since it may be called for a configuration change
     */
    public void launchOccurred(int index, Result result) {
        // We selected an item on the list,
        // now we can cleanup the filter:
        if(!searchEditText.getText().toString().equals("")) {
            searchEditText.setText("");
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if(view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void showKeyboard() {
        searchEditText.requestFocus();
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    class WidgetContextMenuInfo implements ContextMenu.ContextMenuInfo {
        public View targetedView;
    }
}
