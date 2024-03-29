package com.octopusbeach.restassured;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.johnpersano.supertoasts.SuperActivityToast;
import com.github.johnpersano.supertoasts.SuperToast;
import com.github.johnpersano.supertoasts.util.OnClickWrapper;
import com.melnykov.fab.FloatingActionButton;
import com.octopusbeach.restassured.model.Item;

import java.util.ArrayList;
import java.util.Calendar;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class HomeActivity extends ActionBarActivity {

    @InjectView(R.id.toolbar)
    Toolbar toolbar;
    @InjectView(R.id.drawer_layout)
    DrawerLayout drawerLayout;
    @InjectView(R.id.left_drawer)
    LinearLayout leftDrawer;
    private ActionBarDrawerToggle drawerToggle;
    @InjectView(R.id.grid)
    GridView gridView;


    @InjectView(R.id.fab)
    FloatingActionButton fab;

    private ArrayList<Item> data;
    private GridAdapter adapter;
    private Item deletedItem; // Holds the last delted Item so that it can be readded.

    private AlarmManager manager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.inject(this);
        manager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        setUpDrawer();
        setUpGridView();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.attachToListView(gridView);
        // See if the app has been opened before.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("firstTime", true)) { // Never been opened.
            Item item = new Item("Turned Off Stove", ColorPicker.getColor(this));
            item.setIsRepeating(true);
            Item item2 = new Item("Swipe Right for Help!", ColorPicker.getColor(this));
            item2.setIsRepeating(true);
            DBHelper db = new DBHelper(HomeActivity.this);
            db.addItem(item);
            db.addItem(item2);
            adapter.clear();
            data = db.getItems();
            adapter.addAll(data);
            adapter.notifyDataSetChanged();
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean("firstTime", false);
            editor.apply();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.fab)
    void newItem() {
        final View v = getLayoutInflater().inflate(R.layout.new_item, null);
        final CheckBox repeatBox = (CheckBox) v.findViewById(R.id.repeat_checkbox);

        v.findViewById(R.id.repeat_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                repeatBox.setChecked(!repeatBox.isChecked());
            }
        });

        final RelativeLayout rl = (RelativeLayout) v.findViewById(R.id.remind_layout);
        final Spinner daySpinner = (Spinner) v.findViewById(R.id.spinner_day);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.date_items,
                android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        daySpinner.setAdapter(spinnerAdapter);
        final Spinner timeSpinner = (Spinner) v.findViewById(R.id.spinner_time);
        final ArrayAdapter<CharSequence> timeAdapter = ArrayAdapter.createFromResource(this, R.array.time_items,
                android.R.layout.simple_spinner_item);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);
        final Button cancelReminder = (Button) v.findViewById(R.id.cancel_reminder);
        cancelReminder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rl.setVisibility(View.GONE);
                cancelReminder.setVisibility(View.GONE);
            }
        });
        v.findViewById(R.id.remind_me).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rl.setVisibility(View.VISIBLE);
                cancelReminder.setVisibility(View.VISIBLE);
            }
        });
        new AlertDialog.Builder(HomeActivity.this)
                .setCancelable(true)
                .setNegativeButton("Cancel", null)
                .setView(v)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Item newItem = new Item(((TextView) v.findViewById(R.id.new_item_title))
                                .getText().toString(), ColorPicker.getColor(HomeActivity.this));
                        newItem.setIsRepeating(repeatBox.isChecked());
                        DBHelper db = new DBHelper(HomeActivity.this);
                        if (rl.getVisibility() == View.VISIBLE) {
                            newItem.setIsReminding(true);
                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(System.currentTimeMillis());
                            // Get the time.
                            if (daySpinner.getSelectedItem().toString().equals("Tomorrow"))  // Schedule for tomorrow
                                c.add(Calendar.DAY_OF_YEAR, 1);
                            c.set(Calendar.HOUR_OF_DAY, getHourForSelection(timeSpinner));
                            c.set(Calendar.MINUTE, 0);
                            c.set(Calendar.SECOND, 0);
                            newItem.setRepeatTime(c);
                            createOrCancelAlarm(newItem, true);
                        } else
                            newItem.setIsReminding(false);
                        db.addItem(newItem);
                        data = db.getItems();
                        adapter.clear();
                        adapter.addAll(data);
                        adapter.notifyDataSetChanged();

                    }
                })
                .show();
    }

    private void createOrCancelAlarm(Item item, boolean create) {
        Intent intent = new Intent(HomeActivity.this, AlarmReceiver.class);
        intent.putExtra("title", item.getName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(HomeActivity.this, 0, intent, 0);
        if (create) {
            if (item.isRepeating()) {
                manager.setRepeating(AlarmManager.RTC_WAKEUP, item.getRepeatTime().getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
            } else
                manager.set(AlarmManager.RTC_WAKEUP, item.getRepeatTime().getTimeInMillis(), pendingIntent);
        } else
            manager.cancel(pendingIntent);
    }

    private int getHourForSelection(Spinner spinner) {
        String time = spinner.getSelectedItem().toString();
        Log.d("Time", time);
        if (time.equals("Morning"))
            return 8;
        if (time.equals("Afternoon"))
            return 12;
        if (time.equals("Evening"))
            return 16;
        if (time.equals("Night"))
            return 20;
        return 0;
    }

    @OnClick(R.id.clear_reminders)
    void clearReminders() {
        new DBHelper(this).deleteAllItems();
        adapter.clear();
        for (Item item : data) {
            if (item.isReminding())
                createOrCancelAlarm(item, false);
        }
        data.clear();
        adapter.notifyDataSetChanged();
        drawerLayout.closeDrawers();
    }

    private void setUpGridView() {
        data = new DBHelper(this).getItems();
        adapter = new GridAdapter(this, R.layout.grid_item, data);
        gridView.setAdapter(adapter);
        // Delete an Item.
        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                deletedItem = data.get(i);
                DBHelper db = new DBHelper(HomeActivity.this);
                db.deleteItem(deletedItem);
                adapter.clear();
                data = db.getItems();
                adapter.addAll(data);
                adapter.notifyDataSetChanged();
                // Cancel alarms.
                if (deletedItem.isReminding())
                    createOrCancelAlarm(deletedItem, false);
                SuperActivityToast superActivityToast = new SuperActivityToast(HomeActivity.this, SuperToast.Type.BUTTON);
                superActivityToast.setDuration(SuperToast.Duration.EXTRA_LONG);
                superActivityToast.setText("Item Deleted.");
                superActivityToast.setButtonIcon(SuperToast.Icon.Dark.UNDO, "UNDO");
                superActivityToast.setOnClickWrapper(onClickWrapper);
                superActivityToast.show();
                return false;
            }
        });
        // Mark an item completed.
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Item item = data.get(i);
                DBHelper db = new DBHelper(HomeActivity.this);
                if (item.isRepeating()) {
                    item.setDate(Calendar.getInstance());
                    int newColor = item.getColor();
                    while (newColor == item.getColor())
                        newColor = ColorPicker.getColor(HomeActivity.this);
                    item.setColor(newColor);
                    db.updateItem(item);
                } else {
                    data.remove(i);
                    db.deleteItem(item);
                    adapter.clear();
                    adapter.addAll(data);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void setUpDrawer() {
        toolbar.setTitle("My Dashboard");
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                toolbar, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                toolbar.setTitle("Reminders");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                toolbar.setTitle("Help");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        drawerLayout.setDrawerListener(drawerToggle);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    // ----------------------- For Super Toasts ---------------------------------------------------
    OnClickWrapper onClickWrapper = new OnClickWrapper("superactivitytoast", new SuperToast.OnClickListener() {
        @Override
        public void onClick(View view, Parcelable token) {
            if (deletedItem != null) {
                DBHelper db = new DBHelper(HomeActivity.this);
                db.addItem(deletedItem);
                adapter.clear();
                data = db.getItems();
                adapter.addAll(data);
                adapter.notifyDataSetChanged();
                if (deletedItem.isReminding())
                    createOrCancelAlarm(deletedItem, true);
            }
        }

    });
}