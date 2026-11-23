package com.example.widgetapp.widget

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * 把 [RecyclerItemWidget] 接进 RecyclerView。
 *
 * 子类只实现 [onCreateWidget] 和 [getItemCount]。
 * bind / recycle / attach / detach 会转到 Widget，不要再重写 Adapter 的这几个回调。
 */
abstract class WidgetAdapter<W : RecyclerItemWidget> :
    RecyclerView.Adapter<WidgetAdapter.WidgetHolder<W>>() {

    /**
     * 一个 item 对应一个 Widget。ViewHolder 的 itemView 就是 [widget] 的 root。
     * Widget 会随 ViewHolder 一起被复用，不是每次 bind 都新建。
     */
    class WidgetHolder<W : RecyclerItemWidget>(val widget: W) : RecyclerView.ViewHolder(widget.root)

    /**
     * 创建 ViewHolder。实际创建交给 [onCreateWidget]，这里只包成 [WidgetHolder]。
     * 已是 final，子类不要重写。
     */
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetHolder<W> {
        return WidgetHolder(onCreateWidget(parent, viewType))
    }

    /**
     * 全量绑定。RecyclerView 在没有 payload 时走这里。
     * 转成带空 payload 列表的重载，保证 Widget 只从一条路径收到 [RecyclerItemWidget.onBind]。
     */
    final override fun onBindViewHolder(holder: WidgetHolder<W>, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    /**
     * 带 payload 的绑定，包括上面转过来的全量绑定。
     * 直接交给 Widget，不再调用 super，避免一次绑定触发两次 [RecyclerItemWidget.onBind]。
     */
    final override fun onBindViewHolder(
        holder: WidgetHolder<W>,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        holder.widget.dispatchBind(position, payloads)
    }

    /**
     * ViewHolder 被回收进缓存池时调用，转给 [RecyclerItemWidget.onRecycle]。
     * 子类要清理状态请写在 Widget 里，不要重写这个方法。
     */
    final override fun onViewRecycled(holder: WidgetHolder<W>) {
        holder.widget.dispatchRecycle()
    }

    /** item 进入窗口，转给 [RecyclerItemWidget.onViewAttached]。 */
    final override fun onViewAttachedToWindow(holder: WidgetHolder<W>) {
        holder.widget.dispatchViewAttached()
    }

    /** item 离开窗口，转给 [RecyclerItemWidget.onViewDetached]。 */
    final override fun onViewDetachedFromWindow(holder: WidgetHolder<W>) {
        holder.widget.dispatchViewDetached()
    }

    /**
     * 按 [viewType] 创建一个 item Widget。
     *
     * [parent] 只用来取 Context 和 inflate 时传 attachToRoot = false，不要在这里 addView。
     * 返回的 Widget，其 root 会作为这个 item 的根 View，并在后续绑定中被复用。
     */
    protected abstract fun onCreateWidget(parent: ViewGroup, viewType: Int): W
}
