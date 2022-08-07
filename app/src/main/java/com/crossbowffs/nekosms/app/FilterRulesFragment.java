package com.crossbowffs.nekosms.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.backup.BackupLoader;
import com.crossbowffs.nekosms.backup.ExportResult;
import com.crossbowffs.nekosms.backup.ImportResult;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.loader.FilterRuleLoader;
import com.crossbowffs.nekosms.provider.DatabaseContract;
import com.crossbowffs.nekosms.utils.Xlog;
import com.crossbowffs.nekosms.widget.DialogAsyncTask;
import com.crossbowffs.nekosms.widget.ListRecyclerView;

public class FilterRulesFragment
        extends MainFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final int IMPORT_BACKUP_REQUEST = 1853;
    private static final int EXPORT_BACKUP_REQUEST = 1854;
    public static final String EXTRA_ACTION = "action";
    public static final String ARG_IMPORT_URI = "import_uri";

    private ListRecyclerView mRecyclerView;
    private TextView mEmptyView;
    private FilterRulesAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_filter_rules, container, false);
        mRecyclerView = (ListRecyclerView)view.findViewById(R.id.filter_rules_recyclerview);
        mEmptyView = (TextView)view.findViewById(android.R.id.empty);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Initialize filter list
        FilterRulesAdapter adapter = new FilterRulesAdapter(this);
        mAdapter = adapter;
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(0, null, this);
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setEmptyView(mEmptyView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        registerForContextMenu(mRecyclerView);

        // Display create FAB
        enableFab(R.drawable.ic_baseline_add_24, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), FilterEditorActivity.class);
                //intent.putExtra(FilterEditorActivity.EXTRA_ACTION, null);
                //startActivity(intent);
                //getMainActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

                //退出动画
                //Slide slide = TransitionInflater.from(getContext()).inflateTransition(R.transition.activity_slide);
//                getActivity().getWindow().setExitTransition(
//                        //new Slide(Gravity.LEFT)
//                        new Fade()
//                                .setDuration(500)
//                                .excludeTarget(android.R.id.statusBarBackground, true)
//                                //.excludeTarget(R.id.toolbar,true)
//                                .excludeTarget(R.id.main_fab, true)
//                                .excludeTarget(R.id.fab_add_rule_done, true)
//                );
                //getActivity().getWindow().setExitTransition(new Fade().setDuration(500));

                //返回到动画。不定义则默认为退出动画的反向
                //getActivity().getWindow().setReenterTransition(new Slide().setDuration(1000));

                ActivityOptions transitionActivityOptions = ActivityOptions.makeSceneTransitionAnimation(getActivity());
                startActivity(intent,transitionActivityOptions.toBundle());

                //TransitionManager.beginDelayedTransition(mRecyclerView, new Slide(Gravity.RIGHT).setDuration(3000));
                //TransitionManager.beginDelayedTransition(mRecyclerView, new Fade().setDuration(3000));
            }
        });
        
        setTitle(R.string.list_rules);
        mEmptyView.setText(R.string.list_rules_empty_text);
        // Handle import requests as necessary
        onNewArguments(getArguments());
    }

    @Override
    public void onNewArguments(Bundle args) {
        if (args == null) {
            return;
        }

        Uri importUri = args.getParcelable(ARG_IMPORT_URI);
        if (importUri != null) {
            args.remove(ARG_IMPORT_URI);
            showConfirmImportDialog(importUri);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_filter_rules, menu);
        menu.setHeaderTitle(R.string.filter_actions);

        int clickPosition = ((ListRecyclerView.ContextMenuInfo)menuInfo).mPosition;
        if(clickPosition==0){
            menu.findItem(R.id.menu_item_up).setEnabled(false);
        }else if(clickPosition==mAdapter.getItemCount()-1){
            menu.findItem(R.id.menu_item_down).setEnabled(false);
        }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ListRecyclerView.ContextMenuInfo info = (ListRecyclerView.ContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.menu_item_edit_filter:
                startFilterEditorActivity(info.mId);
                return true;
            case R.id.menu_item_delete_filter:
                deleteFilter(info.mId);
                return true;
            case R.id.menu_item_up:
                FilterRuleLoader.get().swapId(getContext(), info.mId, mAdapter.getItemId(info.mPosition - 1));
                return true;
            case R.id.menu_item_down:
                FilterRuleLoader.get().swapId(getContext(), info.mId, mAdapter.getItemId(info.mPosition + 1));
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.options_filter_rules, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_item_import_export_filters:
            showImportExportDialog();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(
                getContext(),
                DatabaseContract.FilterRules.CONTENT_URI,
                DatabaseContract.FilterRules.ALL,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.changeCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == IMPORT_BACKUP_REQUEST) {
            showConfirmImportDialog(data.getData());
        } else if (requestCode == EXPORT_BACKUP_REQUEST) {
            exportFilterRules(data.getData());
        }
    }

    private void showImportExportDialog() {
        CharSequence[] items = {getString(R.string.import_from_storage), getString(R.string.export_to_storage)};
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.import_export)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        startActivityForResult(BackupLoader.getImportFilePickerIntent(), IMPORT_BACKUP_REQUEST);
                    } else if (which == 1) {
                        startActivityForResult(BackupLoader.getExportFilePickerIntent(), EXPORT_BACKUP_REQUEST);
                    }
                }
            })
            .show();
    }

    private void showConfirmImportDialog(final Uri uri) {
        new AlertDialog.Builder(getContext())
            .setIcon(R.drawable.ic_baseline_warning_24)
            .setTitle(R.string.import_confirm_title)
            .setMessage(R.string.import_confirm_message)
            .setPositiveButton(R.string.backup_button_import, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    importFilterRules(uri);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @SuppressLint("StaticFieldLeak")
    private void importFilterRules(final Uri uri) {
        new DialogAsyncTask<Void, Void, ImportResult>(getContext(), R.string.progress_importing) {
            @Override
            protected ImportResult doInBackground(Void... params) {
                return BackupLoader.importFilterRules(getContext(), uri);
            }

            @Override
            protected void onPostExecute(ImportResult result) {
                super.onPostExecute(result);
                int messageId;
                switch (result) {
                case SUCCESS:
                    messageId = R.string.import_success;
                    break;
                case UNKNOWN_VERSION:
                    messageId = R.string.import_unknown_version;
                    break;
                case INVALID_BACKUP:
                    messageId = R.string.import_invalid_backup;
                    break;
                case READ_FAILED:
                    messageId = R.string.import_read_failed;
                    break;
                default:
                    throw new AssertionError("Unknown backup import result code: " + result);
                }
                showSnackbar(messageId);
            }
        }.execute();
    }

    @SuppressLint("StaticFieldLeak")
    private void exportFilterRules(final Uri uri) {
        new DialogAsyncTask<Void, Void, ExportResult>(getContext(), R.string.progress_exporting) {
            @Override
            protected ExportResult doInBackground(Void... params) {
                return BackupLoader.exportFilterRules(getContext(), uri);
            }

            @Override
            protected void onPostExecute(ExportResult result) {
                super.onPostExecute(result);
                int messageId;
                switch (result) {
                case SUCCESS:
                    messageId = R.string.export_success;
                    break;
                case WRITE_FAILED:
                    messageId = R.string.export_write_failed;
                    break;
                default:
                    throw new AssertionError("Unknown backup export result code: " + result);
                }
                showSnackbar(messageId);
            }
        }.execute();
    }

    private void deleteFilter(long filterId) {
        final SmsFilterData filterData = FilterRuleLoader.get().queryAndDelete(getContext(), filterId);
        if (filterData == null) {
            Xlog.e("Failed to delete filter: could not load data");
            return;
        }

        showSnackbar(R.string.filter_deleted, R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterRuleLoader.get().insert(getContext(), filterData);
            }
        });
    }

    public void startFilterEditorActivity(long id) {
        Intent intent = new Intent(getContext(), FilterEditorActivity.class);
        Uri filterUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, id);
        intent.setData(filterUri);
        startActivity(intent);
    }
}
