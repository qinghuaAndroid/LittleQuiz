package com.example.widgetapp.widget

import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView item 用的 Widget。
 *
 * 不跟 Activity / Fragment 生命周期走。列表会复用 View，所以回调是另一套：
 * [onBind] 绑定当前位置，[onRecycle] 回收进缓存池，
 * [onViewAttached] / [onViewDetached] 对应滑进、滑出窗口。
 *
 * 同一个实例可能多次 bind 到不同 position，[onRecycle] 里要清掉一次性状态。
 * 嵌套的 [RecyclerItemWidget] 会收到同样的回调。
 */
abstract class RecyclerItemWidget : Widget() {

    /**
     * 最近一次 [onBind] 的位置。
     * 还没绑定，或已经 [onRecycle] 后，是 [RecyclerView.NO_POSITION]。
     */
    var boundPosition: Int = RecyclerView.NO_POSITION
        private set

    /** 当前是否处于一次 bind 和对应 recycle 之间。 */
    var isBound: Boolean = false
        private set

    /** 当前 item 是否在 RecyclerView 的窗口里。滑出或回收后为 false。 */
    var isViewAttached: Boolean = false
        private set

    /**
     * 由 [WidgetAdapter] 调用。先更新绑定状态并回调自己，再通知子 [RecyclerItemWidget]。
     * 父节点先收到 [onBind]，这样子节点回调时可以读到父节点已经写好的状态。
     *
     * @param payloads 局部刷新的数据。为空表示全量绑定。
     */
    internal fun dispatchBind(position: Int, payloads: List<Any>) {
        if (isBound) dispatchRecycle()
        boundPosition = position
        isBound = true
        onBind(position, payloads)
        for (child in childWidgets) {
            if (child is RecyclerItemWidget) child.dispatchBind(position, payloads)
        }
    }

    /**
     * 由 [WidgetAdapter] 在 ViewHolder 回收时调用。
     *
     * 先让子 Widget 回收。如果这时还挂在窗口上，先补一次 [onViewDetached]，
     * 避免滑出回调没走到就直接复用。最后才清 [isBound] 和 [boundPosition]，
     * 所以 [onRecycle] 里仍能读到这次绑定的位置。
     */
    internal fun dispatchRecycle() {
        if (!isBound) return
        for (child in childWidgets) {
            if (child is RecyclerItemWidget) child.dispatchRecycle()
        }
        if (isViewAttached) dispatchViewDetached()
        onRecycle()
        isBound = false
        boundPosition = RecyclerView.NO_POSITION
    }

    /**
     * 由 [WidgetAdapter] 在 item 进入窗口时调用。
     * 已经 attached 时直接返回，避免重复回调。自己先收到，再通知子 Widget。
     */
    internal fun dispatchViewAttached() {
        if (isViewAttached) return
        isViewAttached = true
        onViewAttached()
        for (child in childWidgets) {
            if (child is RecyclerItemWidget) child.dispatchViewAttached()
        }
    }

    /**
     * 由 [WidgetAdapter] 在 item 离开窗口时调用。
     * 已经 detached 时直接返回。先通知子 Widget，再回调自己。
     */
    internal fun dispatchViewDetached() {
        if (!isViewAttached) return
        for (child in childWidgets) {
            if (child is RecyclerItemWidget) child.dispatchViewDetached()
        }
        isViewAttached = false
        onViewDetached()
    }

    /**
     * 绑定到 [position]。
     *
     * [payloads] 为空是全量绑定，要刷新整个 item。
     * 不为空是 [RecyclerView.Adapter.notifyItemChanged] 带来的局部更新，按 payload 增量刷新即可。
     * 同一个实例下次可能绑到别的 position，不要把这里的数据留到 [onRecycle] 之后。
     */
    protected open fun onBind(position: Int, payloads: List<Any>) {}

    /**
     * ViewHolder 进入缓存池，准备被复用。
     *
     * 此时 [boundPosition] 还是这次的位置，方法返回后会被清掉。
     * 监听、图片请求、临时状态放这里清理。下次复用会再走 [onBind]。
     */
    protected open fun onRecycle() {}

    /**
     * item 滑进 RecyclerView 窗口。
     * 适合开始只在屏幕上才需要的工作，例如动画、曝光统计。此时一般已经 [onBind] 过。
     */
    protected open fun onViewAttached() {}

    /**
     * item 滑出窗口。
     *
     * 滑出不等于马上复用，接下来仍可能再绑到同一个 position，所以不要在这里清业务数据。
     * 要释放的是「在屏幕上才需要」的资源。真正复用前会再走 [onRecycle]。
     */
    protected open fun onViewDetached() {}
}
