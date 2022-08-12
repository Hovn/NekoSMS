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
import android.util.Log;
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
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.ItemTouchHelper;
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

import java.util.List;

public class FilterRulesFragment extends MainFragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        ActionMode.Callback,
        FilterRulesAdapter.OnItemListener,
        MyItemTouchHelperCallback.IMoveAndSwipeCallback {
    private static final int IMPORT_BACKUP_REQUEST = 1853;
    private static final int EXPORT_BACKUP_REQUEST = 1854;
    public static final String EXTRA_ACTION = "action";
    public static final String ARG_IMPORT_URI = "import_uri";

    private ListRecyclerView mRecyclerView;
    private TextView mEmptyView;
    private FilterRulesAdapter mAdapter;
    private ItemTouchHelper mItemTouchHelper;

    private ActionMode actionMode;
    private Cursor mCursor;
    /* true：长按调出ActionMode；false：长按调出上下文菜单  */
    //private final boolean useActionMode=false;
    public final boolean useFabButton=false;
    public final boolean useContextMenu=false;
    public final boolean useActionMode=!useContextMenu;
    public final boolean allowDrag=false;//仅在使用actionMode时（即useContextMenu=false时）有效
    public final boolean allowSwipe=false;//仅在使用actionMode时（即useContextMenu=false时）有效
    //private List<SmsFilterData> selectedSmsFilterDatas = new ArrayList<>();

//    public ActionMode getActionMode() {
//        return actionMode;
//    }
//
//    public List<SmsFilterData> getSelectedSmsFilterDatas() {
//        return selectedSmsFilterDatas;
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onDestroy() {
        if(actionMode!=null){
            actionMode.finish();
        }
        super.onDestroy();
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

        //set some text
        setTitle(R.string.list_rules);
        mEmptyView.setText(R.string.list_rules_empty_text);

        // Initialize filter list
        FilterRulesAdapter adapter = new FilterRulesAdapter(this);
        mAdapter = adapter;
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(0, null, this);
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setEmptyView(mEmptyView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        if(useContextMenu)registerForContextMenu(mRecyclerView);

        //给列表的条目设置监听器
        mAdapter.setOnItemListener(this);

        //给RecyclerView设置ItemTouchHelper
        MyItemTouchHelperCallback itemTouchHelperCallback = new MyItemTouchHelperCallback();
        itemTouchHelperCallback.setEnableDrag(allowDrag);
        itemTouchHelperCallback.setEnableSwipe(allowSwipe);
        itemTouchHelperCallback.setIMoveAndSwipeCallback(this);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        /* if (allowDragAndSwipe) */itemTouchHelper.attachToRecyclerView(mRecyclerView);


        // Display create FAB
        enableFab(R.drawable.ic_baseline_add_24, v -> jumpToFilterEditorActivityWithAnimation());
        getMainActivity().showFab(useFabButton);

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
                //FilterRuleLoader.get().swapId(getContext(), info.mId, mAdapter.getItemId(info.mPosition - 1));
                FilterRuleLoader.get().swapPriority(getContext(),mAdapter, info.mPosition, info.mPosition-1);
                return true;
            case R.id.menu_item_down:
                //FilterRuleLoader.get().swapId(getContext(), info.mId, mAdapter.getItemId(info.mPosition + 1));
                FilterRuleLoader.get().swapPriority(getContext(),mAdapter, info.mPosition, info.mPosition+1);
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
            case R.id.menu_item_add_filters:
                jumpToFilterEditorActivity();
                return true;
            case R.id.menu_item_import_export_filters:
                showImportExportDialog();
                return true;
            case R.id.menu_item_optimize_priority_filters:
                optimizePriorityDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    private void jumpToFilterEditorActivity() {
        startActivity(new Intent(getContext(),FilterEditorActivity.class));
    }

    private void jumpToFilterEditorActivityWithAnimation() {
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

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(
                getContext(),
                DatabaseContract.FilterRules.CONTENT_URI,
                DatabaseContract.FilterRules.ALL,
                null,
                null,
                DatabaseContract.FilterRules.PRIORITY + " DESC"//ASC DESC 根据优先级排序
                //null
        );

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCursor = data;
        mAdapter.changeCursor(data);
        updateActionMode(false);//删除后更新操作栏的信息（放到这了，因为执行顺序的关系）
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursor = null;
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
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startActivityForResult(BackupLoader.getImportFilePickerIntent(), IMPORT_BACKUP_REQUEST);
                    } else if (which == 1) {
                        startActivityForResult(BackupLoader.getExportFilePickerIntent(), EXPORT_BACKUP_REQUEST);
                    }
                })
                .show();
    }

    private void optimizePriorityDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.optimize_priority)
                .setMessage("当发现移动条目出现一些奇怪现象时，可能是由于优先级字段（priority）出现了重复值。例如导入了版本3及以下的备份文件。\n" +
                        "此时需要进行修复，将对所有条目的优先级进行重新计算并排序！\n" +
                        "执行此操作不会对正常的列表造成影响，所以无论何时，你可以放心的执行此操作。")
                .setPositiveButton("开始", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<SmsFilterData> all = mAdapter.getAllItem();
                        for (int i = 0, j = all.size()-1; i <all.size() ; i++, j--) {
                            SmsFilterData data = all.get(i);
                            data.setPriority(j);
                            FilterRuleLoader.get().update(getContext(), data.getUri(), data, false);
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
        mAdapter.notifyDataSetChanged();

        showSnackbar(R.string.filter_deleted, R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterRuleLoader.get().insert(getContext(), filterData);
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    public void startFilterEditorActivity(long id) {
        Intent intent = new Intent(getContext(), FilterEditorActivity.class);
        Uri filterUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, id);
        intent.setData(filterUri);
        startActivity(intent);
    }

    //Action Mode
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (actionMode == null) {
            getMainActivity().hideFab();
            //getMainActivity().getFloatingActionButton().setVisibility(View.GONE);
            actionMode = mode;
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.actionmode_filter_rules, menu);
            //actionMode.setTitle(getString(R.string.message_multi_selected,selectedSmsFilterDatas.size()));
            //actionMode.setTitle("num:"+selectedSmsFilterDatas.size());

            mAdapter.notifyDataSetChanged();// 更新列表界面，否则无法显示已选的item
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int menuItemId = item.getItemId();
        switch (menuItemId) {
            case R.id.menu_action_move_up:
            case R.id.menu_action_move_down:
                //onMove(0,1);
                SmsFilterData currentData = mAdapter.getSelectedSmsFilterDatas().get(0);
                SmsFilterData toData;
                boolean isClickUp;
                if(menuItemId==R.id.menu_action_move_up){
                    isClickUp=true;
                    toData = FilterRuleLoader.get().queryDataOfHigherPriority(getContext(), currentData.getPriority());
                }else {
                    isClickUp=false;
                    toData = FilterRuleLoader.get().queryDataOfLowerPriority(getContext(), currentData.getPriority());
                }
                //Log.d("NNN", currentData.getSenderPattern().getPattern()+"  当前："+currentData.getPriority());

                if(toData==null){
                    showSnackbar(isClickUp?"该条目已在最上面！":"该条目已在最下面！");
                }else{
                    FilterRuleLoader.get().swapPriority(getContext(),currentData.getId(), toData.getId());
                    mAdapter.getSelectedSmsFilterDatas().get(0).setPriority(toData.getPriority());
                    //mAdapter.clearSelectedSmsFilterData();
                    //currentData.setPriority(previousData.getPriority());
                    //mAdapter.addSelectedSmsFilterData(currentData);

                    //Log.d("NNN", currentData.getSenderPattern().getPattern()+"  "+"--> "+toData.getPriority());

                    int newPosition=FilterRuleLoader.get().queryNumHigherPriority(getContext(), toData.getPriority());
                    //LinearLayoutManager linearLayoutManager=(LinearLayoutManager)mRecyclerView.getLayoutManager();
                    //linearLayoutManager.scrollToPosition(newPosition);
                    //mRecyclerView.smoothScrollToPosition(newPosition);
                    mRecyclerView.scrollToPosition(newPosition);

//                    new Handler().postDelayed(() -> {
//                        int newPosition=FilterRuleLoader.get().queryNumHigherPriority(getContext(), toData.getPriority());
//                        //((LinearLayoutManager)mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(newPosition,0);
//                        //mRecyclerView.smoothScrollToPosition(newPosition);
//                        smoothMoveToPosition(mRecyclerView, newPosition);
//
//                    }, 1000);

                }
                return true;
            case R.id.menu_action_delete:
                new AlertDialog.Builder(getContext())
                        .setTitle("警告")
                        .setMessage("删除后无法恢复，确认？")
                        .setNegativeButton("我再想想", null)
                        .setPositiveButton("确定删除", (dialog, which) -> deleteSelectedFilters())
                        .show();
                //deleteSelectedFilters();
                //mode.finish();
                return true;
            case R.id.menu_action_all_select:// 全选
                mAdapter.addSelectedAllSmsFilterData();
                updateActionMode(false);
                mAdapter.notifyDataSetChanged();
                return true;
            case R.id.menu_action_inv_select:// 反选
                mAdapter.addSelectedFlipSmsFilterData();
                updateActionMode(false);
                mAdapter.notifyDataSetChanged();
                return true;
            default:
                return false;
        }
    }

    /**
     *
     * @param isSelectedNunEq0ThenCloseActionMode true-选择项为0时结束操作栏；false-反之，保持
     */
    private synchronized void updateActionMode(boolean isSelectedNunEq0ThenCloseActionMode) {
        if(actionMode==null){
            return;
        }
        //删除全部后，结束操作栏模式。因为del后需要重新load，在del的方法中获取数量是删除之前的数量（还没加载完）。
        //故在 onLoadFinished() 中也进行操作栏的更新。~~~
        if(mAdapter.getItemCount()==0){
            actionMode.finish();
            return;//重要
        }

        int selectedNum=mAdapter.getSelectedSmsFilterDatas().size();
        if (selectedNum == 0 && isSelectedNunEq0ThenCloseActionMode) {
            actionMode.finish();
        } else {
//            _entryListFragment.setIsLongPressDragEnabled(!multipleSelected);
//            _actionMode.getMenu().findItem(R.id.action_edit).setVisible(!multipleSelected);
//            _actionMode.getMenu().findItem(R.id.action_copy).setVisible(!multipleSelected);
            actionMode.getMenu().findItem(R.id.menu_action_move_up).setVisible(selectedNum==1);
            actionMode.getMenu().findItem(R.id.menu_action_move_down).setVisible(selectedNum==1);

            actionMode.setTitle(getString(R.string.item_multi_selected,selectedNum));
            //mAdapter.notifyDataSetChanged();
        }

    }

    private void addOrRemove(SmsFilterData originData) {
        //请注意：originData 为 holder保持的，当随着滑动导致holder被复用时，会发生改变！！！
        //所以，添加到 已选中 List 中时，根据 id 进行查询后获取一个新的内存变量！！！

        // 如果包含，则取消选择；如果不包含，则添加选择
        SmsFilterData data = FilterRuleLoader.get().query(getContext(), originData.getId());
        if (mAdapter.isSelectedSmsFilterDataContains(data)) {
            mAdapter.removeSelectedSmsFilterData(data);
        } else {
            mAdapter.addSelectedSmsFilterData(data);
        }

        // 更新ActionMode的相关信息
        updateActionMode(true);
        mAdapter.notifyDataSetChanged();
    }
    private void deleteSelectedFilters() {
        for (SmsFilterData data:mAdapter.getSelectedSmsFilterDatas()){
            FilterRuleLoader.get().delete(getContext(), data.getId());
        }
        mAdapter.clearSelectedSmsFilterData();
        //updateActionMode(false);
        mAdapter.notifyDataSetChanged();

        //此处直接获取的话，还是删除前的数量，经Log打印排查后，是 onLoadFinished() 没loaded完
        //Log.d("NNN", "删除后剩余数量："+mAdapter.getItemCount());
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        getMainActivity().showFab(useFabButton);
        //getMainActivity().getFloatingActionButton().setVisibility(View.VISIBLE);

        actionMode = null;
        mAdapter.clearSelectedSmsFilterData();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMove(int fromPosition, int toPosition) {
        //long fromId = mAdapter.getItemId(fromPosition);
        //long toId = mAdapter.getItemId(toPosition);
        FilterRuleLoader.get().swapPriority(getContext(), mAdapter,fromPosition, toPosition);



        //Collections.swap(strings, prePosition, postPosition);

//        if (fromPosition == toPosition) {
//            return;
//        }
//        if (fromPosition < toPosition) {
//            for (int i = fromPosition; i < toPosition; i++) {
//                //Collections.swap(list, i, i + 1);
//                FilterRuleLoader.get().swapPriority(getContext(), mAdapter,i, i+1);
//                //FilterRuleLoader.get().swapPriorityByExecUpdate(getContext(), mAdapter,i, i+1);
//            }
//        } else {
//            for (int i = fromPosition; i > toPosition; i--) {
//                //Collections.swap(list, i, i - 1);
//                FilterRuleLoader.get().swapPriority(getContext(), mAdapter,i, i-1);
//                //FilterRuleLoader.get().swapPriorityByExecUpdate(getContext(), mAdapter,i, i-1);
//            }
//        }
        Log.i("NekoSMS ", fromPosition+" --> "+toPosition);

        //因为界面是loader加载的，会自动刷新，不用进行特殊通知
        //mAdapter.notifyItemMoved(fromPosition, toPosition);

        //int fromPosition = viewHolder.getAdapterPosition();
        //int toPosition = target.getAdapterPosition();
//        if (fromPosition == toPosition) {
//            return;
//        }

//        SmsFilterData smsFilterData=FilterRuleLoader.get().query(getContext(), mAdapter.getItemId(fromPosition));
//        SmsFilterData smsFilterData2=FilterRuleLoader.get().query(getContext(), mAdapter.getItemId(toPosition));
        //SmsFilterData smsFilterData2=((AllFilterRulesAdapter.UserFiltersItemHolder)target).mFilterData;

        //if(toPosition!=toPosition_tmp){
//        Log.i("NekoSMS ", fromPosition+" --> "+toPosition+" , "+smsFilterData.getAction()+","+smsFilterData2.getAction());

//            long fromId = mAdapter.getItemId(fromPosition);
//            long toId = mAdapter.getItemId(toPosition);
//            Uri fromUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, fromId);
//            Uri toUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, toId);
//            SmsFilterData fromFilter = FilterRuleLoader.get().query(getContext(), fromUri);
//            SmsFilterData toFilter = FilterRuleLoader.get().query(getContext(), toUri);
//
//            fromFilter.setId(toId);
//            toFilter.setId(fromId);
//            updateFilter(fromUri, toFilter);
//            updateFilter(toUri, fromFilter);

//        FilterRuleLoader.get().swapId(getContext(), smsFilterData, smsFilterData2);
//        mAdapter.notifyItemMoved(fromPosition, toPosition);
        //mAdapter.changeCursor(FilterRuleLoader.get().query_full_custom_cbh(getContext(), null, null, null));





//            toPosition_tmp=toPosition;
//        }


//        long fromId =mAdapter.getItemId(fromPosition);
//        long toId   =mAdapter.getItemId(toPosition);
//        Uri fromUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, fromId);
//        Uri toUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, toId);
//        SmsFilterData fromFilter=FilterRuleLoader.get().query(getContext(), fromUri);
//        SmsFilterData toFilter=FilterRuleLoader.get().query(getContext(), toUri);
//
//        fromFilter.setId(toId);
//        toFilter.setId(fromId);
//        updateFilter(fromUri, toFilter);
//        updateFilter(toUri, fromFilter);

        //mAdapter.notifyItemMoved(fromPosition, toPosition);

//        if (mRecyclerView.getAdapter() != null) {
//            mRecyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
//        }

    }

    @Override
    public void onSwiped(int position) {
        //int position=viewHolder.getAdapterPosition();
        //long id=mRecyclerView.getAdapter().getItemId(position);
        deleteFilter(mAdapter.getItemId(position));
        //strings.remove(position);

        //mRecyclerView.getAdapter()
//        if (mAdapter != null) {
//            mAdapter.notifyItemRemoved(position);
//        }
        //Log.i("onSwiped", position + ","+id);//END 32   START 16
    }

    @Override
    public void onItemClick(View view, SmsFilterData filterData) {
        if (actionMode == null) {
            startFilterEditorActivity(filterData.getId());
        }else {
            //多选模式，选择其他项
            addOrRemove(filterData);
        }
    }

    @Override
    public boolean onItemLongClick(View view, SmsFilterData filterData) {
        if (actionMode == null && !useContextMenu) {
            actionMode = getMainActivity().startSupportActionMode(FilterRulesFragment.this);
        }
        if(useActionMode){
            addOrRemove(filterData);
            return true;//不传递给父级监听器。长时间长按，直接触发长按，不用等松手
        }
        return false;//传递给父类的监听器（唤出上下文菜单）。长时间长按，等松手才会触发。
    }

    @Override
    public void onItemMove(SmsFilterData filterData1, SmsFilterData filterData2) {

    }

    @Override
    public void onItemDrop(SmsFilterData filterData) {

    }

    @Override
    public void onSelect(SmsFilterData filterData) {

    }

    @Override
    public void onDeselect(SmsFilterData filterData) {

    }
}
