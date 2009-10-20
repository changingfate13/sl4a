package com.google.ase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.google.ase.interpreter.Interpreter;
import com.google.ase.interpreter.InterpreterInstaller;
import com.google.ase.interpreter.InterpreterUninstaller;
import com.google.ase.interpreter.InterpreterUtils;
import com.google.ase.terminal.Terminal;

public class InterpreterManager extends ListActivity {

  private static enum RequestCode {
    INSTALL_INTERPRETER, UNINSTALL_INTERPRETER
  }

  private HashMap<Integer, Interpreter> installerMenuIds;

  private static enum MenuId {
    HELP, ADD, DELETE;
    public int getId() {
      return ordinal() + Menu.FIRST;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    setContentView(R.layout.list);
    CustomWindowTitle.buildWindowTitle(this);

    listInterpreters();
    registerForContextMenu(getListView());
  }

  @Override
  protected void onResume() {
    super.onResume();
    listInterpreters();
  }

  /**
   * Populates the list view with all available interpreters.
   */
  private void listInterpreters() {
    // Get all of the rows from the database and create the item list
    List<Interpreter> interpreters = InterpreterUtils.getInstalledInterpreters();

    // Build up a simple list of maps. Just one attribute we care about
    // currently.
    List<Map<String, String>> data = new ArrayList<Map<String, String>>();
    for (Interpreter interpreter : interpreters) {
      Map<String, String> map = new HashMap<String, String>();
      map.put(Constants.EXTRA_INTERPRETER_NAME, interpreter.getName());
      data.add(map);
    }

    Collections.sort(data, new Comparator<Map<String, String>>() {
      public int compare(Map<String, String> m1, Map<String, String> m2) {
        return m1.get(Constants.EXTRA_INTERPRETER_NAME).compareTo(
            m2.get(Constants.EXTRA_INTERPRETER_NAME));
      }
    });

    // Create an array to specify the fields we want to display in the list
    // (only TITLE)
    String[] from = new String[] { Constants.EXTRA_INTERPRETER_NAME };

    // and an array of the fields we want to bind those fields to (in this case
    // just text1)
    int[] to = new int[] { R.id.text1 };

    // Now create a simple cursor adapter and set it to display
    SimpleAdapter scripts = new SimpleAdapter(this, data, R.layout.row, from, to);
    setListAdapter(scripts);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    menu.clear();
    buildMenuIdMaps();
    buildInstallLanguagesMenu(menu);
    menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help");
    return true;
  }

  private void buildMenuIdMaps() {
    installerMenuIds = new HashMap<Integer, Interpreter>();
    int i = MenuId.values().length + Menu.FIRST;
    List<Interpreter> notInstalled = InterpreterUtils.getNotInstalledInterpreters();
    for (Interpreter interpreter : notInstalled) {
      installerMenuIds.put(i, interpreter);
      ++i;
    }
  }

  private void buildInstallLanguagesMenu(Menu menu) {
    if (InterpreterUtils.getNotInstalledInterpreters().size() > 0) {
      SubMenu installMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, "Add Interpreter");
      for (Entry<Integer, Interpreter> entry : installerMenuIds.entrySet()) {
        installMenu.add(Menu.NONE, entry.getKey(), Menu.NONE, entry.getValue().getNiceName());
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == MenuId.HELP.getId()) {
      // Show documentation.
      Intent intent = new Intent();
      intent.setAction(Intent.ACTION_VIEW);
      intent.setData(Uri.parse(getString(R.string.wiki_url)));
      startActivity(intent);
    } else if (installerMenuIds.containsKey(itemId)) {
      // Install selected interpreter.
      Interpreter interpreter = installerMenuIds.get(itemId);
      installInterpreter(interpreter);
    }
    return super.onOptionsItemSelected(item);
  }

  private void installInterpreter(Interpreter interpreter) {
    Intent intent = new Intent(this, InterpreterInstaller.class);
    intent.putExtra(Constants.EXTRA_INTERPRETER_NAME, interpreter.getName());
    startActivityForResult(intent, RequestCode.INSTALL_INTERPRETER.ordinal());
  }

  private void launchTerminal(Interpreter interpreter) {
    Intent i = new Intent(this, Terminal.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK & Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    i.putExtra(Constants.EXTRA_INTERPRETER_NAME, interpreter.getName());
    startActivity(i);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void onListItemClick(ListView list, View view, int position, long id) {
    super.onListItemClick(list, view, position, id);
    Map<String, String> item = (Map<String, String>) list.getItemAtPosition(position);
    String interpreterName = item.get(Constants.EXTRA_INTERPRETER_NAME);
    launchTerminal(InterpreterUtils.getInterpreterByName(interpreterName));
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
    menu.add(Menu.NONE, MenuId.DELETE.getId(), Menu.NONE, "Uninstall");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterView.AdapterContextMenuInfo info;
    try {
      info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
    } catch (ClassCastException e) {
      AseLog.e("Bad MenuInfo", e);
      return false;
    }

    Map<String, String> interpreterItem =
        (Map<String, String>) getListAdapter().getItem(info.position);
    if (interpreterItem == null) {
      AseLog.v(this, "No interpreter selected.");
      return false;
    }

    String interpreterName = interpreterItem.get(Constants.EXTRA_INTERPRETER_NAME);
    if (!InterpreterUtils.getInterpreterByName(interpreterName).isUninstallable()) {
      AseLog.v(this, "Cannot uninstall " + interpreterName);
      return true;
    }

    int itemId = item.getItemId();
    if (itemId == MenuId.DELETE.getId()) {
      Intent intent = new Intent(this, InterpreterUninstaller.class);
      intent.putExtra(Constants.EXTRA_INTERPRETER_NAME, interpreterName);
      startActivityForResult(intent, RequestCode.UNINSTALL_INTERPRETER.ordinal());
    }
    return true;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    RequestCode request = RequestCode.values()[requestCode];
    if (resultCode == RESULT_OK) {
      switch (request) {
        case INSTALL_INTERPRETER:
          break;
        case UNINSTALL_INTERPRETER:
          AseLog.v(this, "Uninstallation successful.");
          break;
        default:
          break;
      }
    } else {
      switch (request) {
        case INSTALL_INTERPRETER:
          break;
        case UNINSTALL_INTERPRETER:
          AseLog.v(this, "Uninstallation failed.");
          break;
        default:
          break;
      }
    }
    listInterpreters();
  }
}