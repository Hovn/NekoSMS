package com.crossbowffs.nekosms.app;

import android.database.Cursor;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.crossbowffs.nekosms.BuildConfig;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.data.SmsFilterAction;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.data.SmsFilterField;
import com.crossbowffs.nekosms.data.SmsFilterMode;
import com.crossbowffs.nekosms.data.SmsFilterPatternData;
import com.crossbowffs.nekosms.loader.FilterRuleLoader;
import com.crossbowffs.nekosms.widget.RecyclerCursorAdapter;

import java.util.ArrayList;
import java.util.List;

/* package */ public class FilterRulesAdapter extends RecyclerCursorAdapter<FilterRulesAdapter.UserFiltersItemHolder> {

    public static class UserFiltersItemHolder extends RecyclerView.ViewHolder {
        public final TextView mActionInfoTextView;
        public final TextView mSenderInfoTextView;
        public final TextView mSenderPatternTextView;
        public final TextView mBodyInfoTextView;
        public final TextView mBodyPatternTextView;
        public SmsFilterData mFilterData;

        public UserFiltersItemHolder(View itemView) {
            super(itemView);
            mActionInfoTextView = (TextView)itemView.findViewById(R.id.filter_rule_action_info_textview);
            mSenderInfoTextView = (TextView)itemView.findViewById(R.id.filter_rule_sender_info_textview);
            mSenderPatternTextView = (TextView)itemView.findViewById(R.id.filter_rule_sender_pattern_textview);
            mBodyInfoTextView = (TextView)itemView.findViewById(R.id.filter_rule_body_info_textview);
            mBodyPatternTextView = (TextView)itemView.findViewById(R.id.filter_rule_body_pattern_textview);
        }
    }

    private final FilterRulesFragment mFragment;
    //private List<SmsFilterData> mSmsFilterDatas;
    private List<SmsFilterData> selectedSmsFilterDatas;//选择的条目，建议在Adapter中来保存？还是在Fragment中来？
    private OnItemListener onItemListener;

    //method
    public List<SmsFilterData> getSelectedSmsFilterDatas() {
        return selectedSmsFilterDatas;
    }

    public boolean isSelectedSmsFilterDataContains(SmsFilterData data) {
        return selectedSmsFilterDatas.contains(data);
    }
    public void addSelectedSmsFilterData(SmsFilterData data) {
        selectedSmsFilterDatas.add(data);
    }
    public void addSelectedAllSmsFilterData( ) {
        clearSelectedSmsFilterData();
        selectedSmsFilterDatas.addAll(getAllItem());
    }
    public void addSelectedFlipSmsFilterData( ) {
        List<SmsFilterData> all = getAllItem();
        if(all.removeAll(selectedSmsFilterDatas)){
            clearSelectedSmsFilterData();
            selectedSmsFilterDatas.addAll(all);
        }
    }
    public void removeSelectedSmsFilterData(SmsFilterData data) {
        selectedSmsFilterDatas.remove(data);
    }
    public void clearSelectedSmsFilterData( ) {
        selectedSmsFilterDatas.clear();
    }

    public FilterRulesAdapter(FilterRulesFragment fragment) {
        mFragment = fragment;
        selectedSmsFilterDatas=new ArrayList<>();
    }

    @Override
    public UserFiltersItemHolder onCreateViewHolder(ViewGroup group, int i) {
        LayoutInflater layoutInflater = LayoutInflater.from(mFragment.getContext());
        View view = layoutInflater.inflate(R.layout.listitem_filter_rules, group, false);
        return new UserFiltersItemHolder(view);

        //将监听放在这，会导致 R.id.menu_action_move_up 等菜单上下移动时，有BUG
//        UserFiltersItemHolder holder = new UserFiltersItemHolder(view);
//        if (onItemListener != null) {
//            Cursor cursor = getCursor();
//            //cursor.moveToPosition(i);
//            SmsFilterData filterData = FilterRuleLoader.get().getData(cursor, getColumns(), holder.mFilterData);
//            holder.itemView.setOnClickListener(v -> onItemListener.onItemClick(v, filterData));
//            holder.itemView.setOnLongClickListener(v -> onItemListener.onItemLongClick(v, filterData));
//        }
//        return holder;
    }

    @Override
    protected int[] onBindColumns(Cursor cursor) {
        return FilterRuleLoader.get().getColumns(cursor);
    }

    @Override
    public void onBindViewHolder(UserFiltersItemHolder holder, Cursor cursor) {
        //不复用视图，修复状态错乱等问题。不建议使用此方法
        //holder.setIsRecyclable(false);

        SmsFilterData filterData = FilterRuleLoader.get().getData(cursor, getColumns(), holder.mFilterData);

        holder.mFilterData = filterData;
        final long id = filterData.getId();

        SmsFilterAction action=filterData.getAction();
        SmsFilterPatternData senderPattern = filterData.getSenderPattern();
        SmsFilterPatternData bodyPattern = filterData.getBodyPattern();

        bindTextViews(action, holder.mActionInfoTextView);
        bindTextViews(senderPattern, holder.mSenderInfoTextView, holder.mSenderPatternTextView);
        bindTextViews(bodyPattern, holder.mBodyInfoTextView, holder.mBodyPatternTextView);
        //appendStringToTextViewOnlyForDebug(" (Priority: "+filterData.getPriority()+")", holder.mActionInfoTextView);

        if (selectedSmsFilterDatas.contains(filterData)) {//最终根据 SmsFilterData.equals() 来判断
            //Log.d("NNN", "选中个数："+selectedSmsFilterDatas.size()+"  【此位置被选中】："+holder.getLayoutPosition()+"  优先级为："+filterData.getPriority()+"  选中列表index："+selectedSmsFilterDatas.indexOf(filterData));
            holder.itemView.setAlpha(0.2f);
        } else {
            //Log.d("NNN", "选中个数："+selectedSmsFilterDatas.size()+"  此位置未被选中："+holder.getLayoutPosition()+"  优先级为："+filterData.getPriority());
            holder.itemView.setAlpha(1.0f);
        }

//        holder.itemView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mFragment.startFilterEditorActivity(id);
//            }
//        });


        if (onItemListener != null) {
            holder.itemView.setOnClickListener(v -> onItemListener.onItemClick(v, filterData));
            //return： true:不继续传递事件，告诉系统此事件已被处理；false:继续传递事件，上级可继续处理（一般建议使用）
            holder.itemView.setOnLongClickListener(v -> onItemListener.onItemLongClick(v, filterData));
        }

    }

    public List<SmsFilterData> getAllItem() {
        Cursor cursor=getCursor();
        List<SmsFilterData> items= new ArrayList<>();
        if (cursor == null) {
            return null;
        } else {
            if (cursor.moveToFirst()) {
                do {
                    SmsFilterData filterData = FilterRuleLoader.get().getData(cursor, getColumns(), null);
                    items.add(filterData);
                } while (cursor.moveToNext());
            }
            return items;
        }
    }



    private void bindTextViews(SmsFilterAction action, TextView infoView) {
        infoView.setText(action.name());
        infoView.setVisibility(View.VISIBLE);
        if(action==SmsFilterAction.ALLOW){
            infoView.setTextColor(Color.GREEN);
            infoView.setAlpha(0.5f);
        }else {
            infoView.setTextColor(Color.RED);
            infoView.setAlpha(0.5f);
        }
    }
    private void appendStringToTextViewOnlyForDebug(String text, TextView infoView) {
        if(BuildConfig.DEBUG){
            infoView.append(text);
        }
    }

    private void bindTextViews(SmsFilterPatternData pattern, TextView infoView, TextView patternView) {
        if (pattern.hasData()) {
            infoView.setText(buildFilterInfoString(R.string.format_filter_info, pattern));
            patternView.setText(pattern.getPattern());
            infoView.setVisibility(View.VISIBLE);
            patternView.setVisibility(View.VISIBLE);
        } else {
            infoView.setText("");
            patternView.setText("");
            infoView.setVisibility(View.GONE);
            patternView.setVisibility(View.GONE);
        }
    }

    private String buildFilterInfoString(int lineId, SmsFilterPatternData patternData) {
        String fieldString = mFragment.getString(getFilterFieldStringId(patternData.getField()));
        String modeString = mFragment.getString(getFilterModeStringId(patternData.getMode()));
        String caseSensitiveString = "";
        if (patternData.isCaseSensitive()) {
            caseSensitiveString = mFragment.getString(R.string.filter_info_case_sensitive);
        }
        return mFragment.getString(lineId, fieldString, modeString, caseSensitiveString);
    }

    private int getFilterFieldStringId(SmsFilterField field) {
        switch (field) {
        case SENDER:
            return R.string.filter_info_field_sender;
        case BODY:
            return R.string.filter_info_field_body;
        default:
            return 0;
        }
    }

    private int getFilterModeStringId(SmsFilterMode mode) {
        switch (mode) {
        case REGEX:
            return R.string.filter_info_mode_regex;
        case WILDCARD:
            return R.string.filter_info_mode_wildcard;
        case CONTAINS:
            return R.string.filter_info_mode_contains;
        case PREFIX:
            return R.string.filter_info_mode_prefix;
        case SUFFIX:
            return R.string.filter_info_mode_suffix;
        case EQUALS:
            return R.string.filter_info_mode_equals;
        default:
            return 0;
        }
    }


    public void setOnItemListener(OnItemListener onItemListener) {
        this.onItemListener = onItemListener;
    }

//    public interface OnItemClickListener {
//        void onItemClick(View view, SmsFilterData smsFilterData);
//        boolean onItemLongClick(View view, SmsFilterData smsFilterData);
//    }

    public interface OnItemListener {
        void onItemClick(View view, SmsFilterData filterData);
        boolean onItemLongClick(View view, SmsFilterData filterData);

        void onItemMove(SmsFilterData filterData1, SmsFilterData filterData2);
        void onItemDrop(SmsFilterData filterData);

        void onSelect(SmsFilterData filterData);
        void onDeselect(SmsFilterData filterData);

//        void onItemChange(SmsFilterData entry);
//        void onItemCopy(SmsFilterData entry);
//        void onPeriodUniformityChanged(boolean uniform, int period);
//        void onListChange();
    }

}
