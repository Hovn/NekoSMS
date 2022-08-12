package com.crossbowffs.nekosms.app;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class MyItemTouchHelperCallback extends ItemTouchHelper.Callback {
//    private VaultEntry _selectedEntry;
//    private final ItemTouchHelperAdapter adapter;

//    private boolean _positionChanged = false;
//    private boolean _isLongPressDragEnabled = true;

    public void setEnableDrag(boolean enableDrag) {
        this.enableDrag = enableDrag;
    }

    public void setEnableSwipe(boolean enableSwipe) {
        this.enableSwipe = enableSwipe;
    }

    private boolean enableDrag = true;
    private boolean enableSwipe = true;

    private IMoveAndSwipeCallback iMoveAndSwipeCallback;

    public void setIMoveAndSwipeCallback(IMoveAndSwipeCallback iMoveAndSwipeCallback) {
        this.iMoveAndSwipeCallback = iMoveAndSwipeCallback;
    }

    // 3个方法必须实现

    /**
     * 设置 item 拖拽及滑动的可支持方向。必须实现。
     * @param recyclerView
     * @param viewHolder
     * @return
     */
    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        //支持上下拖拽
        final int dragFlag = ItemTouchHelper.UP | ItemTouchHelper.DOWN ;//  | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        //支持左右划动
        //final int swipeFlag = ItemTouchHelper.START| ItemTouchHelper.END;
        final int swipeFlag = ItemTouchHelper.LEFT| ItemTouchHelper.RIGHT;
        //表示不支持左右划动
        //final int swipeFlag = 0;
        return makeMovementFlags(dragFlag, swipeFlag);
    }

    /**
     * 拖拽结束后（手指抬起）会回调的方法。必须实现。
     * @param recyclerView 列表
     * @param viewHolder 手指拖拽的item
     * @param target 移动到的 item
     * @return
     */
    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        //拖拽的viewHolder的Position
        int fromPosition = viewHolder.getAdapterPosition();
        //当前拖拽到的item的viewHolder
        int toPosition = target.getAdapterPosition();
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                //Collections.swap(list, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                //Collections.swap(list, i, i - 1);
            }
        }
        //myAdapter.notifyItemMoved(fromPosition, toPosition);


        if (iMoveAndSwipeCallback != null) {
            iMoveAndSwipeCallback.onMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        }
        return true;
    }

    /**
     * 左右划回调。必须实现。
     * @param viewHolder
     * @param direction 方向
     */
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        if (iMoveAndSwipeCallback != null) {
            iMoveAndSwipeCallback.onSwiped(viewHolder.getAdapterPosition());
        }
    }


    //================================以下方法自由选择是否覆写
	
    /**
     * 长按选中Item时修改颜色
     * 比如当用户在拖拽排序的时候，可以改变当前拖拽 item 的透明度，这样就可以和其他 item 区分开来了
     * @param viewHolder
     * @param actionState
     */
    //@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
		
        //if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
        //viewHolder.itemView.setBackground(getDrawable(R.drawable.card_drag_selected));
        //}

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            //viewHolder.itemView.setBackgroundColor(Color.WHITE);
        }

    }

    /**
     * 手指松开的时候还原颜色
     * 相对应地，当用户手指从拖拽 item 中抬起的时候，我们需要把 item 的透明度复原。
     * @param recyclerView
     * @param viewHolder
     */
    //@RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        //viewHolder.itemView.setBackground(getDrawable(R.drawable.card));
        //viewHolder.itemView.getBackground().setAlpha(0);
        //viewHolder.itemView.setBackgroundColor(Color.WHITE);
		
    }

    /**
     * 重写拖拽不可用
     *
     * @return false-不可拖拽
     */
    @Override
    public boolean isLongPressDragEnabled() {
        return enableDrag;
    }

    /**
     * 重写划动不可用
     *
     * @return false-不可拖拽
     */
    @Override
    public boolean isItemViewSwipeEnabled() {
        return enableSwipe;
    }

    /**
     * 拖拽和侧滑抽象接口
     */
    public interface IMoveAndSwipeCallback {
        void onMove(int fromPosition, int toPosition);
        void onSwiped(int position);
    }
}